"""Host-only adversarial checks. No device, Gradle, network, or signing keys."""
import unittest
import hashlib
import subprocess
import tempfile
import json
from pathlib import Path
from unittest.mock import patch

import check_package
import instrument
from instrument import parse_result


def event(name, status, total=1, current=1):
    return (f"INSTRUMENTATION_STATUS: class=example.Tests\n"
            f"INSTRUMENTATION_STATUS: test={name}\n"
            f"INSTRUMENTATION_STATUS: numtests={total}\n"
            f"INSTRUMENTATION_STATUS: current={current}\n"
            f"INSTRUMENTATION_STATUS_CODE: {status}\n")


def finish(total=1, code=-1):
    return f"OK ({total} tests)\nINSTRUMENTATION_CODE: {code}\n"


class DistributionGateTest(unittest.TestCase):
    def manifest(self, value):
        return ('A: http://schemas.android.com/apk/res/android:name="com.moa.babylanguage.TEST_DISTRIBUTION"\n'
                '  A: http://schemas.android.com/apk/res/android:value(0x01010024)='+value+'\n')

    def test_correct_release_and_preview_pass(self):
        check_package.assert_distribution(self.manifest('false'), "package: versionName='0.5.2'", 'release')
        check_package.assert_distribution(self.manifest('true'), "package: versionName='0.5.2-test'", 'preview')

    def test_preview_cannot_be_reported_as_release(self):
        with self.assertRaises(ValueError):
            check_package.assert_distribution(self.manifest('true'), "package: versionName='0.5.2-test'", 'release')

    def test_missing_ambiguous_or_mislabelled_marker_fails(self):
        for manifest, name in [('', '0.5.2'), (self.manifest('false')*2, '0.5.2'), (self.manifest('false'),'0.5.2-test')]:
            with self.subTest(manifest=manifest, name=name), self.assertRaises(ValueError):
                check_package.assert_distribution(manifest, "package: versionName='"+name+"'", 'release')


class InstrumentationGateTest(unittest.TestCase):
    def test_complete_run_passes(self):
        verdict = parse_result(event("one", 1) + event("one", 0) + finish(), 0, .1)
        self.assertEqual("pass", verdict["status"])
        self.assertEqual(1, verdict["passed_tests"])

    def test_ok_line_without_results_fails(self):
        self.assertEqual("fail", parse_result(finish(), 0, .1)["status"])

    def test_duplicate_terminal_results_fail(self):
        raw = event("one", 1) + event("one", 0) * 2 + finish()
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])

    def test_incomplete_run_fails_even_with_ok_summary(self):
        raw = event("one", 1, 2) + event("one", 0, 2) + event("two", 1, 2, 2) + finish(2)
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])

    def test_missing_runner_end_fails(self):
        raw = event("one", 1) + event("one", 0) + "OK (1 test)\n"
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])

    def test_abnormal_runner_end_fails_even_with_host_exit_zero(self):
        raw = event("one", 1) + event("one", 0) + finish(code=0)
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])

    def test_unexpected_skip_fails(self):
        raw = event("one", 1, 2) + event("one", 0, 2) + event("two", 1, 2, 2) + event("two", -4, 2, 2) + finish(2)
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])

    def test_explicit_expected_skip_is_reported_separately(self):
        raw = event("one", 1, 2) + event("one", 0, 2) + event("two", 1, 2, 2) + event("two", -4, 2, 2) + finish(2)
        verdict = parse_result(raw, 0, .1, allowed_skips={"example.Tests#two"})
        self.assertEqual("pass", verdict["status"])
        self.assertEqual(1, verdict["passed_tests"])
        self.assertEqual(1, verdict["assumption_skipped_tests"])

    def test_all_skipped_fails_even_if_allowed(self):
        raw = event("one", 1) + event("one", -4) + finish()
        self.assertEqual("fail", parse_result(raw, 0, .1, allowed_skips={"example.Tests#one"})["status"])

    def test_failure_timeout_and_host_failure_fail(self):
        for raw, code in [(event("one", 1) + event("one", -2) + finish(), 0),
                          (event("one", 1) + event("one", 0) + finish() + "HARNESS TIMEOUT", 0),
                          (event("one", 1) + event("one", 0) + finish(), 1)]:
            with self.subTest(raw=raw, code=code):
                self.assertEqual("fail", parse_result(raw, code, .1)["status"])

    def test_inconsistent_discovered_count_fails(self):
        raw = event("one", 1, 2) + event("one", 0, 2) + finish(1)
        self.assertEqual("fail", parse_result(raw, 0, .1)["status"])


class BundleSignatureGateTest(unittest.TestCase):
    def test_unsigned_bundle_is_rejected_by_default(self):
        with self.assertRaises(ValueError):
            check_package.aab_signature_state("jar is unsigned.")

    def test_signed_bundle_is_accepted_with_self_signed_warning(self):
        self.assertFalse(check_package.aab_signature_state("jar verified.\nWarning: signer certificate is self-signed."))

    def test_intentionally_unsigned_bundle_requires_explicit_mode(self):
        self.assertTrue(check_package.aab_signature_state("jar is unsigned.", expect_unsigned=True))
        with self.assertRaises(ValueError):
            check_package.aab_signature_state("jar verified.", expect_unsigned=True)

    def test_missing_verification_and_partially_unsigned_bundle_fail(self):
        for output in ["", "Warning: something went wrong", "jar verified.\nThis jar contains unsigned entries."]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                check_package.aab_signature_state(output)


