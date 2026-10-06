"""Fictional metadata fixtures only; these tests cannot authorize a real beta."""
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import verify_infant_presence_arm as gate
from ast_device_benchmark import CANDIDATE_APK_SHA, CANDIDATE_TEST_SHA, CANDIDATE_SHA, CANDIDATE_PACKAGE
from instrument import parse_result

NOW = datetime(2026, 10, 7, tzinfo=timezone.utc)


def fixture(directory):
    value = dict(run_id='a' * 32, status='pass', stage='complete', cleanup_complete=True,
                 quality_validated=False, production_install_unchanged=True, frozen_apk_copies_removed=True,
                 completed_at=NOW.isoformat(), fixture_only=True,
                 production_before={'installed': False}, production_after={'installed': False},
                 physical_device={'ro.kernel.qemu': '0', 'ro.boot.qemu': '',
                                  'ro.product.cpu.abi': 'arm64-v8a', 'ro.product.model': 'FICTIONAL_TEST_ONLY',
                                  'ro.build.version.sdk': '36'}, provenance={})
    for role, digest, size in [('app', CANDIDATE_APK_SHA, 265_353_775), ('test', CANDIDATE_TEST_SHA, 2_765_526)]:
        value['provenance'][role + '_apk'] = dict(sha256=digest, bytes=size)
        value[role + '_installed'] = dict(verified=True, sha256=digest, expected_sha256=digest,
            package=CANDIDATE_PACKAGE + ('.test' if role == 'test' else ''), errors=[])
    cases = []
    for index, duration in enumerate((10000, 20000, 20000)):
        windows = [{'start_ms': 0, 'end_ms': 10000}]
        if duration == 20000:
            windows += [{'start_ms': 5000, 'end_ms': 15000}, {'start_ms': 10000, 'end_ms': 20000}]
        cases.append(dict(index=index, duration_ms=duration, input_samples=duration * 16, problem=None,
            elapsed_ms=1000, peak_sampled_pss_kb=500000, pss_samples=10, thermal_before=0, thermal_after=0,
            windows=windows))
    value['measurement'] = dict(run_id=value['run_id'], status='completed', synthetic_only=True,
        model_sha256=CANDIDATE_SHA, infant_presence_candidate=True, abi='arm64-v8a',
        device_model='FICTIONAL_TEST_ONLY', api=36, cases=cases)
    for group, names in gate.TESTS.items():
        cls = 'com.moa.babylanguage.' + ('AstDeviceBenchmarkTest' if group == 'benchmark' else 'AstAudioAnalyzerTest')
        lines = []
        for index, name in enumerate(sorted(names), 1):
            for status in (1, 0):
                lines += [f'INSTRUMENTATION_STATUS: class={cls}', f'INSTRUMENTATION_STATUS: test={name}',
                          f'INSTRUMENTATION_STATUS: numtests={len(names)}', f'INSTRUMENTATION_STATUS: current={index}',
                          f'INSTRUMENTATION_STATUS_CODE: {status}']
        raw = '\n'.join(lines + [f'OK ({len(names)} tests)', 'INSTRUMENTATION_CODE: -1', ''])
        (directory / (group + '-instrumentation.txt')).write_text(raw, encoding='utf-8')
        value[group] = parse_result(raw, 0, 1)
    return value


class ArmGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.value = fixture(self.directory)

    def tearDown(self):
        self.temp.cleanup()

    def test_valid_fixture_is_unit_only_and_keeps_accuracy_unvalidated(self):
        result = gate.verify(self.value, self.directory, now=NOW)
        self.assertFalse(result['general_accuracy_validated'])

    def reject(self, mutations):
        for change in mutations:
            value = copy.deepcopy(self.value); change(value)
            with self.subTest(change=change), self.assertRaises((ValueError, TypeError, KeyError, AttributeError)):
                gate.verify(value, self.directory, now=NOW)

    def test_emulator_nonarm_mismatched_runtime_and_missing_properties_rejected(self):
        self.reject([lambda v: v['physical_device'].update({'ro.kernel.qemu': '1'}),
                     lambda v: v['physical_device'].update({'ro.product.cpu.abi': 'x86_64'}),
                     lambda v: v['physical_device'].pop('ro.boot.qemu'),
                     lambda v: v['measurement'].update(abi='armeabi-v7a'),
                     lambda v: v['measurement'].update(api=True),
                     lambda v: v['physical_device'].update({'ro.build.version.sdk': '25'})])

    def test_artifact_model_run_pins_and_cleanup_drift_rejected(self):
        self.reject([lambda v: v['provenance']['app_apk'].update(sha256='0' * 64),
                     lambda v: v['test_installed'].update(verified=False),
                     lambda v: v['measurement'].update(model_sha256='0' * 64),
                     lambda v: v['measurement'].update(run_id='b' * 32),
                     lambda v: v.update(cleanup_complete=False),
                     lambda v: v.update(production_install_unchanged=False),
                     lambda v: v['production_after'].update(installed=True)])

    def test_stale_future_naive_time_and_misleading_pass_rejected(self):
        self.reject([lambda v: v.update(completed_at='2026-09-01T00:00:00+00:00'),
                     lambda v: v.update(completed_at='2026-11-01T00:00:00+00:00'),
                     lambda v: v.update(completed_at='2026-10-07T00:00:00'),
                     lambda v: v.update(stage='instrumentation'),
                     lambda v: v.update(quality_validated=True),
                     lambda v: v.update(status='fail')])

    def test_missing_windows_runtime_error_slow_memory_heat_nan_bool_rejected(self):
        self.reject([lambda v: v['measurement']['cases'][1]['windows'].pop(),
                     lambda v: v['measurement']['cases'][0].update(problem='error'),
                     lambda v: v['measurement']['cases'][0].update(elapsed_ms=30001),
                     lambda v: v['measurement']['cases'][0].update(peak_sampled_pss_kb=921601),
                     lambda v: v['measurement']['cases'][0].update(thermal_after=3),
                     lambda v: v['measurement']['cases'][0].update(thermal_before=-1),
                     lambda v: v['measurement']['cases'][0].update(elapsed_ms=float('nan')),
                     lambda v: v['measurement']['cases'][0].update(pss_samples=True)])

    def test_missing_and_fake_ok_logs_do_not_pass(self):
        path = self.directory / 'recovery-instrumentation.txt'
        path.write_text('OK (2 tests)\nINSTRUMENTATION_CODE: -1\n', encoding='utf-8')
        with self.assertRaises(ValueError): gate.verify(self.value, self.directory, now=NOW)
        path.unlink()
        with self.assertRaises(ValueError): gate.verify(self.value, self.directory, now=NOW)

    def test_wrong_test_identity_cannot_reuse_same_pass_count(self):
        path = self.directory / 'benchmark-instrumentation.txt'
        path.write_text(path.read_text().replace('measureSyntheticTenAndTwentySecondCalls', 'unrelated'), encoding='utf-8')
        with self.assertRaises(ValueError): gate.verify(self.value, self.directory, now=NOW)

    def test_json_duplicate_nonfinite_oversized_and_missing_file_rejected(self):
        path = self.directory / 'malformed.json'
        for payload in ('{"status":"fail","status":"pass"}', '{"a":NaN}', ' ' * (gate.MAX_BYTES + 1)):
            path.write_text(payload, encoding='utf-8')
            with self.assertRaises(ValueError): gate.read_proof(path)
        with self.assertRaises(ValueError): gate.read_proof(self.directory / 'missing.json')

    def test_cli_rejects_absent_and_actual_emulator_measurement(self):
        path = self.directory / 'emulator.json'
        self.value['physical_device']['ro.kernel.qemu'] = '1'
        self.value['completed_at'] = datetime.now(timezone.utc).isoformat()
        path.write_text(json.dumps(self.value), encoding='utf-8')
        for proof in (path, self.directory / 'missing.json'):
            result = subprocess.run([sys.executable, gate.__file__, str(proof)], capture_output=True,
                                    text=True, timeout=15)
            self.assertEqual(1, result.returncode)
            self.assertEqual('fail', json.loads(result.stdout)['status'])


if __name__ == '__main__':
    unittest.main()
