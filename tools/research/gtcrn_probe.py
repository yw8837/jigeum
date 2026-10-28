"""Local GTCRN/AST paired experiment; never uploads or changes product decisions.

Pinned raw GTCRN graph uses 16kHz, FFT512/hop256 and sqrt periodic Hann.
Unlike the upstream demo, analysis and synthesis use the SAME periodic window.
Original and processed PCM retain the exact origin and sample count. Output is
private research data, not an independently labelled accuracy benchmark.
"""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import sys
import time
import numpy as np

from efficientat_probe import digest, load_corpus
from ondevice_audio_candidate import window_ranges, CONFIG_FILES
from ondevice_ast_features import extract
from ondevice_av_probe import validate_output_path

REVISION = '502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73'
MODEL_SHA = 'f648b02f2d7ff96ebcb0eec2219688a08ed12fe7e3d50f248605a90eba8cad17'
AST_SHA = '3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
CACHES = {'conv_cache': (2, 1, 16, 16, 33), 'tra_cache': (2, 3, 1, 1, 16),
          'inter_cache': (2, 1, 33, 16)}
MIX_SHAPE = (1, 257, 1, 2)
WINDOW = np.sqrt(.5 - .5 * np.cos(2 * np.pi * np.arange(512) / 512)).astype(np.float32)


def spectrogram(samples):
    padded = np.pad(samples, (256, 256), mode='reflect')
    frames = np.lib.stride_tricks.sliding_window_view(padded, 512)[::256]
    return np.fft.rfft(frames * WINDOW, axis=-1).astype(np.complex64)


def enhance(samples, session):
    samples = np.asarray(samples)
    if (samples.ndim != 1 or not 16000 <= samples.size <= 320000
            or not np.issubdtype(samples.dtype, np.floating)
            or not np.isfinite(samples).all() or np.abs(samples).max() > 1):
        raise ValueError('Expected finite 1..20s mono 16kHz PCM in [-1,1]')
    spectrum = spectrogram(samples.astype(np.float32))
    length = (len(spectrum) - 1) * 256 + 512
    accumulated = np.zeros(length, dtype=np.float64)
    denominator = np.zeros(length, dtype=np.float64)
    caches = {key: np.zeros(shape, dtype=np.float32) for key, shape in CACHES.items()}
    names = ['enh'] + [key + '_out' for key in CACHES]
    shapes = [MIX_SHAPE] + list(CACHES.values())
    for index, frame in enumerate(spectrum):
        mix = np.stack((frame.real, frame.imag), axis=-1)[None, :, None, :].copy()
        results = session.run(names, {'mix': mix, **caches})
        if len(results) != len(shapes):
            raise ValueError('Unexpected GTCRN output count')
        for value, shape in zip(results, shapes):
            if value.dtype != np.float32 or value.shape != shape or not np.isfinite(value).all():
                raise ValueError('GTCRN requires finite float32 output of pinned shape')
        enhanced = results[0][0, :, 0, :]
        restored = np.fft.irfft(enhanced[:, 0] + 1j * enhanced[:, 1], n=512) * WINDOW
        first = index * 256
        accumulated[first:first + 512] += restored
        denominator[first:first + 512] += WINDOW.astype(np.float64) ** 2
        caches = dict(zip(CACHES, results[1:]))
    selected = slice(256, 256 + samples.size)
    # The final partial hop has only one window contribution, as in torch.istft.
    # It is still reconstructable; require nonzero overlap, not interior COLA=1.
    if len(accumulated[selected]) != samples.size or np.any(denominator[selected] <= 1e-8):
        raise ValueError('Selected interval lost samples or overlap coverage')
    output = (accumulated[selected] / denominator[selected]).astype(np.float32)
    if not np.isfinite(output).all():
        raise ValueError('Nonfinite waveform')
    # Never normalize or silently clamp a model overshoot.
    return output


def open_sessions(args):
    import onnxruntime as ort
    if ort.__version__ != '1.24.3':
        raise ValueError('ORT runtime must match pinned Android comparison')
    if args.model.stat().st_size != 352084 or digest(args.model) != MODEL_SHA:
        raise ValueError('GTCRN integrity mismatch')
    if args.ast_model.stat().st_size != 91134123 or digest(args.ast_model) != AST_SHA:
        raise ValueError('AST integrity mismatch')
    if digest(args.labels) != CONFIG_FILES['config.json']['sha256']:
        raise ValueError('AST label integrity mismatch')
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    denoiser = ort.InferenceSession(str(args.model), sess_options=options, providers=['CPUExecutionProvider'])
    expected_inputs = {'mix': MIX_SHAPE, **CACHES}
    expected_outputs = {'enh': MIX_SHAPE, **{k + '_out': v for k, v in CACHES.items()}}
    for metadata, expected in ((denoiser.get_inputs(), expected_inputs), (denoiser.get_outputs(), expected_outputs)):
        if {v.name: tuple(v.shape) for v in metadata} != expected or any(v.type != 'tensor(float)' for v in metadata):
            raise ValueError('GTCRN graph tensor contract mismatch')
    ast = ort.InferenceSession(str(args.ast_model), sess_options=options, providers=['CPUExecutionProvider'])
    return denoiser, ast


