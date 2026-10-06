"""Hostile local CLI fixtures only, never classifies user or public media."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    from ondevice_av_probe import validate_output_path
    out = args.output.resolve()
    validate_output_path(out)
    out.mkdir(parents=True, exist_ok=False)
    cases = [dict(id='bad_hash', kind='hash', expected='PCM hash mismatch'),
             dict(id='nonfinite', kind='nan', expected='finite'),
             dict(id='traversal', kind='path', expected='ID invalid'),
             dict(id='stale', kind='stale', expected='FileExistsError'),
             dict(id='outside', kind='outside', expected='Private outputs')]
    (out/'matrix-before.json').write_text(json.dumps(cases, indent=2), encoding='utf-8')
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    rows = []
    for case in cases:
        corpus = out/(case['id']+'-corpus')
        corpus.mkdir()
        payload = struct.pack('<f', float('nan') if case['kind']=='nan' else .125)*16000
        entry = dict(id='../signal' if case['kind']=='path' else 'signal', pcm_samples=16000,
            pcm_bytes=len(payload), pcm_sha256='0'*64 if case['kind']=='hash' else hashlib.sha256(payload).hexdigest(),
            duration_ms=1000, sample_rate_hz=16000, channels=1, encoding='float32le')
        pcm = corpus/'signal.f32'
        pcm.write_bytes(payload)
        (corpus/'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
        target = out/(case['id']+'-run')
        sentinel_bytes = b'{"status":"completed","old":true}'
        if case['kind']=='stale':
            target.mkdir()
            (target/'sentinel.json').write_bytes(sentinel_bytes)
        if case['kind']=='outside':
            target = ROOT/'docs/vtc1-qa-must-not-exist'
            if target.exists():
                raise ValueError('Outside target already exists')
        command = [sys.executable, str(ROOT/'tools/research/vtc1_probe.py'), '--validate-only',
            '--assets', str(ROOT/'work/vtc1-20261006'), '--corpus', str(corpus), '--output', str(target)]
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, timeout=30)
        text = (result.stdout+result.stderr).decode('utf-8', errors='replace')
        (out/(case['id']+'.log')).write_text(text, encoding='utf-8')
        if (target/'inference-private.log').exists():
            text += (target/'inference-private.log').read_text('utf-8', errors='replace')
        process = json.loads((target/'process.json').read_text('utf-8')) if (target/'process.json').exists() else None
        clean = not process or process['cleanup_complete'] and process['remaining_job_processes']==0
        successful = (target/'scores-private.json').exists() or (target/'validation-private.json').exists()
        sentinel_intact = case['kind']!='stale' or (target/'sentinel.json').read_bytes()==sentinel_bytes
        pcm_intact = hashlib.sha256(pcm.read_bytes()).hexdigest()==hashlib.sha256(payload).hexdigest()
        passed = (result.returncode != 0 and case['expected'].lower() in text.lower()
                  and clean and not successful and sentinel_intact and pcm_intact
                  and (case['kind']!='outside' or not target.exists()))
        rows.append(dict(**case, passed=passed, exit_code=result.returncode, cleanup_complete=clean,
            success_artifact=successful, sentinel_intact=sentinel_intact, pcm_intact=pcm_intact, process=process))
    report = dict(status='pass' if all(r['passed'] for r in rows) else 'fail', cases=rows,
                  classifier_forwards=0, media_read=False, product_changed=False)
    (out/'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(dict(status=report['status'], cases=len(rows), passed=sum(r['passed'] for r in rows))))
    raise SystemExit(0 if report['status']=='pass' else 1)


if __name__ == '__main__':
    main()
