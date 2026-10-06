"""Synthetic AST measurement in an isolated, offline Android research package.

Does not install, stop, remove or read records from the production package.
The caller must exclusively own ADB use during this bounded experiment.
"""
from pathlib import Path
import argparse
from datetime import datetime, timezone
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'outputs/QA'))
from instrument import installed_provenance, local_provenance, parse_result

PACKAGE = 'com.moa.babylanguage.astprobe'
TEST_PACKAGE = PACKAGE + '.test'
PRODUCTION = 'com.moa.babylanguage'
MODEL_SHA = '3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
CANDIDATE_PACKAGE = 'com.moa.babylanguage.astcandidate'
CANDIDATE_SHA = '4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a'
CANDIDATE_APK_SHA = '77b20be46ecef6e302515d63bde12f4c378a8eeb50ea829fb455d0271a61624e'
CANDIDATE_TEST_SHA = '3dea96314dbb0fe8daafecbdfed051e8a9d7b72ed774a2c02c590027bb184159'
ANDROID = '{http://schemas.android.com/apk/res/android}'
PROHIBITED = {'android.permission.INTERNET', 'android.permission.RECORD_AUDIO',
              'android.permission.CAMERA', 'android.permission.WRITE_EXTERNAL_STORAGE',
              'com.android.vending.BILLING'}


def validate_manifest(xml: str, *, test=False, infant_presence=False):
    root = ET.fromstring(xml)
    package = CANDIDATE_PACKAGE if infant_presence else PACKAGE
    expected = package + '.test' if test else package
    if root.get('package') != expected:
        raise ValueError('Only the exact standalone research APK is allowed')
    permissions = {p.get(ANDROID + 'name') for tag in ('uses-permission', 'uses-permission-sdk-23')
                   for p in root.findall(tag)}
    prohibited = {'android.permission.INTERNET', 'com.android.vending.BILLING'} if infant_presence else PROHIBITED
    if permissions & prohibited:
        raise ValueError('Research APK contains a prohibited permission')
    if test:
        runners = root.findall('instrumentation')
        if len(runners) != 1 or runners[0].get(ANDROID + 'targetPackage') != package:
            raise ValueError('Instrumentation does not target the standalone app')
    else:
        app = root.find('application')
        if app is None or app.get(ANDROID + 'allowBackup') != 'false':
            raise ValueError('Research package must disable backup')
        main = [a for a in app.findall('activity') if a.get(ANDROID + 'name') == PRODUCTION + '.MainActivity']
        if len(main) != 1 or (not infant_presence and main[0].get(ANDROID + 'enabled') != 'false'):
            raise ValueError('Research launcher must be disabled')
    return {'package': expected, 'permissions': sorted(permissions)}


def validate_measurement(value, run_id, *, infant_presence=False):
    model_sha = CANDIDATE_SHA if infant_presence else MODEL_SHA
    if (value.get('run_id') != run_id or value.get('status') != 'completed' or
            value.get('synthetic_only') is not True or value.get('model_sha256') != model_sha or
            (infant_presence and value.get('infant_presence_candidate') is not True)):
        raise ValueError('Stale, failed or unexpected benchmark result')
    cases = value.get('cases')
    if not isinstance(cases, list) or len(cases) != 3:
        raise ValueError('Missing measurement cases')
    for index, (row, duration) in enumerate(zip(cases, (10000, 20000, 20000))):
        if (row.get('index') != index or row.get('duration_ms') != duration or
                row.get('input_samples') != duration * 16 or row.get('problem') is not None):
            raise ValueError('Wrong input or failed AST call')
        expected = [{'start_ms': 0, 'end_ms': 10000}]
        if duration == 20000:
            expected += [{'start_ms': 5000, 'end_ms': 15000}, {'start_ms': 10000, 'end_ms': 20000}]
        if row.get('windows') != expected:
            raise ValueError('Incomplete interval coverage')
        if any(not isinstance(w, dict) or set(w) != {'start_ms', 'end_ms'} or
               any(type(w[k]) is not int for k in ('start_ms', 'end_ms')) for w in row['windows']):
            raise ValueError('Invalid window schema')
        for key in ('elapsed_ms', 'peak_sampled_pss_kb', 'pss_samples'):
            if type(row.get(key)) is not int or row[key] < 1:
                raise ValueError('Missing or invalid runtime metric')
        for key in ('thermal_before', 'thermal_after'):
            if type(row.get(key)) is not int or not -1 <= row[key] < 3:
                raise ValueError('Invalid or severe thermal status')
    return value


