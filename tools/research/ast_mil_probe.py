"""AST768 caption-weak bag head; fixed public35, no new labels or product change."""
import argparse
from dataclasses import asdict
import os
from pathlib import Path
import platform
import sys

from ast_infant_head_probe import (
    ROOT, RUNTIME, SOURCE_SHA, MODEL_SHA, MODEL, normalized, validate_representations,
    validate_sources, score_spans, evaluate, offline, digest, read_report, write,
)
from infant_mil_probe import fit_mil, select, bag_logit_and_attention
from infant_presence_probe import sigmoid, presence_weights, supported
from ondevice_av_probe import validate_output_path

FEATURE_SHA = '359083b64f4db2ac29059be5430e773ea99e8fc423952bbb0e5ecf1940e0c330'
METADATA_SHA = '1e823faf7afdd1774efb8d98d52443ffdcd1cc6d7821b836777ff866720a1ecc'
EVIDENCE_ROOT = ROOT / 'work/ast-mil-20261007'


def ast_bag(features, frames):
    import numpy as np
    validate_representations(features, frames)
    kept = [i for i, f in enumerate(frames) if f['rms'] >= .003]
    if not kept:
        raise ValueError('Quiet-only AST bag')
    x = np.stack([normalized(features[i]) for i in kept])
    times = np.asarray([frames[i]['real_samples'] for i in kept], np.float64)
    return x, times / times.sum()


def score_bag(bag, w, b):
    import numpy as np
    x, times = bag
    value, attention = bag_logit_and_attention(x @ w + b, times)
    stored, _ = bag_logit_and_attention(
        x.astype(np.float32) @ w.astype(np.float32) + np.float32(b), times)
    probability = float(sigmoid(value))
    error = abs(probability - float(sigmoid(stored)))
    if error > 5e-5:
        raise ValueError('AST MIL FP32 parity differs')
    return dict(infant_score=probability, infant_support=supported(probability),
                float32_absolute_error=error, attention=attention.tolist())


def validate_feature_inputs(args):
    sources = validate_sources(args)
    archive = args.features / 'features-public.npz'
    report = args.features / 'features.json'
    if (archive.is_symlink() or report.is_symlink()
            or digest(archive) != FEATURE_SHA or digest(report) != METADATA_SHA):
        raise ValueError('Fixed AST MIL archive/metadata differs')
    metadata = read_report(report)
    if (metadata['features_sha256'] != FEATURE_SHA or metadata['runtime'] != RUNTIME
            or metadata['model_sha256'] != MODEL_SHA
            or metadata['source_snapshot_sha256'] != SOURCE_SHA
            or metadata['frontend_sha256'] != digest(ROOT / 'tools/research/ondevice_ast_features.py')):
        raise ValueError('AST MIL feature provenance differs')
    cases = metadata['cases']
    if len(cases) != len(sources):
        raise ValueError('AST MIL case count differs')
    for case, row in zip(cases, sources):
        if (case['id'], case['role'], case['weak_class'], case['sourcegroup']) != (
                row['id'], row['role'], row['provisionalclass'], row['sourcegroup']):
            raise ValueError('AST MIL source role differs')
    train = [c for c in cases if c['role'] == 'train']
    if len(train) != 21 or len({c['sourcegroup'] for c in train}) != 18:
        raise ValueError('AST MIL training partition differs')
    return archive, cases, train


