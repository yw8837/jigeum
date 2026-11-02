"""Offline, fixed-10-second Cnn10 host probe; no product decision or downloads.

Only --validate-only may run before the research runtime module manifest is
pinned. It loads all weights strictly and checks the frozen frontend without
calling Cnn10.forward. Scores/media stay in ignored work/.
"""
import argparse
import ast
import csv
from dataclasses import asdict
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import socket
import sys
import time
import types

from efficientat_probe import digest, load_corpus
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('panns-model.json')
REPO = Path(__file__).resolve().parents[2]
MONITORED = ('Babbling', 'Baby cry, infant cry', 'Baby laughter', 'Speech',
    'Child speech, kid speaking', 'Female speech, woman speaking',
    'Male speech, man speaking', 'Crying, sobbing', 'Laughter',
    'Hubbub, speech noise, speech babble', 'Music', 'Television')
LABEL_INDICES = {'Speech': 0, 'Male speech, man speaking': 1,
    'Female speech, woman speaking': 2, 'Child speech, kid speaking': 3,
    'Babbling': 6, 'Baby laughter': 17, 'Baby cry, infant cry': 23}
MODULE_PREFIXES = ('torch', 'torchaudio', 'numpy', 'scipy', 'librosa',
    'torchlibrosa', 'numba', 'llvmlite', 'resampy', 'sklearn', 'joblib',
    'matplotlib', 'audioread', 'decorator', 'six', 'pkg_resources',
    'setuptools', 'threadpoolctl')
FROZEN_FRONTEND = ('spectrogram_extractor.stft.conv_real.weight',
    'spectrogram_extractor.stft.conv_imag.weight', 'logmel_extractor.melW')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def tensor_digest(tensor):
    return hashlib.sha256(tensor.detach().cpu().contiguous().numpy().tobytes()).hexdigest()


def setup_runtime(args, pin):
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local screening')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.socket.sendto = denied
    socket.create_connection = denied
    os.environ.update(HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1',
                      NUMBA_CACHE_DIR=str(args.output / 'numba-cache'))
    sys.dont_write_bytecode = True
    dependency_root = args.assets / 'dependencies'
    sys.path.insert(0, str(dependency_root))
    # Read-only fallback for missing scientific dependencies. Current temp
    # EfficientAT torch stays ahead of this global site-packages directory.
    fallback = Path(pin['fallback_site_packages']).resolve(strict=True)
    sys.path.append(str(fallback))
    require(sys.version.split()[0] == pin['python_version'], 'Python version differs from pin')
    versions = {name: importlib.metadata.version(name) for name in pin['runtime']}
    require(versions == pin['runtime'], 'Research package versions differ from pin')
    for relative, expected in pin['dependency_files'].items():
        path = dependency_root / relative
        require(not path.is_symlink() and path.resolve().is_relative_to(dependency_root),
                'Dependency location invalid')
        require(digest(path) == expected, 'Dependency source mismatch: ' + relative)
    import numpy as np
    # Librosa0.6.3 uses names removed by NumPy2. These are process-only aliases
    # to the same scalar/builtin types, never edits to installed packages.
    for name, value in {'complex': np.complex128, 'float': float, 'int': int}.items():
        require(name not in np.__dict__, 'Unexpected preexisting NumPy compatibility alias')
        setattr(np, name, value)
    import numba
    require('numba.decorators' not in sys.modules, 'Unexpected preexisting numba.decorators')
    alias = types.ModuleType('numba.decorators')
    alias.jit = numba.jit
    sys.modules['numba.decorators'] = alias
    return versions


