"""Adversarial CLI evidence checks for local screening runners, not infant accuracy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--ast-python', type=Path, required=True)
    parser.add_argument('--sed-python', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / 'work') or out == ROOT / 'work':
        raise ValueError('Use a dedicated ignored work subdirectory')
    out.mkdir(parents=True, exist_ok=False)
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    cases = [dict(id='ast_bad_hash', runner='ast', kind='hash', expected='hash'),
             dict(id='sed_nonfinite', runner='sed', kind='nan', expected='finite'),
             dict(id='ast_traversal', runner='ast', kind='path', expected='ID'),
             dict(id='sed_stale_output', runner='sed', kind='stale', expected='FileExistsError'),
             dict(id='ast_outside_output', runner='ast', kind='outside', expected='Private outputs')]
    (out / 'matrix-before.json').write_text(json.dumps(cases, indent=2), encoding='utf-8')
    results = []
    for case in cases:
        corpus = out / (case['id'] + '-corpus')
        corpus.mkdir()
        payload = struct.pack('<f', float('nan') if case['kind'] == 'nan' else .125) * 16000
        entry = dict(id='signal', pcm_samples=16000, pcm_bytes=len(payload),
            pcm_sha256=hashlib.sha256(payload).hexdigest(), duration_ms=1000,
            sample_rate_hz=16000, channels=1, encoding='float32le')
        if case['kind'] == 'hash':
            entry['pcm_sha256'] = '0' * 64
        if case['kind'] == 'path':
            entry['id'] = '../signal'
        (corpus / 'signal.f32').write_bytes(payload)
        (corpus / 'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
        target = out / (case['id'] + '-run')
        if case['kind'] == 'stale':
            target.mkdir()
            (target / 'sentinel.json').write_text('{"status":"completed","old":true}', encoding='utf-8')
        if case['kind'] == 'outside':
            target = ROOT / 'docs' / 'qa-output-must-not-exist'
            if target.exists():
                raise ValueError('Refuse to reuse outside-output target')
        if case['runner'] == 'ast':
            argv = [str(args.ast_python.resolve(strict=True)), str(ROOT/'tools/research/ast_short_window_probe.py'),
                '--model', str(ROOT/'outputs/MoaAndroid/app/src/astProbe/assets/ast-dynamic-int8.onnx'),
                '--config', str(ROOT/'work/ondevice-av/ast-model/config.json')]
        else:
            argv = [str(args.sed_python.resolve(strict=True)), str(ROOT/'tools/research/pretrainedsed_probe.py'),
                '--assets', str(ROOT/'work/pretrainedsed-20261006'), '--validate-only']
        argv += ['--corpus', str(corpus), '--output', str(target)]
        execution = subprocess.run(argv, cwd=ROOT, env=env, capture_output=True, timeout=60)
        text = (execution.stdout + execution.stderr).decode('utf-8', 'replace')
        (out / (case['id'] + '.log')).write_text(text, encoding='utf-8')
        worker_log = target / 'inference-private.log'
        combined = text + (worker_log.read_text('utf-8', errors='replace') if worker_log.exists() else '')
        process = target / 'process.json'
        if process.exists():
            detail = json.loads(process.read_text('utf-8'))
            cleanup_ok = detail['cleanup_complete'] and detail['remaining_job_processes'] == 0
        else:
            detail = None
            cleanup_ok = True  # Parent preflight rejected before worker creation.
        stale_unchanged = (target/'sentinel.json').read_text('utf-8') == '{"status":"completed","old":true}' if case['kind'] == 'stale' else True
        accepted = (target/'scores-private.json').exists() or (target/'validation-private.json').exists()
        passed = (execution.returncode != 0 and case['expected'].lower() in combined.lower()
                  and not accepted and cleanup_ok and stale_unchanged
                  and (case['kind'] != 'outside' or not target.exists()))
        results.append(dict(**case, exit_code=execution.returncode, passed=passed,
                            misleading_success=False if not accepted else True,
                            cleanup_ok=cleanup_ok, process=detail))
    report = dict(status='pass' if all(r['passed'] for r in results) else 'fail',
                  cases=results, product_changed=False, quality_validated=False)
    (out/'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(dict(status=report['status'], cases=len(results), passed=sum(r['passed'] for r in results),
                         product_changed=False, quality_validated=False)))
    raise SystemExit(0 if report['status'] == 'pass' else 1)


if __name__ == '__main__':
    main()
