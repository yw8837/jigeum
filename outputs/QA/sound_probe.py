"""Measure licensed local PCM through the actual Android sound pipeline.

This is an experiment, not an accuracy pass. Source-label disagreements stay in
the report even when instrumentation completes. --strict enforces exact labels.
No recording is sent to a server or included in the application.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import re
import struct
import subprocess
import time
import uuid
from qa_config import APK, TEST_APK, PACKAGE, adb_command, evidence_dir
from instrument import installed_provenance, parse_result


def validate_pcm(pcm: bytes, expected_hash: str, fixture_id: str) -> dict:
    """Mono 16kHz float32 little-endian, 1..20 seconds, without clipping."""
    if len(pcm) % 4 or not 64000 <= len(pcm) <= 1280000:
        raise ValueError(f'{fixture_id}: expected aligned 1..20 second PCM (64000..1280000 bytes), got {len(pcm)}')
    if not isinstance(expected_hash, str) or not re.fullmatch('[a-fA-F0-9]{64}', expected_hash):
        raise ValueError(f'{fixture_id}: invalid PCM hash')
    if hashlib.sha256(pcm).hexdigest() != expected_hash.lower():
        raise ValueError(f'{fixture_id}: fixture hash mismatch')
    for index, (value,) in enumerate(struct.iter_unpack('<f', pcm)):
        if not math.isfinite(value) or abs(value) > 1.0010000467300415:  # Android 1.001f
            raise ValueError(f'{fixture_id}: invalid PCM sample at {index}: require finite amplitude <=1.001f')
    samples = len(pcm) // 4
    return dict(pcm_bytes=len(pcm), pcm_samples=samples, duration_ms=(samples * 1000 + 15999) // 16000,
                sample_rate_hz=16000, channels=1, encoding='float32le')


def validate_measurements(results: list, entries: list, run_id: str) -> list:
    if not isinstance(results, list) or [x.get('id') for x in results] != [e['id'] for e in entries]:
        raise ValueError('Missing or out-of-order sound measurements')
    disagreements = []
    for result, entry in zip(results, entries):
        name = entry['id']
        required = dict(run_id=run_id, status='measured', received_bytes=entry['pcm_bytes'],
                        received_sha256=entry['pcm_sha256'], samples=entry['pcm_samples'],
                        range_end_ms=entry['duration_ms'])
        if any(result.get(key) != value for key, value in required.items()) or not isinstance(result.get('observed'), str):
            raise ValueError(f'{name}: stale, failed, or incomplete received PCM measurement')
        if result['observed'] not in entry['allowed']:
            disagreements.append(dict(id=name, expected=entry['expected'], observed=result['observed']))
    return disagreements


def probe_exit_code(execution: dict, disagreements: list, strict: bool) -> int:
    return int(execution.get('status') != 'pass' or (strict and bool(disagreements)))


def cleanup_owned_processes(adb: list[str]) -> dict:
    """After timeout, stop only the verified target/test and confirm PID absence."""
    owned = (PACKAGE, PACKAGE + '.test')
    stops = {}
    for package in owned:
        try:
            result = subprocess.run([*adb,'shell','am','force-stop',package],capture_output=True,timeout=20)
            stops[package] = dict(exit=result.returncode)
        except Exception as error:
            stops[package] = dict(error=str(error))
    deadline = time.monotonic() + 3
    checks = {}
    while True:
        for package in owned:
            try:
                result = subprocess.run([*adb,'shell','pidof',package],capture_output=True,timeout=5)
                text = result.stdout.decode('utf-8','replace').strip()
                checks[package] = dict(exit=result.returncode,pids=text,
                    absent=result.returncode in (0,1) and not text and not result.stderr.strip())
            except Exception as error:
                checks[package] = dict(error=str(error),absent=False)
        if all(checks[p]['absent'] for p in owned) or time.monotonic() >= deadline:
            break
        time.sleep(.1)
    return dict(complete=all(stops[p].get('exit')==0 and checks[p]['absent'] for p in owned),
                force_stops=stops,process_checks=checks)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--corpus', type=Path, required=True, help='manifest.json and mono 16kHz float32 files')
    parser.add_argument('--install-app', action='store_true')
    parser.add_argument('--strict', action='store_true', help='Fail on every declared label mismatch; not a clinical/accuracy certification')
    args=parser.parse_args()
    run_id=uuid.uuid4().hex
    out=evidence_dir()/('sound-probe-'+run_id); out.mkdir(exist_ok=False)
    report=dict(run_id=run_id,evidence_dir=str(out),status='fail',stage='corpus',cases=0,
                independent_accuracy_validated=False,strict_labels_requested=args.strict)
    try:
        corpus=args.corpus.resolve()
        entries=json.loads((corpus/'manifest.json').read_text(encoding='utf-8'))
        if not isinstance(entries,list) or not 1 <= len(entries) <= 100: raise ValueError('Expected 1..100 local fixtures')
        names=[e['id'] for e in entries]
        if any(not isinstance(x,str) or not re.fullmatch('[a-z0-9_-]+',x) for x in names) or len(set(names))!=len(names):
            raise ValueError('Unique safe fixture IDs required')
        payloads={}
        for entry in entries:
            name=entry['id']; pcm=(corpus/(name+'.f32')).read_bytes()
            metadata=validate_pcm(pcm,entry['pcm_sha256'],name)
            if not isinstance(entry.get('expected'),str) or not isinstance(entry.get('allowed'),list) or not entry['allowed'] or any(not isinstance(x,str) for x in entry['allowed']):
                raise ValueError(name+': expected label and nonempty allowed labels required')
            entry.update(metadata,run_id=run_id,pcm_sha256=entry['pcm_sha256'].lower())
            payloads[name+'.f32']=pcm  # Transfer exactly the bytes already validated.
        payloads['manifest.json']=json.dumps(entries,ensure_ascii=False).encode('utf-8')
        (out/'probe-manifest.json').write_bytes(payloads['manifest.json'])
        report['stage']='provenance'; adb=adb_command()
        def call(*cmd, timeout=120):
            r=subprocess.run([*adb,*cmd],capture_output=True,timeout=timeout)
            r.check_returncode(); return r.stdout
        if args.install_app:
            for apk in [APK,TEST_APK]: call('install','-r',str(apk))
        provenance={}; report['provenance']=provenance
        for role,package,apk in [('app',PACKAGE,APK),('test',PACKAGE+'.test',TEST_APK)]:
            record=installed_provenance(adb,package,hashlib.sha256(apk.read_bytes()).hexdigest(),role=='app')
            if not record['verified']: raise RuntimeError(record)
            provenance[role]=record
        report['stage']='transfer'; destination='files/qa-public-audio/'+run_id
        call('shell','run-as',PACKAGE,'mkdir','-p',destination)
        for name,data in payloads.items():
            r=subprocess.run([*adb,'exec-in','run-as',PACKAGE,'sh','-c',f'cat > {destination}/{name}'],
                             input=data,capture_output=True,timeout=30)
            r.check_returncode()
        report['stage']='instrumentation'; started=time.monotonic()
        try:
            r=subprocess.run([*adb,'shell','am','instrument','-w','-r','-e','class',PACKAGE+'.PublicAudioProbeTest',
                '-e','public_audio_probe','true','-e','sound_probe_run_id',run_id,
                '-e','enforce_sound_expectations',str(args.strict).lower(),
                PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=300)
            raw=(r.stdout+r.stderr).decode('utf-8','replace'); code=r.returncode
        except subprocess.TimeoutExpired as error:
            raw=((error.stdout or b'')+(error.stderr or b'')).decode('utf-8','replace')+'\nHARNESS TIMEOUT\n'; code=None
            report['timeout_cleanup']=cleanup_owned_processes(adb)
            raw+='Timeout cleanup: '+json.dumps(report['timeout_cleanup'])+'\n'
        (out/'probe-instrumentation.txt').write_text(raw,encoding='utf-8')
        execution=parse_result(raw,code,time.monotonic()-started); report['execution']=execution
        if execution['status']!='pass': raise RuntimeError('Instrumentation failed; no result counted as fresh evidence')
        report['stage']='received_results'
        data=call('shell','run-as',PACKAGE,'cat',destination+'/probe-results.json')
        results=json.loads(data)
        disagreements=validate_measurements(results,entries,run_id)
        (out/'sound-results.json').write_bytes(data)
        exit_code=probe_exit_code(execution,disagreements,args.strict)
        report.update(status='pass' if exit_code==0 else 'fail',stage='complete',cases=len(results),
            outputs=sum(x['observed']!='abstain' for x in results),source_label_disagreements=disagreements)
    except Exception as error:
        report.update(error_type=type(error).__name__,error=str(error)); exit_code=1
    (out/'sound-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
    return exit_code


if __name__=='__main__':
    raise SystemExit(main())
