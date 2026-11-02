"""Offline screening of pinned EfficientSED checkpoints, not a product decision.

Use the reviewed upstream class unchanged, without importing unused Mamba/large
transformer dependencies. No downloads; strict tensor loading; original PCM stays
unchanged. Per-case scores and media belong only in ignored work/.
"""
import argparse
import ast
from dataclasses import asdict
import importlib.metadata
import json
import os
from pathlib import Path
import socket
import sys
import time
import warnings

from efficientat_probe import load_corpus, digest
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('efficientsed-models.json')
MONITORED = ('Babbling', 'Baby cry, infant cry', 'Baby laughter', 'Speech',
             'Child speech, kid speaking', 'Crying, sobbing', 'Laughter',
             'Female speech, woman speaking', 'Male speech, man speaking',
             'Hubbub, speech noise, speech babble', 'Music')


def worker(args):
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local screening')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    pin = json.loads(PIN.read_text(encoding='utf-8'))
    source = args.assets / 'source'
    for relative, expected in pin['source_files'].items():
        if digest(source / relative) != expected:
            raise ValueError('Source hash mismatch: ' + relative)
    entries = [(root, item) for root in args.corpus for item in load_corpus(root)]
    if len({item['id'] for _, item in entries}) != len(entries):
        raise ValueError('Duplicate IDs across corpora')
    sys.path.insert(0, str(source))
    import numpy as np
    import torch
    import torchaudio
    import torchvision
    from models.efficient_cnns.fmn.fmn_wrapper import FrameMNWrapper
    from data_util.audioset_classes import as_strong_train_classes

    versions = dict(torch=torch.__version__, torchaudio=torchaudio.__version__,
                    torchvision=torchvision.__version__,
                    einops=importlib.metadata.version('einops'),
                    rotary_embedding_torch=importlib.metadata.version('rotary-embedding-torch'))
    if versions != pin['runtime']:
        raise ValueError('Runtime differs from screening pin')
    labels = as_strong_train_classes
    assert len(labels) == len(set(labels)) == 447
    assert all(label in labels for label in MONITORED)
    torch.set_num_threads(4)
    torch.manual_seed(17)

    # Only the reviewed PredictionsWrapper class is compiled, with its original
    # constructor/forward intact. checkpoint=None avoids automatic downloads.
    # Import only selected CPU sequence dependencies, not GPU-only Mamba.
    file = source / 'models/prediction_wrapper.py'
    module = ast.parse(file.read_text(encoding='utf-8'))
    classes = [n for n in module.body if isinstance(n, ast.ClassDef)
               and n.name == 'PredictionsWrapper']
    assert len(classes) == 1
    from models.sequence_models.helper import init_weights
    from models.sequence_models.roformer.transformer_block import TransformerBlock
    namespace = dict(torch=torch, nn=torch.nn, warnings=warnings,
                     init_weights=init_weights, TransformerBlock=TransformerBlock)
    exec(compile(ast.Module(body=classes, type_ignores=[]), str(file), 'exec'), namespace)
    Wrapper = namespace['PredictionsWrapper']

    result = dict(status='running', revision=pin['revision'], runtime=versions,
                  source_files=pin['source_files'], python_network_blocked=True,
                  android_validated=False, quality_validated=False,
                  policy='upstream disjoint 10s chunks; zero-pad tail; crop scores to true duration; no smoothing',
                  score_note='raw sigmoid; 0.5 occupancy is descriptive, not calibrated approval', models=[])
    for asset in pin['weights']:
        if asset['name'] != args.model:
            continue
        weights = args.assets / asset['name']
        assert weights.stat().st_size == asset['size'] and digest(weights) == asset['sha256']
        width = {'fmn04_strong.pt': 0.4, 'fmn06_strong.pt': 0.6,
                 'fmn10+tf-256_advanced-kd-weak-strong.pt': 1.0}[asset['name']]
        sequence = 'tf' if '+tf-' in asset['name'] else None
        began = time.monotonic()
        base = FrameMNWrapper(width)
        dim = base.state_dict()['fmn.features.16.1.bias'].shape[0]
        model = Wrapper(base, checkpoint=None, seq_model_type=sequence,
                        seq_model_dim=256, embed_dim=dim).float().eval()
        raw = torch.load(weights, map_location='cpu', weights_only=True)
        # Exactly the three key-renaming operations in upstream load_checkpoint.
        state = {('model.' + k[len('net.'):] if k.startswith('net.') else k): v for k, v in raw.items()}
        state = {('model.fmn.' + k[len('model.model.'):] if k.startswith('model.model.') else k): v for k, v in state.items()}
        state = {(k[len('model.'):] if not k.startswith('model.fmn.') else k): v for k, v in state.items()}
        model.load_state_dict(state, strict=True)
        row = dict(name=asset['name'], sha256=asset['sha256'],
                   parameters=sum(p.numel() for p in model.parameters()),
                   load_seconds=time.monotonic()-began, cases=[])
        # Numerical repeatability and output contract are checked on synthetic
        # silence, not used as a quality claim or threshold tuning input.
        with torch.inference_mode():
            test = model.mel_forward(torch.zeros(1, 160000))
            a, _ = model(test)
            b, _ = model(test)
            assert a.shape == (1, 447, 250) and torch.isfinite(a).all()
            assert torch.equal(a, b)
        for root, entry in entries:
            path = root / (entry['id'] + '.f32')
            pcm = torch.from_numpy(np.fromfile(path, dtype='<f4').copy())
            started = time.monotonic()
            scores_all = []
            for first in range(0, pcm.numel(), 160000):
                chunk = pcm[first:first+160000]
                length = chunk.numel()
                chunk = torch.nn.functional.pad(chunk, (0, 160000-length)).unsqueeze(0)
                with torch.inference_mode():
                    mel = model.mel_forward(chunk)
                    logits, _ = model(mel)
                    assert logits.shape == (1, 447, 250) and torch.isfinite(logits).all()
                    scores_all.append(logits[0, :, :(length+639)//640].sigmoid())
            scores = torch.cat(scores_all, dim=1)
            assert scores.shape[1] == (pcm.numel()+639)//640
            monitored = {}
            for label in MONITORED:
                s = scores[labels.index(label)]
                monitored[label] = dict(max=float(s.max()), mean=float(s.mean()),
                    bins_ge_half=int((s >= .5).sum()), scores=s.tolist())
            maxima = scores.max(dim=1).values
            top = maxima.argsort(descending=True)[:5].tolist()
            assert digest(path) == entry['pcm_sha256']
            row['cases'].append(dict(id=entry['id'], pcm_sha256=entry['pcm_sha256'],
                expected=entry.get('expected'), role=entry.get('role'), duration_ms=entry['duration_ms'],
                inference_seconds=time.monotonic()-started, monitored=monitored,
                top5_max=[dict(label=labels[i], score=float(maxima[i])) for i in top]))
        result['models'].append(row)
        del model, base, raw, state
    result['status'] = 'completed'
    (args.output / 'scores-private.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    parser.add_argument('--model', required=True, choices=['fmn04_strong.pt', 'fmn06_strong.pt',
                        'fmn10+tf-256_advanced-kd-weak-strong.pt'])
    args = parser.parse_args()
    args.assets = args.assets.resolve(strict=True)
    args.corpus = [p.resolve(strict=True) for p in args.corpus]
    args.output = args.output.resolve()
    validate_output_path(args.output)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    result = run_bounded([sys.executable, str(Path(__file__).resolve()), '--worker',
        '--assets', str(args.assets), '--model', args.model, '--corpus', *map(str, args.corpus),
        '--output', str(args.output)],
        cwd=Path(__file__).resolve().parents[2], log_path=args.output/'inference-private.log',
        timeout_seconds=180, memory_limit_bytes=4*1024**3, env=dict(os.environ))
    (args.output/'process.json').write_text(json.dumps(asdict(result), indent=2), encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete:
        raise SystemExit('Screening failed; inspect private log/process result')
    summary = json.loads((args.output/'scores-private.json').read_text(encoding='utf-8'))
    assert summary['status'] == 'completed'
    print(json.dumps(dict(status='completed', models=len(summary['models']),
                          cases_each=len(summary['models'][0]['cases']), android_validated=False,
                          quality_validated=False)))


if __name__ == '__main__':
    main()