class ExecutionProvenanceTest(unittest.TestCase):
    def test_local_apk_hashes_and_git_state_are_recorded(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            app, test = root / "app.apk", root / "test.apk"
            app.write_bytes(b"intended app")
            test.write_bytes(b"intended tests")
            responses = [subprocess.CompletedProcess([], 0, "a" * 40 + "\n", ""),
                         subprocess.CompletedProcess([], 0, " M code.kt\n", "")]
            with patch("instrument.subprocess.run", side_effect=responses):
                result = instrument.local_provenance(app, test, root)
            self.assertEqual(hashlib.sha256(b"intended app").hexdigest(), result["app_apk"]["sha256"])
            self.assertEqual(hashlib.sha256(b"intended tests").hexdigest(), result["test_apk"]["sha256"])
            self.assertEqual("a" * 40, result["git"]["sha"])
            self.assertTrue(result["git"]["dirty"])

    def test_installed_base_hash_and_target_version_are_verified(self):
        digest = "a" * 64
        results = [subprocess.CompletedProcess([], 0, "package:/data/app/owned/base.apk\n", ""),
                   subprocess.CompletedProcess([], 0, digest + "  /data/app/owned/base.apk\n", ""),
                   subprocess.CompletedProcess([], 0, "versionCode=13 minSdk=26\nversionName=0.5.0\n", "")]
        with patch("instrument.subprocess.run", side_effect=results) as run:
            result = instrument.installed_provenance(["adb", "-s", "owned"], "example.app", digest)
        self.assertTrue(result["verified"])
        self.assertEqual(13, result["version_code"])
        self.assertEqual("0.5.0", result["version_name"])
        self.assertEqual(["adb", "-s", "owned", "shell", "sha256sum", "/data/app/owned/base.apk"], run.call_args_list[1].args[0])

    def test_stale_installed_app_is_rejected_without_install_flag(self):
        results = [subprocess.CompletedProcess([], 0, "package:/data/app/owned/base.apk\n", ""),
                   subprocess.CompletedProcess([], 0, "b" * 64 + "  /data/app/owned/base.apk\n", ""),
                   subprocess.CompletedProcess([], 0, "versionCode=12\nversionName=0.4.5\n", "")]
        with patch("instrument.subprocess.run", side_effect=results):
            result = instrument.installed_provenance(["adb", "-s", "owned"], "example.app", "a" * 64)
        self.assertFalse(result["verified"])
        self.assertIn("hash", " ".join(result["errors"]).lower())

    def test_split_or_missing_package_is_rejected(self):
        for output in ["", "package:/data/app/owned/base.apk\npackage:/data/app/owned/split.apk\n"]:
            with self.subTest(output=output), patch("instrument.subprocess.run", return_value=subprocess.CompletedProcess([], 0, output, "")):
                with self.assertRaises(RuntimeError):
                    instrument.installed_provenance(["adb", "-s", "owned"], "example.app", "a" * 64)

    def test_unreadable_hash_or_missing_target_version_fails(self):
        for hash_output, metadata in [("permission denied", "versionCode=13\nversionName=0.5.0\n"),
                                      ("a" * 64 + "  /data/app/owned/base.apk", "")]:
            responses = [subprocess.CompletedProcess([], 0, "package:/data/app/owned/base.apk\n", ""),
                         subprocess.CompletedProcess([], 0, hash_output, ""),
                         subprocess.CompletedProcess([], 0, metadata, "")]
            with self.subTest(hash_output=hash_output), patch("instrument.subprocess.run", side_effect=responses):
                result = instrument.installed_provenance(["adb", "-s", "owned"], "example.app", "a" * 64)
            self.assertFalse(result["verified"])

    def test_main_never_starts_instrumentation_on_stale_install_without_install_flag(self):
        with tempfile.TemporaryDirectory() as folder:
            with patch("instrument.evidence_dir", return_value=Path(folder)), \
                 patch("instrument.adb_command", return_value=["adb", "-s", "owned"]), \
                 patch("instrument.local_provenance", return_value={"app_apk": {"sha256": "a" * 64}, "test_apk": {"sha256": "b" * 64}}), \
                 patch("instrument.installed_provenance", return_value={"verified": False, "errors": ["Installed APK hash differs"]}), \
                 patch("instrument.subprocess.run", return_value=subprocess.CompletedProcess([], 0, b"Success", b"")) as commands, \
                 patch("sys.argv", ["instrument.py"]), patch("builtins.print"):
                self.assertEqual(1, instrument.main())
            verdict = json.loads((Path(folder) / "instrumentation-result.json").read_text(encoding="utf-8"))
            self.assertFalse(verdict["instrumentation_started"])
            self.assertEqual("provenance", verdict["stage"])
            self.assertEqual(1, commands.call_count)  # Only the test APK install.
            self.assertNotIn("instrument", commands.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