def physical_arm_device(adb, serial):
    properties = {}
    for name in ('ro.kernel.qemu', 'ro.boot.qemu', 'ro.product.cpu.abi', 'ro.product.model', 'ro.build.version.sdk'):
        properties[name] = command(adb + ['shell', 'getprop', name]).decode().strip()
    if (serial.startswith('emulator-') or properties['ro.kernel.qemu'] not in ('', '0') or
            properties['ro.boot.qemu'] not in ('', '0') or
            properties['ro.product.cpu.abi'] not in ('arm64-v8a', 'armeabi-v7a') or
            not properties['ro.product.model'] or not properties['ro.build.version.sdk'].isdigit() or
            not 26 <= int(properties['ro.build.version.sdk']) <= 100):
        raise ValueError('Actual physical ARM Android phone required')
    return properties


def command(argv, timeout=30):
    result = subprocess.run(argv, capture_output=True, timeout=timeout)
    result.check_returncode()
    return result.stdout


def installed(adb, package):
    rows = command(adb + ['shell', 'pm', 'list', 'packages', '--user', '0', package]).decode().splitlines()
    return 'package:' + package in rows


def storage_snapshot(directory):
    """Measure only this fresh owned run directory, never user media or caches."""
    files = [path for path in directory.rglob('*') if path.is_file() and not path.is_symlink()]
    return {'measured_at': datetime.now(timezone.utc).isoformat(), 'scope': str(directory),
            'file_count': len(files), 'folder_logical_bytes': sum(path.stat().st_size for path in files),
            'drive_free_bytes': shutil.disk_usage(directory).free}


def production_snapshot(adb):
    if not installed(adb, PRODUCTION):
        return {'installed': False}
    metadata = command(adb + ['shell', 'dumpsys', 'package', PRODUCTION]).decode('utf-8', 'replace')
    paths = command(adb + ['shell', 'pm', 'path', PRODUCTION]).decode().splitlines()
    hashes = []
    for line in paths:
        path = line.removeprefix('package:').strip()
        if not re.fullmatch(r'/data/app/[A-Za-z0-9_./+=~-]+\.apk', path):
            raise ValueError('Unverified production APK path')
        hashes.append(command(adb + ['shell', 'sha256sum', path]).decode().split()[0])
    result = {'installed': True, 'apk_hashes': sorted(hashes)}
    for key in ('versionCode', 'versionName', 'lastUpdateTime'):
        matches = re.findall(r'\b' + key + r'=([^\r\n]+)', metadata)
        if not matches:
            raise ValueError('Missing production install metadata')
        result[key] = matches[0].strip()
    return result


