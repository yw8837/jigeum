"""Bounded packaging checks; uses existing pinned synthetic files, never inference.

Run from tools/research with: python -m unittest test_export_gemma_reproducer -v
The parent owns execution; real-fixture CLI export is a separate integration gate.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image
import export_gemma_reproducer as exporter


class ReproducerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in exporter.SOURCE_FILES:
            destination = self.root / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(b"test source\n")
        for run, spec in exporter.RUNS.items():
            base = f"work/gemma-android/{run}"
            files = ["result-private.json", "response-private.txt", "input/bundle.json", "input/audio.wav"]
            files += [f"input/frame-{i:03d}.png" for i in range(spec["seconds"])]
            for name in files:
                destination = self.root / base / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(exporter.REPO / base / name, destination)

    def first(self, name):
        return self.root / "work/gemma-android/s25-av10-gpu-01" / name

    def mutate_json(self, name, change):
        file = self.first(name)
        value = json.loads(file.read_bytes())
        change(value)
        file.write_bytes(exporter.json_bytes(value))

    def test_allowlist_and_portable_inventory_are_deterministic(self):
        # Extra files outside fixed inputs are ignored, never recursively collected.
        self.first("do-not-export.log").write_text("PRIVATE_SERIAL")
        self.first("model.litertlm").write_text("PRIVATE_MODEL")
        members = exporter.build_export(self.root)
        self.assertEqual(members, exporter.build_export(self.root))
        self.assertEqual(len(members), 57)
        self.assertEqual(members["cases/s25-av10-gpu-01/input/audio.wav"], self.first("input/audio.wav").read_bytes())
        inventory = json.loads(members["sha256-inventory.json"])
        self.assertEqual(set(inventory), set(members) - {"sha256-inventory.json"})
        for name, entry in inventory.items():
            self.assertEqual(entry, {"sha256": exporter.digest(members[name]), "bytes": len(members[name])})
        for run in exporter.RUNS:
            portable = json.loads(members[f"cases/{run}/bundle-portable.json"])
            for name in [portable["audio"]] + [frame["path"] for frame in portable["frames"]]:
                self.assertFalse(Path(name).is_absolute())
                self.assertNotIn("..", Path(name).parts)
                self.assertIn(name, members)
        self.assertNotIn(b"PRIVATE_SERIAL", b"".join(members.values()))
        self.assertNotIn(b"PRIVATE_MODEL", b"".join(members.values()))

    def test_default_dry_run_does_not_create_output(self):
        result = exporter.export(root=self.root)
        self.assertFalse(result["written"])
        self.assertEqual(result["file_count"], 57)
        self.assertFalse((self.root / "work/out").exists())

    def test_changed_and_non_synthetic_media_are_rejected(self):
        file = self.first("input/audio.wav")
        original = file.read_bytes()
        file.write_bytes(original[:-2] + b"\x01\x00")
        with self.assertRaisesRegex(ValueError, "Pinned WAV changed"):
            exporter.build_export(self.root)
        file.write_bytes(original)
        image = self.first("input/frame-000.png")
        with Image.open(image) as source:
            changed = source.copy()
        try:
            changed.putpixel((30, 30), (120, 90, 50))
            changed.save(image)
        finally:
            changed.close()
        with self.assertRaisesRegex(ValueError, "Pinned frame changed"):
            exporter.build_export(self.root)

    def test_unexpected_input_entries_refuse_before_output(self):
        self.first("input/extra-user-record.json").write_text("PRIVATE_RECORD")
        with patch.object(exporter.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
            with self.assertRaisesRegex(ValueError, "Unexpected input entries"):
                exporter.export("work/out", self.root)
        self.assertFalse((self.root / "work/out").exists())

    def test_string_metrics_are_rejected_and_private_strings_are_dropped(self):
        self.mutate_json("result-private.json", lambda result: result.update({
            "private_key": "PRIVATE_SECRET", "response_file": "C:/PRIVATE_PATH/token",
            "error_message": "PRIVATE_MESSAGE", "device_model": "PRIVATE_SERIAL"}))
        members = exporter.build_export(self.root)
        safe = members["cases/s25-av10-gpu-01/metrics.json"]
        for secret in (b"PRIVATE_SECRET", b"PRIVATE_PATH", b"PRIVATE_MESSAGE", b"PRIVATE_SERIAL"):
            self.assertNotIn(secret, safe)
        self.mutate_json("result-private.json", lambda result: result.update({"inference_ms": "PRIVATE_SECRET"}))
        with self.assertRaisesRegex(ValueError, "Metric has invalid type"):
            exporter.build_export(self.root)

    def test_nonfinite_metrics_and_string_capabilities_are_rejected(self):
        original = self.first("result-private.json").read_bytes()
        for key, value in (("inference_ms", float("nan")), ("capabilities", {"audio": "true"})):
            self.first("result-private.json").write_bytes(original)
            result = json.loads(original)
            result[key] = value
            self.first("result-private.json").write_text(json.dumps(result))
            with self.assertRaises(ValueError):
                exporter.build_export(self.root)

    def test_changed_response_even_with_forged_matching_result_is_rejected(self):
        response = b"arbitrary private output"
        self.first("response-private.txt").write_bytes(response)
        self.mutate_json("result-private.json", lambda result: result.update({
            "response_sha256": exporter.digest(response), "response_bytes": len(response)}))
        with self.assertRaisesRegex(ValueError, "Response evidence mismatch"):
            exporter.build_export(self.root)

    def test_traversal_and_existing_output_are_refused(self):
        for output in ("work/../escaped", str(self.root.parent / "outside")):
            with self.assertRaises(ValueError):
                exporter.output_path(self.root, output)
        output = self.root / "work/existing"
        output.mkdir()
        marker = output / "keep"
        marker.write_text("preserve")
        with self.assertRaisesRegex(ValueError, "Output exists"):
            exporter.export(output, self.root)
        self.assertEqual(marker.read_text(), "preserve")
        self.mutate_json("input/bundle.json", lambda result: result.update({"audio": "../private.wav"}))
        with self.assertRaises(ValueError):
            exporter.build_export(self.root)

    def test_symlink_source_and_output_are_refused(self):
        file = self.first("input/audio.wav")
        target = self.first("audio-copy.wav")
        file.rename(target)
        try:
            file.symlink_to(target)
        except OSError:
            self.skipTest("Host does not permit symlink creation")
        with self.assertRaisesRegex(ValueError, "Linked path refused"):
            exporter.build_export(self.root)
        link = self.root / "work/linked"
        link.symlink_to(self.root / "work/gemma-android", target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "Linked path refused"):
            exporter.output_path(self.root, link / "out")

    @unittest.skipUnless(os.name == "nt", "Actual Windows junction boundary")
    def test_actual_windows_junction_refuses_source_and_output_ancestor(self):
        shell = shutil.which("pwsh") or shutil.which("powershell")
        if shell is None:
            self.skipTest("Windows PowerShell is unavailable")
        link = self.first("input")
        target = self.first("owned-input-target")
        self.assertTrue(link.resolve().is_relative_to(self.root.resolve()))
        self.assertTrue(target.resolve().is_relative_to(self.root.resolve()))
        link.rename(target)
        sentinel = target / "preserve-sentinel"
        sentinel.write_bytes(b"owned target must survive junction removal")
        environment = dict(os.environ, AGATTOK_JUNCTION_LINK=str(link), AGATTOK_JUNCTION_TARGET=str(target))

        def powershell(script):
            return subprocess.run([shell, "-NoProfile", "-NonInteractive", "-Command", script],
                                  env=environment, capture_output=True, timeout=15,
                                  creationflags=subprocess.CREATE_NO_WINDOW)

        try:
            created = powershell("$ErrorActionPreference = 'Stop'; New-Item -ItemType Junction "
                                 "-Path $env:AGATTOK_JUNCTION_LINK -Target $env:AGATTOK_JUNCTION_TARGET | Out-Null")
            self.assertEqual(created.returncode, 0, "Owned junction creation failed")
            with self.assertRaisesRegex(ValueError, "Linked path refused"):
                exporter.build_export(self.root)
            with self.assertRaisesRegex(ValueError, "Linked path refused"):
                exporter.output_path(self.root, link / "new-output")
            self.assertFalse((target / "new-output").exists())
            self.assertEqual(sentinel.read_bytes(), b"owned target must survive junction removal")
        finally:
            # Native literal-path unlink only: never recurse through a junction.
            removed = powershell("$ErrorActionPreference = 'Stop'; if (Test-Path -LiteralPath "
                                 "$env:AGATTOK_JUNCTION_LINK) { Remove-Item -LiteralPath "
                                 "$env:AGATTOK_JUNCTION_LINK -Force }")
            self.assertEqual(removed.returncode, 0, "Owned junction unlink failed")
            self.assertFalse(link.exists())
            self.assertEqual(sentinel.read_bytes(), b"owned target must survive junction removal")
            target.rename(link)

    def test_explicit_output_requires_git_ignore_and_preserves_exact_members(self):
        with patch.object(exporter.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaisesRegex(ValueError, "not Git-ignored"):
                exporter.export("work/out", self.root)
        self.assertFalse((self.root / "work/out").exists())
        with patch.object(exporter.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
            result = exporter.export("work/out", self.root)
        self.assertTrue(result["written"])
        output = self.root / "work/out"
        expected = exporter.build_export(self.root)
        self.assertEqual({p.relative_to(output).as_posix() for p in output.rglob("*") if p.is_file()}, set(expected))
        for name, data in expected.items():
            self.assertEqual((output / name).read_bytes(), data)


if __name__ == "__main__":
    unittest.main()
