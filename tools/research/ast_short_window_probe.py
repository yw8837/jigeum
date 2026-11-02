"""Fixed 2s/4s AST context ablation; local research, never a product policy.

Keep the shipped model/frontend/scores fixed. Compare independent-length windows
without declaring their classifier spans to be infant event onsets. Per-input
outputs remain ignored; this runner performs no downloads or media uploads.
"""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import socket
import sys
import time

from efficientat_probe import digest, load_corpus
from ondevice_audio_candidate import CONFIG_FILES, window_ranges
from ondevice_av_probe import validate_output_path, verify_model

MODEL_SHA = '3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
CONTRARY = ('Baby cry, infant cry', 'Baby laughter', 'Crying, sobbing', 'Laughter',
            'Screaming', 'Giggle', 'Snicker', 'Belly laugh', 'Chuckle, chortle',
            'Whimper', 'Wail, moan')


def short_ranges(size, length):
    if type(size) is not int or not 16000 <= size <= 320000 or length not in (32000, 64000):
        raise ValueError('Invalid fixed short-window contract')
    if size <= length:
        return [(0, size)]
    # Fixed disjoint windows; no score-guided cropping or shifted grid search.
    starts = list(range(0, size - length + 1, length))
    return [(s, s + length) for s in starts]


def candidate_summary(windows, base_windows):
    # The current complete-selection conflict guard is retained. Added windows
    # may only tighten it. This is screening and does not bypass YAMNet gates.
    all_windows = base_windows + windows
    blocked = any(any(w['scores'][k] >= .30 for k in CONTRARY + ('Television',))
                  for w in all_windows)
    supported = []
    for w in windows:
        scores = w['scores']
        babble, child = scores['Babbling'], scores['Child speech, kid speaking']
        contrary = max(scores[k] for k in CONTRARY)
        if ((babble >= .30 or (babble >= .10 and child >= .30))
                and not (contrary >= .10 and contrary >= babble * .60)):
            supported.append([w['start_ms'], w['end_ms']])
    return dict(whole_selection_blocked=blocked, supported_classifier_spans=supported,
                positive_not_product_acceptance=bool(supported) and not blocked)


def summarize_variants(variants, baseline):
    """All new contexts share the selection-wide contrary guard from the spec."""
    all_short = [w for variant in variants for w in variant['windows']]
    return [dict(**variant, summary=candidate_summary(variant['windows'], baseline + all_short))
            for variant in variants]


def worker(args):
    def denied(*unused, **kw):
        raise RuntimeError('Network prohibited during local AST ablation')
    socket.socket.connect = socket.socket.connect_ex = socket.socket.sendto = socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    import numpy as np
    import onnxruntime as ort
    from ondevice_ast_features import extract
    verify_model(args.model, dict(size_bytes=91134123, sha256=MODEL_SHA))
    verify_model(args.config, CONFIG_FILES['config.json'])
    if ort.__version__ != '1.24.4' or np.__version__ != '2.4.6':
        raise ValueError('AST screening runtime differs from fixed baseline')
    labels_map = json.loads(args.config.read_text('utf-8'))['id2label']
    labels = [labels_map[str(i)] for i in range(527)]
    if len(set(labels)) != 527 or not set(CONTRARY + ('Television', 'Babbling', 'Child speech, kid speaking')).issubset(labels):
        raise ValueError('Unexpected labels')
    entries = [(root, e) for root in args.corpus for e in load_corpus(root)]
    if len({e['id'] for _, e in entries}) != len(entries):
        raise ValueError('Duplicate IDs across corpora')
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.enable_mem_pattern = False
    session = ort.InferenceSession(str(args.model), sess_options=options, providers=['CPUExecutionProvider'])
    if session.get_inputs()[0].shape != [1, 1024, 128]:
        raise ValueError('Unexpected AST graph')
    result = dict(status='running', model_sha256=MODEL_SHA,
                  frontend_sha256=digest(Path(__file__).with_name('ondevice_ast_features.py')),
                  runner_sha256=digest(Path(__file__)), runtime=dict(numpy=np.__version__, ort=ort.__version__),
                  python_network_blocked=True, quality_validated=False, android_validated=False,
                  contract='baseline10s/5s-hop; fixed disjoint2s and4s; no threshold change; classifier spans only', cases=[])

    def measure(pcm, ranges):
        rows = []
        for start, end in ranges:
            began = time.monotonic()
            features = extract(pcm[start:end])[None]
            logits = session.run(['logits'], {'input_values': features})[0][0]
            if logits.shape != (527,) or not np.isfinite(logits).all():
                raise ValueError('Invalid AST output')
            # Stable sigmoid without overflow; keep full precision for the gate.
            positive = logits >= 0
            scores = np.empty_like(logits)
            scores[positive] = 1 / (1 + np.exp(-logits[positive]))
            exp_negative = np.exp(logits[~positive])
            scores[~positive] = exp_negative / (1 + exp_negative)
            rows.append(dict(start_ms=start // 16, end_ms=end // 16,
                             seconds=time.monotonic()-began,
                             scores={label: float(scores[i]) for i, label in enumerate(labels)}))
        return rows

    for root, entry in entries:
        file = root / (entry['id'] + '.f32')
        pcm = np.fromfile(file, dtype='<f4')
        before = time.monotonic()
        base = measure(pcm, window_ranges(len(pcm)))
        row = dict(id=entry['id'], pcm_sha256=entry['pcm_sha256'], source_sha256=entry.get('source_sha256'),
                   duration_ms=entry['duration_ms'], expected=entry.get('expected'), role=entry.get('role'),
                   baseline_windows=base, baseline=candidate_summary(base, []), variants=[])
        for seconds in (2, 4):
            windows = measure(pcm, short_ranges(len(pcm), seconds * 16000))
            row['variants'].append(dict(seconds=seconds, windows=windows))
        row['variants'] = summarize_variants(row['variants'], base)
        if digest(file) != entry['pcm_sha256']:
            raise ValueError('Input changed during inference')
        row['elapsed_seconds'] = time.monotonic()-before
        result['cases'].append(row)
        pcm.fill(0)
    result['status'] = 'completed'
    (args.output / 'scores-private.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    args.model = args.model.resolve(strict=True)
    args.config = args.config.resolve(strict=True)
    args.corpus = [p.resolve(strict=True) for p in args.corpus]
    args.output = args.output.resolve()
    validate_output_path(args.output)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    execution = run_bounded([sys.executable, str(Path(__file__).resolve()), '--worker',
        '--model', str(args.model), '--config', str(args.config), '--corpus', *map(str, args.corpus),
        '--output', str(args.output)], cwd=Path(__file__).resolve().parents[2],
        log_path=args.output/'inference-private.log', timeout_seconds=300,
        memory_limit_bytes=2*1024**3, env=dict(os.environ))
    (args.output/'process.json').write_text(json.dumps(asdict(execution), indent=2), encoding='utf-8')
    if execution.exit_code or execution.termination or not execution.cleanup_complete:
        raise SystemExit('Ablation failed; inspect private log/process evidence')
    result = json.loads((args.output/'scores-private.json').read_text('utf-8'))
    if result['status'] != 'completed':
        raise SystemExit('Incomplete ablation output')
    print(json.dumps(dict(status='completed', cases=len(result['cases']),
                          inference_calls=sum(len(c['baseline_windows']) + sum(len(v['windows']) for v in c['variants']) for c in result['cases']),
                          android_validated=False, quality_validated=False)))


if __name__ == '__main__':
    main()