def stop_and_remove(adb, package=PACKAGE):
    errors = []
    for package in (package + '.test', package):
        present = True
        try:
            present = installed(adb, package)
        except Exception as error:
            errors.append(f'{package}: installation check: {error}')
        if present:
            try:
                command(adb + ['shell', 'am', 'force-stop', '--user', '0', package])
            except Exception as error:
                errors.append(f'{package}: force-stop: {error}')
            try:
                if command(adb + ['uninstall', package]).strip() != b'Success':
                    raise RuntimeError('Probe uninstall was not confirmed')
            except Exception as error:
                errors.append(f'{package}: uninstall: {error}')
        try:
            if installed(adb, package):
                raise RuntimeError('Probe package remains installed')
        except Exception as error:
            errors.append(f'{package}: absence check: {error}')
        try:
            r = subprocess.run(adb + ['shell', 'pidof', package], capture_output=True, timeout=15)
            if r.returncode != 1 or r.stdout.strip() or r.stderr.strip():
                raise RuntimeError('Probe process termination was not confirmed')
        except Exception as error:
            errors.append(f'{package}: termination check: {error}')
    if errors:
        raise RuntimeError('; '.join(errors))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--apkanalyzer', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--infant-presence-candidate', action='store_true',
                        help='Measure the pinned integrated candidate on a physical ARM phone, not an emulator.')
    args = parser.parse_args(argv)
    infant = args.infant_presence_candidate
    package = CANDIDATE_PACKAGE if infant else PACKAGE
    test_package = package + '.test'
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    storage_before = storage_snapshot(out)
    frozen = tempfile.TemporaryDirectory(prefix='.frozen-apks-', dir=out)
    adb = [str(args.adb.resolve()), '-s', args.serial]
    run_id = uuid.uuid4().hex
    result = {'run_id': run_id, 'status': 'fail', 'quality_validated': False, 'stage': 'apk_preflight'}
    result['storage_before'] = storage_before
    touched = False
    before = None
    try:
        app_apk, test_apk = Path(frozen.name) / 'probe-app.apk', Path(frozen.name) / 'probe-test.apk'
        shutil.copyfile(args.apk, app_apk)
        shutil.copyfile(args.test_apk, test_apk)
        for key, apk, is_test in [('app', app_apk, False), ('test', test_apk, True)]:
            xml = command([str(args.apkanalyzer.resolve()), 'manifest', 'print', str(apk.resolve())], timeout=60).decode('utf-8')
            result[key + '_manifest'] = validate_manifest(xml, test=is_test, infant_presence=infant)
        result['provenance'] = local_provenance(app_apk, test_apk, ROOT)
        if infant:
            for role, expected in [('app_apk', CANDIDATE_APK_SHA), ('test_apk', CANDIDATE_TEST_SHA)]:
                if result['provenance'][role]['sha256'] != expected:
                    raise ValueError('Use the exact reviewed infant-presence APK/test pair')
        result['stage'] = 'device_preflight'
        if command(adb + ['get-state']).strip() != b'device':
            raise RuntimeError('Requested device is not available')
        if command(adb + ['shell', 'am', 'get-current-user']).strip() != b'0':
            raise RuntimeError('Use the primary Android user for this probe')
        if installed(adb, package) or installed(adb, test_package):
            raise RuntimeError('An existing probe install must be reviewed before reuse')
        if infant:
            result['physical_device'] = physical_arm_device(adb, args.serial)
        before = production_snapshot(adb)
        result['production_before'] = before
        result['stage'] = 'installation'
        touched = True
        for key, apk, installed_package in [('app', app_apk, package), ('test', test_apk, test_package)]:
            raw = command(adb + ['install', '--user', '0', str(apk.resolve())], timeout=120)
            (out / ('install-' + key + '.log')).write_bytes(raw)
            with apk.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            proof = installed_provenance(adb, installed_package, digest, key == 'app')
            result[key + '_installed'] = proof
            if not proof['verified']:
                raise RuntimeError('Installed APK differs from requested artifact')
        result['stage'] = 'instrumentation'
        # Benchmark first: native recovery checks must not warm the measured cold call.
        groups = [
            ('benchmark', PRODUCTION + '.AstDeviceBenchmarkTest',
             ['-e', 'ast_infant_device_probe' if infant else 'ast_device_probe', 'true', '-e', 'ast_device_run_id', run_id]),
            ('recovery', ','.join(PRODUCTION + '.AstAudioAnalyzerTest#' + name for name in (
                'analyzerCancellationReleasesOwnerAndSubsequentRunSucceeds',
                'cancellingNativeRunTerminatesAndFreshRunOptionsResume')), []),
        ]
        for name, classes, extras in groups:
            start = time.monotonic()
            r = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', classes] + extras +
                [test_package + '/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, timeout=180)
            raw = (r.stdout + r.stderr).decode('utf-8', 'replace')
            (out / (name + '-instrumentation.txt')).write_text(raw, encoding='utf-8')
            verdict = parse_result(raw, r.returncode, time.monotonic() - start)
            result[name] = verdict
            if verdict['status'] != 'pass':
                raise RuntimeError('Instrumentation did not pass: ' + name)
            if name == 'benchmark':
                data = command(adb + ['exec-out', 'run-as', package, 'cat', 'files/qa-ast-device/' + run_id + '.json'])
                value = validate_measurement(json.loads(data), run_id, infant_presence=infant)
                (out / 'measurement.json').write_bytes(data)
                result['measurement'] = value
        result['status'] = 'pass'
        result['stage'] = 'complete'
    except Exception as error:
        result.update(status='fail', error_type=type(error).__name__, error=str(error))
        if isinstance(error, subprocess.TimeoutExpired):
            (out / 'timeout.txt').write_bytes((error.stdout or b'') + (error.stderr or b''))
    finally:
        if touched:
            try:
                stop_and_remove(adb, package)
                result['cleanup_complete'] = True
            except Exception as error:
                result.update(status='fail', cleanup_complete=False, cleanup_error=str(error))
        if before is not None:
            try:
                after = production_snapshot(adb)
                result['production_after'] = after
                result['production_install_unchanged'] = before == after
                if before != after:
                    result['status'] = 'fail'
            except Exception as error:
                result.update(status='fail', production_check_error=str(error))
        try:
            frozen.cleanup()
            result['frozen_apk_copies_removed'] = not Path(frozen.name).exists()
            if not result['frozen_apk_copies_removed']:
                result['status'] = 'fail'
        except OSError as error:
            result.update(status='fail', frozen_apk_copies_removed=False, frozen_copy_cleanup_error=str(error))
        try:
            result['storage_after'] = storage_snapshot(out)
        except OSError as error:
            result.update(status='fail', storage_measurement_error=str(error))
        result['completed_at'] = datetime.now(timezone.utc).isoformat()
        if infant and result['status'] == 'pass':
            try:
                sys.path.insert(0, str(ROOT / 'tools/release'))
                from verify_infant_presence_arm import verify
                verify(result, out)
                result['arm_gate_passed'] = True
            except Exception as error:
                result.update(status='fail', arm_gate_passed=False, arm_gate_error=str(error))
        (out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: result[k] for k in ('run_id', 'status', 'stage', 'cleanup_complete', 'production_install_unchanged') if k in result}))
    return 0 if result['status'] == 'pass' else 1


if __name__ == '__main__':
    raise SystemExit(main())