def fit(args):
    import numpy as np
    archive, cases, train = validate_feature_inputs(args)
    with np.load(archive, allow_pickle=False) as data:
        bags = [ast_bag(data[c['id']], c['frames']) for c in train]
        chosen, cv = select(bags, train, 768)
        labels = np.asarray([float(c['weak_class'] == 'infant_vocal') for c in train])
        w, b = fit_mil(bags, labels, presence_weights(train), chosen, 768)
        path = args.output / 'head-ast-presence-public.npz'
        np.savez_compressed(path, weights=w.astype(np.float32), bias=np.asarray(b, np.float32))
        write(args.output / 'selection.json', dict(selected_penalty=chosen, source_cv=cv,
            train_ids=[c['id'] for c in train], validation_features_read=False,
            private_features_read=False, head_sha256=digest(path), features_sha256=FEATURE_SHA,
            source_snapshot_sha256=SOURCE_SHA))
        # Validation arrays are accessed only after weights and selection are frozen.
        rows = [dict(id=c['id'], role=c['role'], weak_class=c['weak_class'],
            sourcegroup=c['sourcegroup'], **score_bag(ast_bag(data[c['id']], c['frames']), w, b),
            spans=score_spans(data[c['id']], c['frames'], w, b)) for c in cases]
    validate_feature_inputs(args)
    write(args.output / 'fit.json', dict(cases=rows, train_cases=21, validation_cases=14,
        parameters=769, fp32_parameter_bytes=3076, head_sha256=digest(path),
        model_sha256=MODEL_SHA, source_snapshot_sha256=SOURCE_SHA, features_sha256=FEATURE_SHA,
        configuration=dict(dimension=768, steps=300, lr=.03, l2=chosen, threshold=.8, margin=.4,
            pooling='actual_sample_weighted_log_mean_exp', temperature=1.,
            normalization='span_L2_only', selection='train_source_macro_nll'),
        weak_labels_only=True, private_training=False, calibrated_probability=False,
        quality_validated=False, android_validated=False, product_changed=False))


def provenance():
    files = ('ast_mil_probe.py', 'infant_mil_probe.py', 'ast_infant_head_probe.py',
             'ast_embedding_interface.py', 'ondevice_ast_features.py', 'infant_presence_probe.py',
             'infant_head_source_cv.py', 'infant_head_screen.py')
    return {f: digest(ROOT / 'tools/research' / f) for f in files}


def worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual = dict(python=platform.python_version(), numpy=np.__version__, onnx=onnx.__version__, ort=ort.__version__)
    if actual != RUNTIME:
        raise ValueError('Fixed AST MIL runtime differs')
    {'fit': fit, 'evaluate': evaluate}[args.mode](args)
    if digest(MODEL) != MODEL_SHA:
        raise ValueError('Original AST changed')
    write(args.output / 'completion.json', dict(status='completed', mode=args.mode,
        runtime=actual, source_code_sha256=provenance(), product_changed=False))


def validate_output(path):
    validate_output_path(path)
    if not path.is_relative_to(EVIDENCE_ROOT.resolve()) or path == EVIDENCE_ROOT.resolve():
        raise ValueError('AST MIL output must stay inside dedicated evidence root')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('fit', 'evaluate'))
    parser.add_argument('--output', type=Path, required=True)
    for name in ('sources', 'features', 'head', 'corpus'):
        parser.add_argument('--' + name, type=Path)
    parser.add_argument('--sources-sha')
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    for name in ('output', 'sources', 'features', 'head', 'corpus'):
        value = getattr(args, name)
        if value is not None:
            setattr(args, name, value.resolve())
    validate_output(args.output)
    required = ('sources', 'sources_sha', 'features') if args.mode == 'fit' else ('head', 'corpus')
    if any(getattr(args, name) is None for name in required):
        raise SystemExit('Missing AST MIL mode arguments')
    if args.mode == 'fit':
        validate_feature_inputs(args)
    if args.worker:
        worker(args)
        return
    pins = provenance()
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    argv = [sys.executable, '-B', str(Path(__file__).resolve()), args.mode,
            '--worker', '--output', str(args.output)]
    for name in ('sources', 'sources_sha', 'features', 'head', 'corpus'):
        value = getattr(args, name)
        if value is not None:
            argv += ['--' + name.replace('_', '-'), str(value)]
    result = run_bounded(argv, cwd=ROOT, env=env, log_path=args.output / 'run.log',
        timeout_seconds=180, memory_limit_bytes=(2 if args.mode == 'fit' else 3) * 1024 ** 3)
    write(args.output / 'process.json', asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('AST MIL failed; inspect bounded evidence')
    done = read_report(args.output / 'completion.json')
    if (done['status'] != 'completed' or done['mode'] != args.mode or done['runtime'] != RUNTIME
            or done['source_code_sha256'] != pins or provenance() != pins):
        raise SystemExit('AST MIL completion provenance differs')
    print('AST MIL completed; product_changed=false')


if __name__ == '__main__':
    main()
