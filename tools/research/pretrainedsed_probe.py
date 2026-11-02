"""Offline host screening of the pinned PretrainedSED frame_mn10 checkpoint.

No downloads or installs. --validate-only checks strict weights and synthetic
frontend without executing classifier inference. Private PCM/scores stay under
ignored work/. Strong scores are descriptive, never calibrated app approval.
"""
import argparse
from dataclasses import asdict
import importlib.metadata
import json
import os
from pathlib import Path
import socket
import sys
import time

from efficientat_probe import digest, load_corpus
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('pretrainedsed-model.json')
REPO = Path(__file__).resolve().parents[2]
MONITORED = ('Babbling', 'Baby cry, infant cry', 'Baby laughter', 'Speech',
             'Child speech, kid speaking', 'Crying, sobbing', 'Laughter',
             'Female speech, woman speaking', 'Male speech, man speaking',
             'Hubbub, speech noise, speech babble', 'Music', 'Television')
EXPECTED_INDICES = {'Babbling': 15, 'Baby cry, infant cry': 16,
                    'Baby laughter': 17, 'Child speech, kid speaking': 78,
                    'Female speech, woman speaking': 157,
                    'Male speech, man speaking': 238, 'Speech': 353,
                    'Television': 381}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def deny_network():
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local screening')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.socket.sendto = denied
    socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    os.environ['TRANSFORMERS_OFFLINE'] = '1'


def verified_assets(args, pin):
    source = args.assets / 'source'
    for relative, expected in pin['source_files'].items():
        path = source / relative
        require(not path.is_symlink() and path.resolve().is_relative_to(source.resolve()),
                'Source location invalid: ' + relative)
        require(digest(path) == expected, 'Source hash mismatch: ' + relative)
    asset = pin['weight']
    weights = args.assets / asset['name']
    require(not weights.is_symlink() and weights.resolve().parent == args.assets,
            'Weight location invalid')
    require(weights.stat().st_size == asset['size'] and digest(weights) == asset['sha256'],
            'Weight size/SHA256 mismatch')
    return source, weights


