"""Operational packaging gate for the frozen infant-presence candidate.

Only local synthetic physical-ARM measurements are accepted. This is a workflow
check, not signed device attestation or evidence of infant intent accuracy.
"""
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/research'))
from ast_device_benchmark import (CANDIDATE_APK_SHA, CANDIDATE_TEST_SHA,
                                  CANDIDATE_PACKAGE, validate_measurement)

MAX_BYTES = 128 * 1024
MAX_AST_MS = 30_000  # Leaves headroom within the existing 45-second analysis limit.
MAX_PSS_KB = 900 * 1024  # Process sampled peak, not native allocated bytes or whole-device RAM.
TESTS = {
    'benchmark': {'measureSyntheticTenAndTwentySecondCalls'},
    'recovery': {'analyzerCancellationReleasesOwnerAndSubsequentRunSucceeds',
                 'cancellingNativeRunTerminatesAndFreshRunOptionsResume'},
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result


def read_proof(path):
    if not path.is_file() or path.stat().st_size > MAX_BYTES:
        raise ValueError('ARM proof missing or oversized')
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_object,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def verify(value, directory, *, now=None):
    if not isinstance(value, dict):
        raise ValueError('Require a JSON object')
    run_id = value.get('run_id')
    if not isinstance(run_id, str) or not re.fullmatch('[a-f0-9]{32}', run_id):
        raise ValueError('Missing or malformed run ID')
    if (value.get('status') != 'pass' or value.get('stage') != 'complete' or
            value.get('cleanup_complete') is not True or
            value.get('frozen_apk_copies_removed') is not True or
            value.get('production_install_unchanged') is not True or
            value.get('quality_validated') is not False):
        raise ValueError('Incomplete run, cleanup or production preservation')
    completed = datetime.fromisoformat(value.get('completed_at', ''))
    now = now or datetime.now(timezone.utc)
    if completed.tzinfo is None or not now - timedelta(days=7) <= completed <= now + timedelta(minutes=5):
        raise ValueError('Stale, undated or future-dated physical measurement')
    device = value.get('physical_device', {})
    if (device.get('ro.kernel.qemu') not in ('', '0') or device.get('ro.boot.qemu') not in ('', '0') or
            device.get('ro.product.cpu.abi') not in ('arm64-v8a', 'armeabi-v7a') or
            not isinstance(device.get('ro.product.model'), str) or not device['ro.product.model'].strip()):
        raise ValueError('Physical ARM phone metadata required; emulators cannot approve beta')
    api_text = device.get('ro.build.version.sdk', '')
    if not isinstance(api_text, str) or not api_text.isdecimal() or not 26 <= int(api_text) <= 100:
        raise ValueError('Invalid device API')
    before, after = value.get('production_before'), value.get('production_after')
    if not isinstance(before, dict) or before != after or type(before.get('installed')) is not bool:
        raise ValueError('Missing or changed production snapshot')
    if before['installed'] and (not before.get('apk_hashes') or any(
            not isinstance(h, str) or not re.fullmatch('[a-f0-9]{64}', h) for h in before['apk_hashes']) or
            any(not before.get(k) for k in ('versionCode', 'versionName', 'lastUpdateTime'))):
        raise ValueError('Incomplete production install snapshot')
    for role, digest, size in [('app', CANDIDATE_APK_SHA, 265_353_775),
                               ('test', CANDIDATE_TEST_SHA, 2_765_526)]:
        artifact = value.get('provenance', {}).get(role + '_apk', {})
        installed = value.get(role + '_installed', {})
        expected_package = CANDIDATE_PACKAGE + ('.test' if role == 'test' else '')
        if (artifact.get('sha256') != digest or type(artifact.get('bytes')) is not int or artifact['bytes'] != size or
                installed.get('verified') is not True or installed.get('sha256') != digest or
                installed.get('expected_sha256') != digest or installed.get('package') != expected_package or
                installed.get('errors') != []):
            raise ValueError('Exact reviewed and installed APK/test provenance required')
    measurement = validate_measurement(value.get('measurement', {}), run_id, infant_presence=True)
    if (measurement.get('abi') != device['ro.product.cpu.abi'] or
            measurement.get('device_model') != device['ro.product.model'] or
            type(measurement.get('api')) is not int or measurement['api'] != int(api_text)):
        raise ValueError('Runtime measurement and physical phone disagree')
    for row in measurement['cases']:
        if row['elapsed_ms'] > MAX_AST_MS or row['peak_sampled_pss_kb'] > MAX_PSS_KB:
            raise ValueError('AST time or sampled process memory exceeds the declared gate')
        if int(api_text) >= 29 and (row['thermal_before'] < 0 or row['thermal_after'] < 0):
            raise ValueError('Thermal measurement unavailable on a supported API')
    # Reparse actual per-test terminal events rather than trusting a pass field.
    for group, names in TESTS.items():
        path = directory / (group + '-instrumentation.txt')
        if not path.is_file() or path.stat().st_size > MAX_BYTES:
            raise ValueError('Missing or oversized instrumentation evidence: ' + group)
        raw = path.read_text(encoding='utf-8')
        from instrument import parse_result  # Imported by the benchmark helper with the repo QA path.
        reported = value.get(group, {})
        parsed = parse_result(raw, reported.get('exit'), reported.get('seconds', 0))
        for key in ('status', 'tests', 'passed_tests', 'failed_tests', 'ignored_tests',
                    'assumption_skipped_tests', 'validation_errors'):
            if parsed[key] != reported.get(key):
                raise ValueError('Instrumentation summary drift: ' + group)
        if parsed['status'] != 'pass' or parsed['tests'] != len(names):
            raise ValueError('Failed, incomplete or skipped instrumentation: ' + group)
        actual_names = set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: test=(.*)$', raw))
        actual_classes = set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: class=(.*)$', raw))
        expected_class = 'com.moa.babylanguage.' + ('AstDeviceBenchmarkTest' if group == 'benchmark' else 'AstAudioAnalyzerTest')
        if actual_names != names or actual_classes != {expected_class}:
            raise ValueError('Wrong test identities: ' + group)
    return {'status': 'pass', 'run_id': run_id, 'model': device['ro.product.model'],
            'abi': device['ro.product.cpu.abi'], 'synthetic_only': True,
            'general_accuracy_validated': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('proof', type=Path)
    args = parser.parse_args(argv)
    try:
        print(json.dumps(verify(read_proof(args.proof), args.proof.resolve().parent)))
        return 0
    except (ValueError, TypeError, KeyError, AttributeError, OSError) as error:
        print(json.dumps({'status': 'fail', 'error': str(error)}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
