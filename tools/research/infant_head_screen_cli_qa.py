"""Actual CLI rejection probes, no new media/model fixtures or classifier calls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from ondevice_av_probe import validate_output_path

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('sources','head','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    variant=parser.add_mutually_exclusive_group()
    variant.add_argument('--presence',action='store_true')
    variant.add_argument('--mil',action='store_true')
    args=parser.parse_args()
    out=args.output.resolve()
    validate_output_path(out)
    out.mkdir(parents=True,exist_ok=False)
    sources=args.sources.resolve(strict=True)
    head=args.head.resolve(strict=True)
    sha=hashlib.sha256(sources.read_bytes()).hexdigest()
    before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in head.iterdir() if p.is_file()}
    wrong=out/'wrong-hash-must-not-exist'
    outside=ROOT/'docs/qa-head-must-not-exist'
    missing=out/'missing-args-must-not-exist'
    if any(path.exists() for path in (wrong,outside,missing)):
        raise ValueError('Expected nonexistent rejection targets')
    binary=args.presence or args.mil
    script='infant_mil_probe.py' if args.mil else ('infant_presence_probe.py' if binary else 'infant_head_screen.py')
    common=[sys.executable,'-B',str(ROOT/'tools/research'/script)]
    cases=[dict(id='wrong-source-sha',expected='Source snapshot hash',target=wrong,
               argv=[*(['fit','--features',str(out)] if binary else ['acquire']),
                     '--sources',str(sources),'--sources-sha','0'*64,'--output',str(wrong)]),
           dict(id='stale-head-output',expected='FileExistsError',target=head,
               argv=['fit','--sources',str(sources),'--sources-sha',sha,'--features',str(out),'--output',str(head)]),
           dict(id='outside-output',expected='Private outputs',target=outside,
               argv=['evaluate','--head',str(head),'--corpus',str(out),'--output',str(outside)]),
           dict(id='missing-arguments',expected='Missing required presence mode arguments' if binary else 'Missing required mode arguments',target=missing,
               argv=['evaluate','--output',str(missing)])]
    (out/'matrix-before.json').write_text(json.dumps([dict(id=c['id'],expected=c['expected']) for c in cases],indent=2),encoding='utf-8')
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    rows=[]
    for case in cases:
        result=subprocess.run(common+case['argv'],cwd=ROOT,env=env,capture_output=True,timeout=30)
        log=(result.stdout+result.stderr).decode('utf-8','replace')
        (out/(case['id']+'.log')).write_text(log,encoding='utf-8')
        after={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in head.iterdir() if p.is_file()}
        absent=case['target']==head or not case['target'].exists()
        passed=result.returncode!=0 and case['expected'] in log and before==after and absent
        rows.append(dict(id=case['id'],exit_code=result.returncode,expected_signal_present=case['expected'] in log,
            old_head_unchanged=before==after,new_output_absent=absent,passed=passed))
    report=dict(status='pass' if all(row['passed'] for row in rows) else 'fail',cases=rows,
        runner=script,
        classifier_forwards=0,network_requests=0,new_fixture_files=0,old_head_before=before,
        preserved_existing_blocked_fixtures=True)
    (out/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(dict(status=report['status'],cases=len(rows),passed=sum(r['passed'] for r in rows))))
    raise SystemExit(0 if report['status']=='pass' else 1)


if __name__=='__main__':
    main()