def ast_scores(samples, runtime, labels):
    windows = []
    for first, last in window_ranges(samples.size):
        logits = runtime.run(['logits'], {'input_values': extract(samples[first:last])[None, ...]})[0]
        if logits.shape != (1, 527) or not np.isfinite(logits).all():
            raise ValueError('Invalid AST logits')
        scores = 1 / (1 + np.exp(-np.clip(logits[0], -80, 80)))
        windows.append(dict(start_sample=first, end_sample=last,
                            scores={labels[str(i)]: float(v) for i, v in enumerate(scores)}))
    return windows


def worker(args):
    import socket
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local inference')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    entries = load_corpus(args.corpus)
    denoiser, ast = open_sessions(args)
    labels = json.loads(args.labels.read_text(encoding='utf-8'))['id2label']
    cleaned = args.output / 'processed-corpus'
    cleaned.mkdir()
    processed_entries = []
    report = dict(status='running', revision=REVISION, model_sha256=MODEL_SHA, ast_sha256=AST_SHA,
                  source_manifest_sha256=digest(args.corpus / 'manifest.json'), ort_version='1.24.3',
                  stft='FFT512/hop256/sqrt-periodic-Hann/center-reflect; same synthesis window',
                  cache_policy='reset per clip; carry every frame', python_network_blocked=True,
                  quality_validated=False, android_validated=False, cases=[])
    for entry in entries:
        source = args.corpus / (entry['id'] + '.f32')
        original = np.fromfile(source, dtype='<f4')
        started = time.monotonic()
        processed = enhance(original, denoiser)
        denoise_seconds = time.monotonic() - started
        if np.abs(processed).max() > 1:
            raise ValueError('Model overshoot exceeds PCM range; do not silently clip')
        file = cleaned / source.name
        file.write_bytes(processed.astype('<f4').tobytes())
        updated = {**entry, 'pcm_sha256': digest(file), 'source_pcm_sha256': entry['pcm_sha256']}
        processed_entries.append(updated)
        begin = time.monotonic()
        before = ast_scores(original, ast, labels)
        after = ast_scores(processed, ast, labels)
        if digest(source) != entry['pcm_sha256']:
            raise ValueError('Source modified during inference')
        report['cases'].append(dict(id=entry['id'], duration_ms=entry['duration_ms'],
            source_pcm_sha256=entry['pcm_sha256'], processed_pcm_sha256=digest(file),
            role=entry.get('role', 'unspecified'), expected=entry.get('expected', 'unknown'),
            label_strength=entry.get('label_strength', 'unspecified'),
            denoise_seconds=denoise_seconds, ast_pair_seconds=time.monotonic() - begin,
            rms_original=float(np.sqrt(np.mean(original.astype(np.float64)**2))),
            rms_processed=float(np.sqrt(np.mean(processed.astype(np.float64)**2))),
            peak_processed=float(np.abs(processed).max()), original=before, processed=after))
        (args.output / 'paired-private.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (cleaned / 'manifest.json').write_text(json.dumps(processed_entries, indent=2), encoding='utf-8')
    load_corpus(cleaned)
    report['status'] = 'completed'
    (args.output / 'paired-private.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'ast-model', 'labels', 'corpus', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    for name in ('model', 'ast_model', 'labels', 'corpus', 'output'):
        setattr(args, name, getattr(args, name).resolve())
    validate_output_path(args.output)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ)
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    command = [sys.executable, str(Path(__file__).resolve()), '--worker']
    for name in ('model', 'ast_model', 'labels', 'corpus', 'output'):
        command.extend(['--' + name.replace('_', '-'), str(getattr(args, name))])
    result = run_bounded(command, cwd=Path(__file__).resolve().parents[2], env=env,
                         log_path=args.output / 'inference-private.log',
                         timeout_seconds=600, memory_limit_bytes=4 * 1024**3)
    (args.output / 'process.json').write_text(json.dumps(asdict(result), indent=2), encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete:
        raise SystemExit(1)
    report = json.loads((args.output / 'paired-private.json').read_text(encoding='utf-8'))
    if report['status'] != 'completed' or len(report['cases']) != len(load_corpus(args.corpus)):
        raise SystemExit('Incomplete comparison')
    print(json.dumps(dict(status='completed', cases=len(report['cases']),
                         quality_validated=False, android_validated=False)))


if __name__ == '__main__':
    main()
