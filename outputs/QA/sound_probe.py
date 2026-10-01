"""Measure licensed local PCM through the actual Android sound pipeline.

This is an experiment, not an accuracy pass. Source-label disagreements stay in
the report even when instrumentation completes. --strict enforces exact labels.
No recording is sent to a server or included in the application.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import time
from qa_config import APK, TEST_APK, PACKAGE, adb_command, evidence_dir
from instrument import installed_provenance, parse_result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--corpus', type=Path, required=True, help='manifest.json and mono 16kHz float32 files')
    parser.add_argument('--install-app', action='store_true')
    parser.add_argument('--strict', action='store_true', help='Fail on every declared label mismatch; not a clinical/accuracy certification')
    args=parser.parse_args()
    adb=adb_command(); out=evidence_dir(); corpus=args.corpus.resolve()
    entries=json.loads((corpus/'manifest.json').read_text(encoding='utf-8'))
    if not 1 <= len(entries) <= 100: raise ValueError('Expected 1..100 local fixtures')
    names=[e['id'] for e in entries]
    if len(set(names))!=len(names) or any(not re.fullmatch('[a-z0-9_-]+',x) for x in names):
        raise ValueError('Unique safe fixture IDs required')
    for entry in entries:
        pcm=(corpus/(entry['id']+'.f32')).read_bytes()
        if not 64000 <= len(pcm) <= 960000 or len(pcm)%4: raise ValueError('Expected 1..15 second PCM')
        if hashlib.sha256(pcm).hexdigest()!=entry['pcm_sha256']: raise ValueError('Fixture hash mismatch: '+entry['id'])
    def call(*cmd, timeout=120):
        r=subprocess.run([*adb,*cmd],capture_output=True,timeout=timeout)
        r.check_returncode(); return r.stdout
    if args.install_app:
        for apk in [APK,TEST_APK]: call('install','-r',str(apk))
    provenance={}
    for role,package,apk in [('app',PACKAGE,APK),('test',PACKAGE+'.test',TEST_APK)]:
        record=installed_provenance(adb,package,hashlib.sha256(apk.read_bytes()).hexdigest(),role=='app')
        if not record['verified']: raise RuntimeError(record)
        provenance[role]=record
    destination='files/qa-public-audio'
    call('shell','run-as',PACKAGE,'mkdir','-p',destination)
    for name in ['manifest.json']+[x+'.f32' for x in names]:
        r=subprocess.run([*adb,'exec-in','run-as',PACKAGE,'sh','-c',f'cat > {destination}/{name}'],
                         input=(corpus/name).read_bytes(),capture_output=True,timeout=30)
        r.check_returncode()
    started=time.monotonic()
    r=subprocess.run([*adb,'shell','am','instrument','-w','-r','-e','class',PACKAGE+'.PublicAudioProbeTest',
        '-e','public_audio_probe','true','-e','enforce_sound_expectations',str(args.strict).lower(),
        PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=300)
    raw=(r.stdout+r.stderr).decode('utf-8','replace')
    (out/'probe-instrumentation.txt').write_text(raw,encoding='utf-8')
    execution=parse_result(raw,r.returncode,time.monotonic()-started)
    data=call('shell','run-as',PACKAGE,'cat',destination+'/probe-results.json')
    (out/'sound-results.json').write_bytes(data)
    results=json.loads(data)
    if [x.get('id') for x in results]!=names or any('observed' not in x for x in results):
        raise RuntimeError('Missing or out-of-order sound measurements')
    disagreements=[{'id':x['id'],'expected':x['expected'],'observed':x.get('observed')} for x in results
                   if x.get('observed') not in x['allowed']]
    report=dict(execution=execution,provenance=provenance,cases=len(results),
        outputs=sum(x.get('observed')!='abstain' for x in results),source_label_disagreements=disagreements,
        independent_accuracy_validated=False,strict_labels_requested=args.strict)
    (out/'sound-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
    return 0 if execution['status']=='pass' else 1


if __name__=='__main__':
    raise SystemExit(main())