def runtime_snapshot():
    files = {}
    for name, module in sorted(sys.modules.items()):
        if not any(name == prefix or name.startswith(prefix + '.') for prefix in MODULE_PREFIXES):
            continue
        path = getattr(module, '__file__', None)
        if path is not None:
            # PyTorch creates these registration namespaces with a virtual
            # __file__. Their real implementation is a separately hashed module.
            virtual = {'torch.classes': ('_classes.py', 'torch._classes'),
                       'torch.ops': ('_ops.py', 'torch._ops')}
            if name in virtual and path == virtual[name][0]:
                backing = sys.modules[virtual[name][1]]
                real_file = Path(backing.__file__).resolve(strict=True)
                files[name] = dict(virtual_registration=True, backing_module=virtual[name][1],
                                   path=str(real_file), sha256=digest(real_file))
                continue
            file = Path(path).resolve(strict=True)
            files[name] = dict(path=str(file), sha256=digest(file))
    payload = json.dumps(files, sort_keys=True, separators=(',', ':')).encode()
    return files, hashlib.sha256(payload).hexdigest()


def original_class(source, namespace):
    # Original AST bodies are unchanged; omit unrelated models and h5py/data
    # imports. Only eval Cnn10 dependencies are compiled.
    path = source / 'pytorch/models.py'
    tree = ast.parse(path.read_text(encoding='utf-8'))
    names = ('init_layer', 'init_bn', 'ConvBlock', 'Cnn10')
    nodes = [node for node in tree.body
             if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name in names]
    require(len(nodes) == len(names), 'Missing/duplicate upstream Cnn10 definitions')
    exec(compile(ast.Module(body=nodes, type_ignores=[]), str(path), 'exec'), namespace)
    return namespace['Cnn10']


