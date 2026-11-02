"""Measure fixed baseline/candidate pairs locally; success is not an accuracy pass.

Uses the offline standalone research APK and attributed local float32 PCM only.
Requires exclusive ADB ownership. Does not mutate the installed product package.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import time
import uuid

from ast_device_benchmark import (ROOT, PACKAGE, TEST_PACKAGE, PRODUCTION, MODEL_SHA,
    command, installed, production_snapshot, stop_and_remove, validate_manifest,
    local_provenance, installed_provenance, parse_result)
from sound_probe import validate_pcm


def validate_paired_results(report, entries, run_id):
    if (report.get('run_id') != run_id or report.get('status') != 'completed' or
            report.get('completed') is not True or report.get('case_count') != len(entries) or
            report.get('measured_count') != len(entries) or report.get('model_sha256') != MODEL_SHA or
            report.get('quality_validated') is not False or report.get('production_babble_policy_enabled') is not False):
        raise ValueError('Incomplete, stale or unexpected paired measurement')
    cases = report.get('cases')
    if not isinstance(cases, list) or [c.get('id') for c in cases] != [e['id'] for e in entries]:
        raise ValueError('Missing or out-of-order paired cases')
    for case, entry in zip(cases, entries):
        expected = dict(run_id=run_id, status='measured', received_sha256=entry['pcm_sha256'],
            received_bytes=entry['pcm_bytes'], samples=entry['pcm_samples'], duration_ms=entry['duration_ms'])
        if any(case.get(k) != v for k,v in expected.items()):
            raise ValueError('Failed case or received input differs from frozen PCM')
        for stage in ('base', 'candidate'):
            result = case.get(stage)
            if (not isinstance(result, dict) or not isinstance(result.get('events'), list) or
                    type(result.get('has_interpretation')) is not bool or
                    result.get('issue') == 'MODEL_FAILURE' or result.get('decision_issue') == 'MODEL_FAILURE'):
                raise ValueError('Missing decision or model failure concealed as abstention')
        ast = case.get('ast', {})
        size = entry['pcm_samples']
        starts = [0] if size <= 160000 else list(range(0, size-160000+1, 80000))
        if size > 160000 and starts[-1] != size-160000: starts.append(size-160000)
        windows = [(s*1000//16000, min(s+160000,size)*1000//16000) for s in starts]
        received = ast.get('windows', [])
        if (ast.get('model_sha256') != MODEL_SHA or ast.get('problem') is not None or
                ast.get('returned_windows') != len(windows) or
                [(w.get('start_ms'), w.get('end_ms')) for w in received] != windows):
            raise ValueError('AST failed or did not cover the full selection')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('adb','apkanalyzer','apk','test-apk','corpus','output'):
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args(argv)
    out = args.output.resolve()
    if not out.is_relative_to(ROOT/'work') or out == ROOT/'work':
        raise ValueError('Individual measurements must stay inside ignored work/')
    out.mkdir(parents=True, exist_ok=False)
    adb = [str(args.adb.resolve()), '-s', args.serial]
    run_id = uuid.uuid4().hex
    report = dict(run_id=run_id, status='fail', stage='corpus', quality_validated=False)
    touched = False
    before = None
    try:
        manifest = args.corpus/'manifest.json'
        if not 1 <= manifest.stat().st_size <= 131072: raise ValueError('Corpus manifest size invalid')
        entries = json.loads(manifest.read_text('utf-8'))
        if not isinstance(entries,list) or not 1 <= len(entries) <= 100: raise ValueError('Expected 1..100 fixtures')
        ids = [e['id'] for e in entries]
        if len(set(ids)) != len(ids) or any(not re.fullmatch('[a-z0-9_-]{1,80}',i) for i in ids):
            raise ValueError('Unique safe fixture IDs required')
        payloads = {}
        for entry in entries:
            file = args.corpus/(entry['id']+'.f32')
            if not 64000 <= file.stat().st_size <= 1280000: raise ValueError('PCM size outside contract')
            data = file.read_bytes()
            meta = validate_pcm(data,entry['pcm_sha256'],entry['id'])
            if any(entry.get(k) != v for k,v in meta.items()): raise ValueError('Frozen PCM metadata mismatch')
            entry['run_id'] = run_id
            payloads[file.name] = data
        payloads['manifest.json'] = json.dumps(entries,ensure_ascii=False).encode('utf-8')
        if len(payloads['manifest.json']) > 131072: raise ValueError('Runtime manifest exceeds Android limit')
        (out/'probe-manifest.json').write_bytes(payloads['manifest.json'])
        report['stage'] = 'apk_preflight'
        app, test = out/'probe-app.apk', out/'probe-test.apk'
        shutil.copyfile(args.apk,app); shutil.copyfile(args.test_apk,test)
        for key,apk,is_test in [('app',app,False),('test',test,True)]:
            xml = command([str(args.apkanalyzer.resolve()),'manifest','print',str(apk)],timeout=60).decode('utf-8')
            report[key+'_manifest'] = validate_manifest(xml,test=is_test)
        report['provenance'] = local_provenance(app,test,ROOT)
        report['stage'] = 'device_preflight'
        if command(adb+['get-state']).strip()!=b'device' or command(adb+['shell','am','get-current-user']).strip()!=b'0':
            raise RuntimeError('Explicit device/primary user required')
        if installed(adb,PACKAGE) or installed(adb,TEST_PACKAGE): raise RuntimeError('Existing research install must be reviewed')
        before = production_snapshot(adb); report['production_before'] = before
        report['stage'] = 'installation'; touched = True
        for key,apk,package in [('app',app,PACKAGE),('test',test,TEST_PACKAGE)]:
            (out/('install-'+key+'.log')).write_bytes(command(adb+['install','--user','0',str(apk)],timeout=120))
            proof = installed_provenance(adb,package,hashlib.sha256(apk.read_bytes()).hexdigest(),key=='app')
            report[key+'_installed'] = proof
            if not proof['verified']: raise RuntimeError('Installed APK identity mismatch')
        report['stage'] = 'transfer'; destination = 'files/qa-public-audio/'+run_id
        command(adb+['shell','run-as',PACKAGE,'mkdir','-p',destination])
        for name,data in payloads.items():
            result = subprocess.run(adb+['exec-in','run-as',PACKAGE,'sh','-c',f'cat > {destination}/{name}'],
                                    input=data,capture_output=True,timeout=30)
            result.check_returncode()
        report['stage'] = 'instrumentation'; started = time.monotonic()
        print(json.dumps(dict(stage='instrumentation',run_id=run_id,cases=len(entries))),flush=True)
        execution = subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',PRODUCTION+'.AstPublicAudioProbeTest',
            '-e','ast_public_probe','true','-e','sound_probe_run_id',run_id,
            TEST_PACKAGE+'/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=600)
        raw = (execution.stdout+execution.stderr).decode('utf-8','replace')
        (out/'instrumentation.txt').write_text(raw,encoding='utf-8')
        verdict = parse_result(raw,execution.returncode,time.monotonic()-started);report['execution'] = verdict
        # Preserve partial evidence for diagnosis, but never validate it as a pass.
        data = command(adb+['exec-out','run-as',PACKAGE,'cat',destination+'/paired-results.json'])
        (out/'paired-results.json').write_bytes(data)
        if verdict['status']!='pass' or verdict['passed_tests']!=2: raise RuntimeError('Paired instrumentation failed')
        value = json.loads(data); validate_paired_results(value,entries,run_id)
        if value.get('manifest_sha256') != hashlib.sha256(payloads['manifest.json']).hexdigest():
            raise ValueError('Received manifest differs from the transferred manifest')
        report.update(status='pass',stage='complete',measured_cases=len(entries))
    except Exception as error:
        report.update(status='fail',error_type=type(error).__name__,error=str(error))
        if isinstance(error,subprocess.TimeoutExpired):
            (out/'timeout.txt').write_bytes((error.stdout or b'')+(error.stderr or b''))
    finally:
        if touched:
            try: stop_and_remove(adb);report['cleanup_complete']=True
            except Exception as error:report.update(status='fail',cleanup_complete=False,cleanup_error=str(error))
        if before is not None:
            try:
                after=production_snapshot(adb);report['production_after']=after
                report['production_install_unchanged']=before==after
                if before!=after:report['status']='fail'
            except Exception as error:report.update(status='fail',production_check_error=str(error))
        (out/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','stage','measured_cases','cleanup_complete','production_install_unchanged') if k in report}))
    return int(report['status']!='pass')


if __name__ == '__main__':raise SystemExit(main())
