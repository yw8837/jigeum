"""Fixed EfficientAT comparison on the same local PCM; no product adoption or uploads.

Downloads/installs nothing. Source code and weights must already match the pinned manifest.
16 kHz source PCM is genuinely resampled to 32 kHz; no recovered high frequencies claimed.
"""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time

from ondevice_audio_candidate import window_ranges
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('efficientat-model.json')
MONITORED = ('Babbling', 'Baby laughter', 'Baby cry, infant cry', 'Child speech, kid speaking',
             'Speech', 'Laughter', 'Crying, sobbing', 'Music', 'Silence')


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_corpus(root):
    import numpy as np
    root = Path(root).resolve(strict=True)
    manifest = root / 'manifest.json'
    if not 1 <= manifest.stat().st_size <= 131072:
        raise ValueError('Corpus manifest size invalid')
    entries = json.loads(manifest.read_text(encoding='utf-8'))
    if not isinstance(entries, list) or not 1 <= len(entries) <= 100:
        raise ValueError('Expected 1..100 cases')
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError('Invalid case')
        identity = entry.get('id')
        if (not isinstance(identity, str) or not re.fullmatch('[a-z0-9_-]{1,80}', identity)
                or identity in seen):
            raise ValueError('Case ID invalid/duplicated')
        seen.add(identity)
        size = entry.get('pcm_samples')
        if (type(size) is not int or not 16000 <= size <= 320000
                or entry.get('pcm_bytes') != size * 4 or entry.get('duration_ms') != (size + 15) // 16
                or entry.get('sample_rate_hz') != 16000 or entry.get('channels') != 1
                or entry.get('encoding') != 'float32le'):
            raise ValueError('PCM contract mismatch')
        file = root / (identity + '.f32')
        if file.is_symlink() or file.resolve().parent != root or file.stat().st_size != size * 4:
            raise ValueError('PCM location/size invalid')
        if digest(file) != entry.get('pcm_sha256'):
            raise ValueError('PCM hash mismatch')
        pcm = np.fromfile(file, dtype='<f4')
        if not np.isfinite(pcm).all() or np.abs(pcm).max() > 1:
            raise ValueError('PCM must be finite in [-1,1]')
    return entries


def verify_source(source, model, pin):
    for name, expected in pin['source_files'].items():
        path = source / name
        if path.resolve().is_relative_to(source) is False or digest(path) != expected:
            raise ValueError('Pinned source mismatch')
    if model.stat().st_size != pin['size_bytes'] or digest(model) != pin['sha256']:
        raise ValueError('Pinned weights mismatch')


