import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ast_device_benchmark as probe


def manifest(test=False):
    body = (f'<instrumentation android:targetPackage="{probe.PACKAGE}" />' if test else
            '<application android:allowBackup="false"><activity android:name="com.moa.babylanguage.MainActivity" android:enabled="false" /></application>')
    return f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{probe.TEST_PACKAGE if test else probe.PACKAGE}">{body}</manifest>'


def measurement():
    rows = []
    for index, duration in enumerate((10000, 20000, 20000)):
        windows = [{'start_ms': 0, 'end_ms': 10000}]
        if duration == 20000:
            windows += [{'start_ms': 5000, 'end_ms': 15000}, {'start_ms': 10000, 'end_ms': 20000}]
        rows.append(dict(index=index, duration_ms=duration, input_samples=duration * 16, problem=None,
                         elapsed_ms=1000, peak_sampled_pss_kb=200000, pss_samples=10,
                         thermal_before=0, thermal_after=0, windows=windows))
    return dict(run_id='a' * 32, status='completed', synthetic_only=True, model_sha256=probe.MODEL_SHA, cases=rows)


class AstDeviceProbeTest(unittest.TestCase):
    def test_only_standalone_package_and_test_target_are_allowed(self):
        probe.validate_manifest(manifest())
        probe.validate_manifest(manifest(True), test=True)
        for xml, test in [(manifest().replace(probe.PACKAGE, probe.PRODUCTION), False),
                          (manifest(True).replace('targetPackage="' + probe.PACKAGE, 'targetPackage="' + probe.PRODUCTION + '.wrong'), True)]:
            with self.assertRaises(ValueError):
                probe.validate_manifest(xml, test=test)

    def test_network_capture_billing_permissions_and_enabled_launcher_rejected(self):
        for permission in probe.PROHIBITED:
            xml = manifest().replace('</manifest>', f'<uses-permission android:name="{permission}" /></manifest>')
            with self.subTest(permission=permission), self.assertRaises(ValueError):
                probe.validate_manifest(xml)
        for xml in (manifest().replace('enabled="false"', 'enabled="true"'),
                    manifest().replace('allowBackup="false"', 'allowBackup="true"')):
            with self.assertRaises(ValueError): probe.validate_manifest(xml)

    def test_valid_full_measurement(self):
        self.assertEqual(measurement(), probe.validate_measurement(measurement(), 'a' * 32))

    def test_stale_failed_wrong_model_and_real_input_rejected(self):
        for key, value in [('run_id', 'b' * 32), ('status', 'failed'), ('model_sha256', '0' * 64), ('synthetic_only', False)]:
            data = measurement(); data[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): probe.validate_measurement(data, 'a' * 32)

    def test_short_coverage_model_error_missing_case_and_invalid_metrics_rejected(self):
        mutations = [lambda d: d['cases'].pop(),
                     lambda d: d['cases'][1]['windows'].pop(),
                     lambda d: d['cases'][1].update(input_samples=160000),
                     lambda d: d['cases'][1].update(problem='runtime failure'),
                     lambda d: d['cases'][1].update(elapsed_ms=float('nan')),
                     lambda d: d['cases'][1].update(peak_sampled_pss_kb=-1),
                     lambda d: d['cases'][1].update(thermal_after=3)]
        for mutate in mutations:
            data = measurement(); mutate(data)
            with self.assertRaises(ValueError): probe.validate_measurement(data, 'a' * 32)

    def test_test_package_cleanup_failure_still_attempts_main_probe_cleanup(self):
        def call(argv, **kwargs):
            if 'uninstall' in argv and argv[-1] == probe.TEST_PACKAGE:
                raise RuntimeError('Test package uninstall error')
            return b'Success'
        with patch.object(probe, 'installed', side_effect=[True, True, True, False]), \
             patch.object(probe, 'command', side_effect=call) as commands, \
             patch.object(probe.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, b'', b'')):
            with self.assertRaises(RuntimeError): probe.stop_and_remove(['adb', '-s', 'test-device'])
        self.assertTrue(any('uninstall' in c.args[0] and c.args[0][-1] == probe.PACKAGE for c in commands.call_args_list))

    def test_manifest_and_install_use_frozen_apk_copies(self):
        self.run_failure(mutate_source=True)
        self.assertEqual([b'app', b'test'], self.install_payloads)

    def run_failure(self, *, bad_apk=False, stale=False, cleanup_error=False, timeout=False, mutate_source=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); app = root/'app.apk'; test = root/'test.apk'
            app.write_bytes(b'app'); test.write_bytes(b'test')
            out = root/'evidence'
            self.install_payloads = []
            def call(argv, **kwargs):
                if 'manifest' in argv:
                    text = manifest(Path(argv[-1]).name in ('test.apk', 'probe-test.apk'))
                    if mutate_source:
                        app.write_bytes(b'replacement-app'); test.write_bytes(b'replacement-test')
                    if bad_apk: text = text.replace(probe.PACKAGE, probe.PRODUCTION)
                    return text.encode()
                if 'get-state' in argv: return b'device'
                if 'get-current-user' in argv: return b'0'
                if 'install' in argv:
                    self.install_payloads.append(Path(argv[-1]).read_bytes())
                    return b'Success'
                raise AssertionError('Unexpected call: '+repr(argv))
            failure = subprocess.TimeoutExpired('instrument', 180) if timeout else None
            with patch.object(probe, 'command', side_effect=call) as commands, \
                 patch.object(probe, 'local_provenance', return_value={}), \
                 patch.object(probe, 'installed', return_value=stale), \
                 patch.object(probe, 'production_snapshot', return_value={'installed': False}), \
                 patch.object(probe, 'installed_provenance', return_value={'verified': True}), \
                 patch.object(probe.subprocess, 'run', side_effect=failure,
                              return_value=subprocess.CompletedProcess([], 0, b'INSTRUMENTATION_FAILED', b'')), \
                 patch.object(probe, 'stop_and_remove', side_effect=RuntimeError('cleanup failed') if cleanup_error else None) as cleanup:
                code = probe.main(['--serial', 'explicit-device', '--adb', str(root/'adb'), '--apkanalyzer', str(root/'apkanalyzer'),
                                   '--apk', str(app), '--test-apk', str(test), '--output', str(out)])
            report = json.loads((out/'result.json').read_text(encoding='utf-8'))
            return code, report, commands.call_args_list, cleanup.call_count

    def test_bad_apk_rejected_before_any_device_command(self):
        code, report, calls, cleanups = self.run_failure(bad_apk=True)
        self.assertEqual(1, code); self.assertEqual('apk_preflight', report['stage'])
        self.assertEqual(1, len(calls)); self.assertEqual(0, cleanups)

    def test_existing_probe_is_not_overwritten_or_removed(self):
        code, report, calls, cleanups = self.run_failure(stale=True)
        self.assertEqual(1, code); self.assertEqual('device_preflight', report['stage'])
        self.assertFalse(any('install' in call.args[0] for call in calls)); self.assertEqual(0, cleanups)

    def test_failed_instrumentation_is_failed_and_own_cleanup_runs(self):
        code, report, calls, cleanups = self.run_failure()
        self.assertEqual(1, code); self.assertEqual(1, cleanups)
        self.assertTrue(report['cleanup_complete']); self.assertNotIn('measurement', report)

    def test_timeout_cannot_skip_cleanup(self):
        code, report, calls, cleanups = self.run_failure(timeout=True)
        self.assertEqual(1, code); self.assertEqual(1, cleanups)
        self.assertEqual('TimeoutExpired', report['error_type'])

    def test_cleanup_error_cannot_be_reported_as_pass(self):
        code, report, calls, cleanups = self.run_failure(cleanup_error=True)
        self.assertEqual(1, code); self.assertEqual(1, cleanups)
        self.assertFalse(report['cleanup_complete']); self.assertEqual('fail', report['status'])


if __name__ == '__main__':
    unittest.main()
