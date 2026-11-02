"""Synthetic metadata-only hostile replay checks; no PCM/model classification."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

from ondevice_av_probe import validate_output_path
from vtc1_probe import PIN
from vtc1_events import SCORES_LIMIT_BYTES

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    validate_output_path(out)
    out.mkdir(parents=True,exist_ok=False)
    pin = json.loads(PIN.read_text('utf-8'))
    cases = [('samples','Manifest PCM contract'),('oversize','JSON size'),
             ('runtime','backend runtime'),('duplicate','Duplicate JSON key'),('stale','Refuse existing')]
    (out/'matrix-before.json').write_text(json.dumps(cases),encoding='utf-8')
    rows = []
    for name, expected in cases:
        corpus = out/(name+'-corpus')
        corpus.mkdir()
        entry = dict(id='synthetic',pcm_samples=16000,pcm_bytes=64000,duration_ms=1000,
            sample_rate_hz=16000,channels=1,encoding='float32le',pcm_sha256='0'*64)
        if name=='samples':
            entry['pcm_samples']=10**12
        manifest = corpus/'manifest.json'
        manifest.write_text(json.dumps([entry]),encoding='utf-8')
        scores = out/(name+'-scores.json')
        report = dict(status='completed',model_sha256=pin['weight']['sha256'],
            runtime=dict(pin['runtime']),corpus_manifest_sha256=[hashlib.sha256(manifest.read_bytes()).hexdigest()],cases=[])
        if name=='runtime':
            report['runtime']['numpy']='wrong'
        scores.write_text(json.dumps(report),encoding='utf-8')
        if name=='oversize':
            with scores.open('wb') as file:
                file.seek(SCORES_LIMIT_BYTES)
                file.write(b' ')
        if name=='duplicate':
            scores.write_text('{"status":"completed","status":"completed"}',encoding='utf-8')
        target = out/(name+'-events.json')
        sentinel = b'{"old":true}'
        if name=='stale':
            target.write_bytes(sentinel)
        command = [sys.executable,str(ROOT/'tools/research/vtc1_events.py'),
            '--assets',str(ROOT/'work/vtc1-20261006'),'--corpus',str(corpus),'--scores',str(scores),'--output',str(target)]
        run = subprocess.run(command,cwd=ROOT,capture_output=True,timeout=20)
        text = (run.stdout+run.stderr).decode('utf-8',errors='replace')
        (out/(name+'.log')).write_text(text,encoding='utf-8')
        worker_log = Path(str(target)+'.log')
        if worker_log.exists():
            text += worker_log.read_text('utf-8',errors='replace')
        process_file = Path(str(target)+'.process.json')
        process = json.loads(process_file.read_text('utf-8')) if process_file.exists() else None
        clean = not process or process['cleanup_complete'] and process['remaining_job_processes']==0
        intact = target.read_bytes()==sentinel if name=='stale' else not target.exists()
        passed = run.returncode != 0 and expected.lower() in text.lower() and clean and intact
        rows.append(dict(id=name,passed=passed,exit_code=run.returncode,cleanup_complete=clean,
                         sentinel_or_absence=intact,process=process))
    result = dict(status='pass' if all(r['passed'] for r in rows) else 'fail',cases=rows,
                  classifier_forwards=0,media_read=False,product_changed=False)
    (out/'report.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(dict(status=result['status'],cases=len(rows),passed=sum(r['passed'] for r in rows))))
    raise SystemExit(0 if result['status']=='pass' else 1)


if __name__ == '__main__':
    main()
