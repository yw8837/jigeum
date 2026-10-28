"""Host safety/evidence tests; not Android model quality tests."""
import json
import hashlib
from pathlib import Path
import tempfile
import unittest
import wave
import subprocess
import sys
from types import SimpleNamespace
from unittest.mock import patch

import gemma_android_probe as probe


class ProbeContractTest(unittest.TestCase):
    def test_visual_budget_reaches_prepared_plan_without_changing_media(self):
        from PIL import Image
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory(prefix="gemma-budget-", dir=repo / "work") as directory:
            root = Path(directory)
            original = probe.prepare_input(None, root / "source", "text")
            source = root / "source"
            Image.new("RGB", (16, 16), (200, 10, 30)).save(source / "one.png")
            original["audio"] = str(source / "audio.wav")
            original["frames"] = [{"offset_ms": 0, "path": str(source / "one.png")}]
            bundle = source / "bundle.json"
            bundle.write_text(json.dumps(original), encoding="utf-8")
            before = hashlib.sha256((source / "audio.wav").read_bytes()).hexdigest()
            result = subprocess.run([sys.executable, str(Path(probe.__file__).resolve()),
                                     "--prepare-only", "--mode", "av", "--bundle", str(bundle),
                                     "--output", str(root / "run"), "--run-id", "budget140",
                                     "--visual-token-budget", "140"], capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr.decode(errors="replace"))
            plan = json.loads((root / "run" / "plan.json").read_text(encoding="utf-8"))
            self.assertEqual(plan["visual_token_budget"], 140)
            self.assertEqual((plan["duration_ms"], plan["frame_count"]), (1000, 1))
            self.assertEqual(hashlib.sha256((root / "run" / "input" / "audio.wav").read_bytes()).hexdigest(), before)

    def test_visual_budget_wrong_mode_or_unsupported_value_creates_no_output(self):
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory(prefix="gemma-budget-", dir=repo / "work") as directory:
            for index, (mode, budget) in enumerate((("text", "70"), ("av", "560"), ("av", "-1"), ("av", "71"))):
                target = Path(directory) / str(index)
                result = subprocess.run([sys.executable, str(Path(probe.__file__).resolve()),
                                         "--prepare-only", "--mode", mode, "--output", str(target),
                                         "--run-id", "invalid", "--visual-token-budget", budget],
                                        capture_output=True, timeout=30)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(target.exists())

    def test_run_ids_cannot_escape_private_result_directory(self):
        self.assertEqual(probe.run_id("s25_cpu_10s-01"), "s25_cpu_10s-01")
        for value in ("", "../x", "x/y", "x;id", "a" * 41):
            with self.subTest(value=value), self.assertRaises(ValueError):
                probe.run_id(value)

    def test_apk_must_be_separate_probe_not_production(self):
        probe.validate_apk_badging("package: name='com.agattok.gemmaprobe' versionCode='1'\napplication-debuggable\n")
        for badging in (
            "package: name='com.moa.babylanguage'\napplication-debuggable\n",
            "package: name='com.agattok.gemmaprobe'\n",
            "package: name='com.agattok.gemmaprobe.fake'\napplication-debuggable\n",
        ):
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                probe.validate_apk_badging(badging)

    def test_internet_permission_is_rejected(self):
        probe.validate_apk_permissions("uses-permission: name='android.permission.WAKE_LOCK'")
        with self.assertRaises(ValueError):
            probe.validate_apk_permissions("uses-permission: name='android.permission.INTERNET'")

    def test_result_is_bound_to_exact_run_and_requires_cleanup(self):
        valid = dict(run_id="run01", status="completed", cleanup_complete=True,
                     inference_ms=10, response_bytes=20)
        self.assertEqual(probe.validate_result(valid, "run01"), "completed")
        for patch in ({"run_id": "previous"}, {"cleanup_complete": False},
                      {"status": "running"}, {"inference_ms": -1},
                      {"response_bytes": 0}, {"status": "failed"}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                probe.validate_result({**valid, **patch}, "run01")

    def test_result_from_another_visual_budget_cannot_validate_comparison(self):
        result = dict(run_id="budget140", status="completed", cleanup_complete=True,
                      inference_ms=10, response_bytes=20, visual_token_budget=140)
        self.assertEqual(probe.validate_result(result, "budget140", 140), "completed")
        for wrong in (None, True, 0, 70, 280, "140"):
            with self.subTest(wrong=wrong), self.assertRaisesRegex(ValueError, "Visual budget"):
                probe.validate_result({**result, "visual_token_budget": wrong}, "budget140", 140)

    def test_cancel_is_not_counted_as_completed_inference(self):
        result = dict(run_id="run01", status="cancelled", cleanup_complete=True,
                      cancel_requested=True, cancel_request_ms=100,
                      cancel_call_ms=2, cancel_terminal_ms=3)
        self.assertEqual(probe.validate_result(result, "run01"), "cancelled")
        with self.assertRaises(ValueError):
            probe.validate_result({**result, "cancel_requested": False}, "run01")
        for metric in ("cancel_request_ms", "cancel_call_ms", "cancel_terminal_ms"):
            with self.subTest(metric=metric), self.assertRaises(ValueError):
                probe.validate_result({**result, metric: -1}, "run01")

    def test_same_length_response_substitution_is_rejected(self):
        data = b"hello"
        metadata = {"response_bytes": len(data), "response_sha256": hashlib.sha256(data).hexdigest()}
        probe.verify_response(metadata, data)
        with self.assertRaises(ValueError):
            probe.verify_response(metadata, b"other")

    def test_device_symlink_is_rejected_before_transfer(self):
        with patch.object(probe.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout=b"", stderr=b"")):
            with self.assertRaises(RuntimeError):
                probe.require_no_symlink(["adb", "-s", "owned"], "files/input/audio.wav")

    def test_live_pid_or_broken_adb_is_not_accepted_as_cleanup(self):
        for answer in (SimpleNamespace(returncode=0, stdout=b"123", stderr=b""),
                       SimpleNamespace(returncode=1, stdout=b"", stderr=b"device missing")):
            with patch.object(probe.subprocess, "run", return_value=answer):
                with self.assertRaises(RuntimeError):
                    probe.require_stopped(["adb", "-s", "owned"])

    def test_setup_timeout_records_failure_and_stops_research_only(self):
        args = SimpleNamespace(adb="adb", serial="owned", apk="probe.apk")
        calls = []
        def fake_command(arguments, **kwargs):
            calls.append(arguments)
            if "get-state" in arguments:
                return b"device\n"
            if "get-current-user" in arguments:
                return b"0\n"
            if "install" in arguments:
                raise subprocess.TimeoutExpired(arguments, 120)
            return b""
        with tempfile.TemporaryDirectory() as directory, patch.object(probe, "command", side_effect=fake_command), \
                patch.object(probe, "require_stopped"):
            result = probe.device_run(args, Path(directory), {"run_id": "install_timeout"})
            self.assertEqual(result["host_status"], "failed")
            self.assertEqual(result["host_error"], "TimeoutExpired")
            self.assertFalse(result["android_executed"])
            self.assertTrue((Path(directory) / "summary.json").is_file())
            stopped = [call for call in calls if "force-stop" in call]
            self.assertEqual(stopped[0][-1], probe.PACKAGE)

    def test_model_copy_failure_removes_public_stage(self):
        calls = []
        def fake_command(arguments, **kwargs):
            calls.append(arguments)
            if "cp" in arguments:
                raise subprocess.CalledProcessError(1, arguments, b"", b"copy failed")
            return b""
        with patch.object(probe, "command", side_effect=fake_command), \
                patch.object(probe, "require_no_symlink"), \
                patch.object(probe.subprocess, "run", return_value=SimpleNamespace(returncode=1, stderr=b"")):
            with self.assertRaises(subprocess.CalledProcessError):
                probe.transfer_public_model(["adb", "-s", "owned"], "public-model.litertlm")
        pushed = next(call[-1] for call in calls if "push" in call)
        removed = next(call[-1] for call in calls if "rm" in call)
        self.assertEqual(pushed, removed)
        self.assertRegex(pushed, r"^/data/local/tmp/agattok-public-model-[0-9a-f]{32}\.litertlm$")

    def test_model_truncated_copy_is_not_success(self):
        def fake_command(arguments, **kwargs):
            return b"2" if "stat" in arguments else b""
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "public-model.litertlm"
            model.write_bytes(b"1234")
            with patch.object(probe, "command", side_effect=fake_command), \
                    patch.object(probe, "require_no_symlink"), \
                    patch.object(probe.subprocess, "run", return_value=SimpleNamespace(returncode=1, stderr=b"")):
                with self.assertRaisesRegex(RuntimeError, "incomplete"):
                    probe.transfer_public_model(["adb", "-s", "owned"], model)

    def test_public_summary_cannot_include_generated_text_or_input_paths(self):
        value = dict(run_id="run01", status="completed", inference_ms=23,
                     private_response="private baby observation", source="C:/private.mp4",
                     prompt="private", exception="private output", device_model="S25")
        public = probe.public_summary(value)
        self.assertEqual(public["inference_ms"], 23)
        serialized = json.dumps(public)
        self.assertNotIn("private", serialized)

    def test_text_preparation_needs_no_personal_media(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "input"
            bundle = probe.prepare_input(None, target, "text")
            self.assertEqual(bundle["duration_ms"], 1000)
            self.assertEqual(bundle["frames"], [])
            with wave.open(str(target / bundle["audio"]), "rb") as audio:
                self.assertEqual((audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getnframes()),
                                 (1, 2, 16000, 16000))

    def test_av_preparation_preserves_decoded_pixels_and_timestamps(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = probe.prepare_input(None, root / "source", "text")
            source = root / "source"
            Image.new("RGB", (16, 16), (200, 10, 30)).save(source / "one.jpg")
            Image.new("RGB", (16, 16), (30, 10, 200)).save(source / "two.jpg")
            original["audio"] = str(source / "audio.wav")
            original["frames"] = [{"offset_ms": i * 500, "path": str(source / name)}
                                  for i, name in enumerate(("one.jpg", "two.jpg"))]
            bundle_path = source / "bundle.json"
            bundle_path.write_text(json.dumps(original), encoding="utf-8")
            prepared = probe.prepare_input(bundle_path, root / "destination", "av")
            self.assertEqual([f["offset_ms"] for f in prepared["frames"]], [0, 500])
            for before, after in zip(original["frames"], prepared["frames"]):
                with Image.open(before["path"]) as a, Image.open(root / "destination" / after["path"]) as b:
                    self.assertEqual(b.format, "PNG")
                    self.assertEqual(a.convert("RGB").tobytes(), b.convert("RGB").tobytes())


if __name__ == "__main__":
    unittest.main()