def worker(args):
    deny_network()
    pin = json.loads(PIN.read_text(encoding='utf-8'))
    source, weights = verified_assets(args, pin)
    versions = {name: importlib.metadata.version(name) for name in pin['runtime']}
    require(versions == pin['runtime'], 'Runtime differs from screening pin')
    require(sys.version.split()[0] == pin['python_version'], 'Python version differs from pin')
    sys.path.insert(0, str(source))
    import numpy as np
    import torch
    from data_util.audioset_classes import as_strong_train_classes as labels
    from models.frame_mn.Frame_MN_wrapper import FrameMNWrapper
    from models.prediction_wrapper import PredictionsWrapper

    require(len(labels) == len(set(labels)) == 447, 'Label set must be exactly 447 unique labels')
    require(all(label in labels for label in MONITORED), 'Required monitored label missing')
    require(all(labels[index] == name for name, index in EXPECTED_INDICES.items()),
            'Exact infant/adult/TV label indices differ from pin')
    torch.set_num_threads(4)
    torch.manual_seed(17)
    began = time.monotonic()
    base = FrameMNWrapper(width_mult=1.0)
    dim = base.state_dict()['frame_mn.features.16.1.bias'].shape[0]
    require(dim == pin['embedding_dim'], 'Embedding width differs from pin')
    model = PredictionsWrapper(base, checkpoint=None, embed_dim=dim,
        n_classes_strong=447, n_classes_weak=447, seq_len=250,
        seq_model_type=None, head_type='linear').float().eval()
    state = torch.load(weights, map_location='cpu', weights_only=True)
    require(isinstance(state, dict), 'Expected full tensor state dictionary')
    expected = model.state_dict()
    require(set(state) == set(expected), 'State keys must match exactly; dropping heads is prohibited')
    require(len(state) == pin['state_keys'], 'State key count differs from pin')
    for key, value in state.items():
        require(isinstance(value, torch.Tensor) and value.shape == expected[key].shape,
                'State tensor/shape mismatch: ' + key)
        if value.is_floating_point():
            require(bool(torch.isfinite(value).all()), 'Nonfinite weight: ' + key)
    for head in ('strong_head', 'weak_head'):
        require(state[head + '.weight'].shape == (447, dim)
                and state[head + '.bias'].shape == (447,), '447-way head contract failed')
    model.load_state_dict(state, strict=True)
    require(sum(p.numel() for p in model.parameters()) == pin['parameters'],
            'Full model parameter count differs from pin')

    # Only frontend execution occurs in validation mode. No classifier forward.
    with torch.inference_mode():
        silence = torch.zeros(1, 160000)
        mel_a = model.mel_forward(silence)
        mel_b = model.mel_forward(silence)
        require(mel_a.shape == (1, 1, 128, 1000) and bool(torch.isfinite(mel_a).all()),
                '10s mel shape/finite contract failed')
        require(torch.equal(mel_a, mel_b), 'Eval frontend is not repeatable')
    entries = [(root, item) for root in args.corpus for item in load_corpus(root)]
    require(len({item['id'] for _, item in entries}) == len(entries), 'Duplicate case IDs across corpora')
    row = dict(name=pin['weight']['name'], sha256=pin['weight']['sha256'],
               parameters=sum(p.numel() for p in model.parameters()),
               strict_keys=len(state), embed_dim=dim,
               load_and_frontend_seconds=time.monotonic() - began, cases=[])
    result = dict(status='completed' if args.validate_only else 'running',
        revision=pin['revision'], runtime=versions, python_version=pin['python_version'],
        source_files=pin['source_files'], python_network_blocked=True,
        android_validated=False, quality_validated=False,
        classifier_forward_executed=False, synthetic_frontend_validated=True,
        validated_corpus_cases=len(entries),
        policy='upstream disjoint 10s chunks; zero-pad tail; crop to true duration; sigmoid; no smoothing',
        time_note='nominal 40ms output grid is not measured event boundary accuracy',
        score_note='raw sigmoid; 0.5 occupancy is descriptive, not calibrated approval', models=[row])
    if args.validate_only:
        target = args.output / 'validation-private.json'
        require(not target.exists(), 'Preserve previous validation result')
        target.write_text(json.dumps(result, indent=2), encoding='utf-8')
        return

    with torch.inference_mode():
        logits_a, weak_a = model(mel_a)
        logits_b, weak_b = model(mel_a)
        require(logits_a.shape == (1, 447, 250) and weak_a.shape == (1, 447),
                'Strong/weak output shape contract failed')
        require(bool(torch.isfinite(logits_a).all()) and bool(torch.isfinite(weak_a).all()),
                'Nonfinite synthetic output')
        require(torch.equal(logits_a, logits_b) and torch.equal(weak_a, weak_b),
                'Classifier output not repeatable')
    result['classifier_forward_executed'] = True
    for root, entry in entries:
        path = root / (entry['id'] + '.f32')
        require(digest(path) == entry['pcm_sha256'], 'PCM changed before inference')
        pcm = torch.from_numpy(np.fromfile(path, dtype='<f4').copy())
        started = time.monotonic()
        all_scores = []
        for first in range(0, pcm.numel(), 160000):
            chunk = pcm[first:first + 160000]
            length = chunk.numel()
            chunk = torch.nn.functional.pad(chunk, (0, 160000 - length)).unsqueeze(0)
            with torch.inference_mode():
                mel = model.mel_forward(chunk)
                logits, weak = model(mel)
                require(logits.shape == (1, 447, 250) and weak.shape == (1, 447),
                        'Strong/weak case shape contract failed')
                require(bool(torch.isfinite(logits).all()) and bool(torch.isfinite(weak).all()),
                        'Nonfinite case output')
                all_scores.append(logits[0, :, :(length + 639) // 640].sigmoid())
        scores = torch.cat(all_scores, dim=1)
        require(scores.shape == (447, (pcm.numel() + 639) // 640), 'Temporal crop shape mismatch')
        monitored = {}
        for label in MONITORED:
            s = scores[labels.index(label)]
            monitored[label] = dict(max=float(s.max()), mean=float(s.mean()),
                bins_ge_half=int((s >= .5).sum()), scores=s.tolist())
        maxima = scores.max(dim=1).values
        top = maxima.argsort(descending=True)[:5].tolist()
        require(digest(path) == entry['pcm_sha256'], 'PCM changed during inference')
        row['cases'].append(dict(id=entry['id'], pcm_sha256=entry['pcm_sha256'],
            expected=entry.get('expected'), role=entry.get('role'), duration_ms=entry['duration_ms'],
            pcm_samples=pcm.numel(), actual_pcm_duration_ms=pcm.numel() / 16,
            bin_start_ms=[first / 16 for first in range(0, pcm.numel(), 640)],
            bin_end_ms=[min(first + 640, pcm.numel()) / 16
                        for first in range(0, pcm.numel(), 640)],
            inference_seconds=time.monotonic() - started, monitored=monitored,
            top5_max=[dict(label=labels[index], score=float(maxima[index])) for index in top]))
    result['status'] = 'completed'
    require(digest(weights) == pin['weight']['sha256'], 'Weights changed during execution')
    target = args.output / 'scores-private.json'
    require(not target.exists(), 'Preserve previous score result')
    target.write_text(json.dumps(result, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, nargs='+', default=[])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--validate-only', action='store_true')
    parser.add_argument('--worker', action='store_true', help=argparse.SUPPRESS)
    args = parser.parse_args()
    if not args.validate_only and not args.corpus:
        parser.error('--corpus required for inference')
    args.assets = args.assets.resolve(strict=True)
    args.corpus = [path.resolve(strict=True) for path in args.corpus]
    args.output = args.output.resolve()
    for path in [args.assets, *args.corpus, args.output]:
        validate_output_path(path)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    command = [sys.executable, str(Path(__file__).resolve()), '--worker',
               '--assets', str(args.assets), '--output', str(args.output)]
    if args.corpus:
        command += ['--corpus', *map(str, args.corpus)]
    if args.validate_only:
        command += ['--validate-only']
    outcome = run_bounded(command, cwd=REPO, log_path=args.output / 'inference-private.log',
        timeout_seconds=180, memory_limit_bytes=4 * 1024**3,
        env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'))
    (args.output / 'process.json').write_text(json.dumps(asdict(outcome), indent=2), encoding='utf-8')
    require(outcome.exit_code == 0 and not outcome.termination and outcome.cleanup_complete
            and outcome.remaining_job_processes == 0, 'Screening failed; inspect private log/process result')
    target = args.output / ('validation-private.json' if args.validate_only else 'scores-private.json')
    summary = json.loads(target.read_text(encoding='utf-8'))
    require(summary['status'] == 'completed', 'Worker did not complete')
    print(json.dumps(dict(status='completed', validation_only=args.validate_only,
        cases=len(summary['models'][0]['cases']),
        validated_corpus_cases=summary['validated_corpus_cases'],
        classifier_forward_executed=summary['classifier_forward_executed'],
        android_validated=False, quality_validated=False)))


if __name__ == '__main__':
    main()