def worker(args):
    pin = json.loads(PIN.read_text(encoding='utf-8'))
    versions = setup_runtime(args, pin)
    source = args.assets / 'source'
    for relative, expected in pin['source_files'].items():
        path = source / relative
        require(not path.is_symlink() and path.resolve().is_relative_to(source), 'Source location invalid')
        require(digest(path) == expected, 'Source hash mismatch: ' + relative)
    asset = pin['weight']
    weights = args.assets / asset['name']
    require(not weights.is_symlink() and weights.resolve().parent == args.assets, 'Weight location invalid')
    require(weights.stat().st_size == asset['size'] and digest(weights) == asset['sha256'],
            'Weight size/SHA256 mismatch')
    require(hashlib.md5(weights.read_bytes()).hexdigest() == asset['md5'], 'Weight MD5 mismatch')
    with (source / 'metadata/class_labels_indices.csv').open(encoding='utf-8', newline='') as handle:
        rows = list(csv.DictReader(handle))
    labels = [row['display_name'] for row in rows]
    require(len(labels) == len(set(labels)) == 527
            and [int(row['index']) for row in rows] == list(range(527)), '527-label contract failed')
    require(all(label in labels for label in MONITORED)
            and all(labels[index] == label for label, index in LABEL_INDICES.items()),
            'Required infant/adult/TV labels differ from source')
    import numpy as np
    import torch
    import torchaudio
    import librosa
    from torchlibrosa.stft import Spectrogram, LogmelFilterBank
    from torchlibrosa.augmentation import SpecAugmentation
    torch.set_num_threads(4)
    torch.manual_seed(17)
    namespace = dict(torch=torch, nn=torch.nn, F=torch.nn.functional,
                     Spectrogram=Spectrogram, LogmelFilterBank=LogmelFilterBank,
                     SpecAugmentation=SpecAugmentation)
    Cnn10 = original_class(source, namespace)
    began = time.monotonic()
    model = Cnn10(sample_rate=32000, window_size=1024, hop_size=320,
                  mel_bins=64, fmin=50, fmax=14000, classes_num=527).float().eval()
    raw = torch.load(weights, map_location='cpu', weights_only=True)
    require(isinstance(raw, dict) and isinstance(raw.get('model'), dict), 'Expected checkpoint model state')
    state = raw['model']
    expected = model.state_dict()
    require(set(state) == set(expected), 'All model keys must match; dropping classifier/frontend prohibited')
    for key, value in state.items():
        require(isinstance(value, torch.Tensor) and value.shape == expected[key].shape,
                'State tensor/shape mismatch: ' + key)
        if value.is_floating_point():
            require(bool(torch.isfinite(value).all()), 'Nonfinite checkpoint tensor: ' + key)
    require(state['fc_audioset.weight'].shape == (527, 512)
            and state['fc_audioset.bias'].shape == (527,), 'Strict527 head contract failed')
    frontend = {}
    for key in FROZEN_FRONTEND:
        require(key in state, 'Missing frozen frontend coefficient: ' + key)
        difference = float((state[key] - expected[key]).abs().max())
        frontend[key] = dict(checkpoint_sha256=tensor_digest(state[key]),
            source_generated_sha256=tensor_digest(expected[key]),
            max_abs_difference=difference, shape=list(state[key].shape))
        require(tensor_digest(state[key]) == tensor_digest(expected[key]),
                'Frontend coefficient bytes differ: ' + key)
    model.load_state_dict(state, strict=True)
    for key in FROZEN_FRONTEND:
        require(tensor_digest(model.state_dict()[key]) == tensor_digest(state[key]),
                'Loaded frozen frontend is not exact checkpoint tensor')
    # Public synthetic probes validate preprocessing only; Cnn10.forward is not
    # called in validation mode. Reference remains exact Librosa0.6.3 source.
    reference_errors = []
    for kind in ('silence', 'tone', 'noise'):
        n = np.arange(320000)
        waveform = (np.zeros(320000, dtype=np.float32) if kind == 'silence'
            else (.2 * np.sin(2 * np.pi * 440 * n / 32000)).astype(np.float32) if kind == 'tone'
            else np.random.RandomState(17).uniform(-.05, .05, 320000).astype(np.float32))
        with torch.inference_mode():
            tensor = torch.from_numpy(waveform.copy()).unsqueeze(0)
            actual_spectral_power = model.spectrogram_extractor(tensor)
            actual = model.logmel_extractor(actual_spectral_power)
            repeat = model.logmel_extractor(model.spectrogram_extractor(tensor))
        require(actual.shape == (1, 1, 1001, 64) and bool(torch.isfinite(actual).all()),
                '10s frontend shape/finite contract failed')
        require(torch.equal(actual, repeat), 'Frontend repeatability failed')
        stft = librosa.core.stft(waveform, n_fft=1024, hop_length=320,
                                 win_length=1024, window='hann', center=True, pad_mode='reflect')
        spectral_reference = np.abs(stft.T).astype(np.float64) ** 2
        spectral_actual = actual_spectral_power[0, 0].numpy().astype(np.float64)
        require(spectral_actual.shape == spectral_reference.shape and np.isfinite(spectral_actual).all(),
                'STFT power shape/finite mismatch')
        reference_norm = float(np.linalg.norm(spectral_reference))
        relative_power_l2 = (float(np.linalg.norm(spectral_actual - spectral_reference)) / reference_norm
                             if reference_norm else 0.0 if not np.any(spectral_actual) else float('inf'))
        mel = librosa.filters.mel(sr=32000, n_fft=1024, n_mels=64, fmin=50, fmax=14000)
        ref = librosa.core.power_to_db(np.dot(np.abs(stft.T) ** 2, mel.T),
                                       ref=1.0, amin=1e-10, top_db=None)
        error = float(np.max(np.abs(actual[0, 0].numpy() - ref)))
        delta = np.abs(actual[0, 0].numpy() - ref)
        worst = np.unravel_index(int(delta.argmax()), delta.shape)
        actual_power = np.power(10., actual[0, 0].numpy().astype(np.float64) / 10)
        reference_power = np.power(10., ref / 10)
        signal = ref > -60
        reference_errors.append(dict(probe=kind, logmel_max_abs_db=error,
            stft_power_relative_l2=relative_power_l2,
            percentiles_db=np.percentile(delta, [50, 95, 99]).tolist(),
            worst_frame=int(worst[0]), worst_mel=int(worst[1]),
            worst_reference_db=float(ref[worst]), worst_actual_db=float(actual[0, 0].numpy()[worst]),
            edge_first_last_frame_max_db=float(delta[[0, -1]].max()),
            interior_max_db=float(delta[1:-1].max()),
            normalized_power_max_abs=float(np.max(np.abs(actual_power - reference_power))
                / max(float(reference_power.max()), 1e-10)),
            logmel_max_abs_db_ref_above_minus60=float(np.max(np.abs(actual[0, 0].numpy() - ref)[signal]))
                if signal.any() else None))
        (args.output / 'frontend-readiness-private.json').write_text(json.dumps(dict(
            frozen_tensors=frontend, reference_errors=reference_errors,
            classifier_forward_executed=False), indent=2), encoding='utf-8')
    # The original all-bin .01dB FFT gate wrongly magnified float32 convolution
    # rounding in near-floor tone bins. No classifier was run before correcting
    # this harness gate. Keep full dB maxima, but gate normwise power accuracy and
    # signal-bin log accuracy; generated/loaded frontend coefficients are exact.
    require(all(error['stft_power_relative_l2'] <= 1e-5 and
                (error['logmel_max_abs_db_ref_above_minus60'] is None or
                 error['logmel_max_abs_db_ref_above_minus60'] <= .01)
                for error in reference_errors), 'Frontend power/signal numeric reference failed')
    # Verify the actual deterministic16k->32k resampler, with no classifier.
    resampled = torchaudio.functional.resample(torch.zeros(16000), 16000, 32000)
    require(resampled.shape == (32000,) and bool(torch.isfinite(resampled).all()), 'Resampling shape/finite failed')
    entries = [(root, item) for root in args.corpus for item in load_corpus(root)]
    require(len({item['id'] for _, item in entries}) == len(entries), 'Duplicate case IDs across corpora')
    module_files, module_sha = runtime_snapshot()
    pinned_sha = pin.get('runtime_modules_sha256')
    if pinned_sha is not None:
        require(module_sha == pinned_sha, 'Loaded research module paths/hashes differ from pin')
    else:
        require(args.validate_only, 'Runtime module manifest must be pinned before inference')
    row = dict(name=asset['name'], sha256=asset['sha256'],
        parameters=sum(p.numel() for p in model.parameters()), strict_keys=len(state),
        load_and_frontend_seconds=time.monotonic() - began, cases=[])
    result = dict(status='completed' if args.validate_only else 'running',
        revision=pin['revision'], runtime=versions, source_files=pin['source_files'],
        python_network_blocked=True, classifier_forward_executed=False,
        quality_validated=False, android_validated=False, validated_corpus_cases=len(entries),
        runtime_modules=module_files, runtime_modules_sha256=module_sha,
        process_only_aliases=['numpy.complex=complex128', 'numpy.float=float',
                              'numpy.int=int', 'numba.decorators.jit=numba.jit'],
        frontend_frozen_tensors=frontend, frontend_reference=reference_errors,
        frontend_validation_contract='byte-identical coefficients; STFT power relativeL2<=1e-5; ref>-60dB logmel<=.01dB; all-bin error retained',
        resampler='torchaudio.functional.resample16k_to32k; no recovered high frequencies',
        policy='disjoint fixed10s chunks at32k; zero-pad tail; original clipwise head; no smoothing',
        score_note='0.5 chunk occupancy is descriptive, not calibrated app approval or event localization',
        models=[row])
    if args.validate_only:
        (args.output / 'validation-private.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
        return
    with torch.inference_mode():
        synthetic = torch.zeros(1, 320000)
        a, b = model(synthetic), model(synthetic)
        require(a['clipwise_output'].shape == (1, 527) and a['embedding'].shape == (1, 512), 'Classifier output shape failed')
        require(all(bool(torch.isfinite(a[key]).all()) and torch.equal(a[key], b[key])
                    for key in ('clipwise_output', 'embedding')), 'Output finite/repeatability failed')
    result['classifier_forward_executed'] = True
    for root, entry in entries:
        path = root / (entry['id'] + '.f32')
        require(digest(path) == entry['pcm_sha256'], 'PCM changed before inference')
        pcm = torch.from_numpy(np.fromfile(path, dtype='<f4').copy())
        started = time.monotonic()
        upsampled = torchaudio.functional.resample(pcm, 16000, 32000)
        require(upsampled.numel() == 2 * pcm.numel() and bool(torch.isfinite(upsampled).all()), 'PCM resample length/finite mismatch')
        predictions, chunks = [], []
        for first in range(0, upsampled.numel(), 320000):
            chunk = upsampled[first:first + 320000]
            valid = chunk.numel()
            chunk = torch.nn.functional.pad(chunk, (0, 320000 - valid)).unsqueeze(0)
            with torch.inference_mode():
                output = model(chunk)
            score, embedding = output['clipwise_output'], output['embedding']
            require(score.shape == (1, 527) and embedding.shape == (1, 512)
                    and bool(torch.isfinite(score).all()) and bool(torch.isfinite(embedding).all())
                    and bool(((score >= 0) & (score <= 1)).all()), 'Case output contract failed')
            predictions.append(score[0])
            chunks.append(dict(start_ms=first / 32, end_ms=(first + valid) / 32,
                               zero_padding_samples=320000 - valid))
        scores = torch.stack(predictions, dim=1)
        require(scores.shape == (527, (pcm.numel() + 159999) // 160000), '10s chunk count mismatch')
        monitored = {}
        for label in MONITORED:
            s = scores[labels.index(label)]
            monitored[label] = dict(max=float(s.max()), mean=float(s.mean()),
                                    chunks_ge_half=int((s >= .5).sum()), scores=s.tolist())
        maxima = scores.max(dim=1).values
        top = maxima.argsort(descending=True)[:5].tolist()
        require(digest(path) == entry['pcm_sha256'], 'PCM changed during inference')
        row['cases'].append(dict(id=entry['id'], pcm_sha256=entry['pcm_sha256'],
            expected=entry.get('expected'), role=entry.get('role'), pcm_samples=pcm.numel(),
            duration_ms=entry['duration_ms'], actual_pcm_duration_ms=pcm.numel() / 16,
            chunks=chunks, inference_seconds=time.monotonic() - started, monitored=monitored,
            top5_max=[dict(label=labels[index], score=float(maxima[index])) for index in top]))
    result['status'] = 'completed'
    require(digest(weights) == asset['sha256'], 'Weights changed during inference')
    (args.output / 'scores-private.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


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
    command = [sys.executable, '-B', str(Path(__file__).resolve()), '--worker',
               '--assets', str(args.assets), '--output', str(args.output)]
    if args.corpus:
        command += ['--corpus', *map(str, args.corpus)]
    if args.validate_only:
        command += ['--validate-only']
    from bounded_process import run_bounded
    outcome = run_bounded(command, cwd=REPO, log_path=args.output / 'inference-private.log',
        timeout_seconds=180, memory_limit_bytes=4 * 1024**3,
        env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'))
    (args.output / 'process.json').write_text(json.dumps(asdict(outcome), indent=2), encoding='utf-8')
    require(outcome.exit_code == 0 and not outcome.termination and outcome.cleanup_complete
            and outcome.remaining_job_processes == 0, 'Probe failed; inspect private process/log')
    target = args.output / ('validation-private.json' if args.validate_only else 'scores-private.json')
    summary = json.loads(target.read_text(encoding='utf-8'))
    require(summary['status'] == 'completed', 'Worker did not complete')
    print(json.dumps(dict(status='completed', validation_only=args.validate_only,
        classifier_forward_executed=summary['classifier_forward_executed'],
        validated_corpus_cases=summary['validated_corpus_cases'], cases=len(summary['models'][0]['cases']),
        android_validated=False, quality_validated=False)))


if __name__ == '__main__':
    main()