def worker(args, out):
    import socket
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local model comparison')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    source = args.source_dir.resolve(strict=True)
    pin = json.loads(PIN.read_text(encoding='utf-8'))
    verify_source(source, args.model, pin)
    entries = load_corpus(args.corpus)
    # Official helpers read their pinned label CSV relative to repository cwd.
    os.chdir(source)
    sys.path.insert(0, str(source))
    import csv
    import numpy as np
    import torch
    import torchaudio
    import torchvision
    from models.mn.model import get_model
    from models.preprocess import AugmentMelSTFT
    versions = dict(torch=torch.__version__, torchaudio=torchaudio.__version__, torchvision=torchvision.__version__)
    if versions != pin['runtime']:
        raise ValueError('Runtime differs from pinned CPU comparison')
    torch.set_num_threads(4)
    torch.manual_seed(17)
    with (source / 'metadata/class_labels_indices.csv').open(encoding='utf-8', newline='') as file:
        labels = {int(row['index']): row['display_name'] for row in csv.DictReader(file)}
    if len(labels) != 527 or labels[6] != 'Babbling' or labels[17] != 'Baby laughter' or labels[23] != 'Baby cry, infant cry':
        raise ValueError('Unexpected label map')
    label_ids = {value: key for key, value in labels.items()}
    started = time.monotonic()
    model = get_model(width_mult=0.4, pretrained_name=None, num_classes=527,
                      head_type='mlp', strides=(2,2,2,2)).float().eval()
    model.load_state_dict(torch.load(args.model, map_location='cpu', weights_only=True), strict=True)
    mel = AugmentMelSTFT(n_mels=128, sr=32000, win_length=800, hopsize=320).eval()
    summary = dict(model=pin['model'], revision=pin['revision'], model_sha256=pin['sha256'],
                   corpus_sha256=digest(args.corpus / 'manifest.json'), runtime=versions,
                   load_seconds=time.monotonic()-started, parameters=sum(p.numel() for p in model.parameters()),
                   source_sample_rate_hz=16000, inference_sample_rate_hz=32000,
                   resample='torchaudio.functional.resample default sinc_interp_hann 6/.99',
                   window_policy='10s windows,5s hop,anchored tail; short input unchanged',
                   python_network_blocked=True, quality_validated=False, android_validated=False,
                   status='running', cases=[])
    for entry in entries:
        path = args.corpus / (entry['id'] + '.f32')
        pcm = torch.from_numpy(np.fromfile(path, dtype='<f4').copy())
        begin = time.monotonic()
        # Resample the full selected interval once so shared windows use identical context.
        with torch.inference_mode():
            upsampled = torchaudio.functional.resample(pcm, 16000, 32000)
        if upsampled.numel() != pcm.numel() * 2 or not torch.isfinite(upsampled).all():
            raise ValueError('Resampling changed selected interval duration')
        windows = []
        for first, last in window_ranges(pcm.numel()):
            with torch.inference_mode():
                features = mel(upsampled[first*2:last*2].unsqueeze(0)).unsqueeze(1)
                logits, _ = model(features)
                scores = logits.float().sigmoid()[0]
            if scores.shape != (527,) or not torch.isfinite(scores).all():
                raise ValueError('Model produced invalid scores')
            order = scores.argsort(descending=True).tolist()
            windows.append(dict(start_sample=first, end_sample=last, start_ms=first/16, end_ms=last/16,
                monitored_scores={name:float(scores[label_ids[name]]) for name in MONITORED},
                top5=[dict(label=labels[i],score=float(scores[i])) for i in order[:5]],
                scores=[float(value) for value in scores]))
        if digest(path) != entry['pcm_sha256']:
            raise ValueError('Source changed during inference')
        summary['cases'].append(dict(id=entry['id'], pcm_sha256=entry['pcm_sha256'],
            duration_ms=entry['duration_ms'], label_strength=entry.get('label_strength','unspecified'),
            role=entry.get('role','unspecified'), expected=entry.get('expected','unknown'),
            inference_seconds=time.monotonic()-begin, windows=windows))
        (out / 'scores-private.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    summary['status'] = 'completed'
    (out / 'scores-private.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source-dir', 'model', 'corpus', 'output'):
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    for name in ('source_dir','model','corpus','output'):
        setattr(args, name, getattr(args,name).resolve())
    out = args.output
    validate_output_path(out)
    if args.worker:
        worker(args, out)
        return
    out.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ)
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    result = run_bounded([sys.executable,str(Path(__file__).resolve()),'--worker',
        '--source-dir',str(args.source_dir),'--model',str(args.model),'--corpus',str(args.corpus),'--output',str(out)],
        cwd=Path(__file__).resolve().parents[2], log_path=out/'inference-private.log',
        timeout_seconds=240, memory_limit_bytes=4*1024**3, env=env)
    (out/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete:
        raise SystemExit(1)
    summary = json.loads((out/'scores-private.json').read_text(encoding='utf-8'))
    if summary['status'] != 'completed' or len(summary['cases']) != len(load_corpus(args.corpus)):
        raise SystemExit('Incomplete comparison')
    print(json.dumps(dict(status='completed',cases=len(summary['cases']),
                         quality_validated=False,android_validated=False)))


if __name__ == '__main__':
    main()
