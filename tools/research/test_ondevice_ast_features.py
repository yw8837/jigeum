"""Independent frontend contract checks; model parity uses the local CLI."""
import unittest
import json
from pathlib import Path
import tempfile
from unittest.mock import patch
import numpy as np
from ondevice_ast_features import extract, mel_bank, synthetic_pcm, MEAN, STD, MEL_FLOOR
from ondevice_audio_candidate import window_ranges


class AstFrontendTests(unittest.TestCase):
    def test_public_golden_values(self):
        golden = json.loads(Path(__file__).with_name("ondevice_ast_golden.json").read_text(encoding="utf-8"))
        for case in golden["cases"]:
            features = extract(synthetic_pcm(case["duration_ms"], case["kind"]))
            for selected in case["selected_values"]:
                with self.subTest(case=case["id"], frame=selected["frame"], mel=selected["mel"]):
                    self.assertLessEqual(abs(float(features[selected["frame"], selected["mel"]]) - selected["value"]),
                                         golden["max_abs_tolerance"])

    def test_silence_and_padding_have_different_values(self):
        features = extract(synthetic_pcm(1000, "silence"))
        self.assertEqual(features.shape, (1024, 128))
        self.assertEqual(features.dtype, np.float32)
        self.assertTrue(features.flags.c_contiguous)
        np.testing.assert_allclose(features[:98], (np.log(MEL_FLOOR) - MEAN) / (STD * 2), atol=1e-6)
        np.testing.assert_allclose(features[98:], -MEAN / (STD * 2), atol=1e-7)

    def test_dc_removed_per_frame(self):
        values = np.full(16000, 0.25, dtype=np.float32)
        np.testing.assert_array_equal(extract(values), extract(np.zeros_like(values)))

    def test_twenty_seconds_all_three_windows(self):
        self.assertEqual(window_ranges(320000), [(0, 160000), (80000, 240000), (160000, 320000)])
        self.assertEqual(window_ranges(240000), [(0, 160000), (80000, 240000)])
        ranges = window_ranges(240001)
        self.assertEqual(ranges[-1][-1], 240001)
        self.assertTrue(all(b <= a for (_, a), (b, _) in zip(ranges, ranges[1:])))

    def test_mel_bank_uses_no_dc_or_nyquist(self):
        bank = mel_bank()
        self.assertEqual(bank.shape, (257, 128))
        self.assertTrue((bank >= 0).all())
        np.testing.assert_array_equal(bank[0], 0)
        np.testing.assert_allclose(bank[-1], 0, atol=1e-12)
        # The pinned 128-bin/512FFT frontend has one empty low-frequency bin.
        self.assertEqual(np.where(bank.max(axis=0) == 0)[0].tolist(), [3])

    def test_invalid_pcm_rejected(self):
        for values in (np.zeros(15999), np.zeros(160001), np.zeros((16000, 1)),
                       np.zeros(16000, dtype=np.int16), np.full(16000, np.nan)):
            with self.subTest(shape=values.shape), self.assertRaises(ValueError):
                extract(values)


class AstRunnerBoundaryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private_root = Path(__file__).resolve().parents[2] / "work" / "ast-portability"
        cls.private_root.mkdir(parents=True, exist_ok=True)

    def test_outputs_outside_ignored_work_rejected(self):
        from ondevice_av_probe import validate_output_path
        with self.assertRaises(ValueError):
            validate_output_path(Path(__file__).parent / "ast-private-output")

    def test_reused_output_rejected_before_model_load(self):
        from ondevice_ast_portability import main
        with tempfile.TemporaryDirectory(dir=self.private_root) as directory:
            with patch("sys.argv", ["probe", "--model", "missing", "--output", directory]):
                with self.assertRaises(FileExistsError):
                    main()

    def test_timeout_status_is_failure(self):
        import subprocess
        from ondevice_ast_portability import main
        with tempfile.TemporaryDirectory(dir=self.private_root) as directory:
            output = str(Path(directory) / "new")
            with patch("sys.argv", ["probe", "--model", "missing", "--output", output, "--timeout", "1"]), \
                 patch("ondevice_ast_portability.subprocess.run") as run, \
                 patch("ondevice_ast_portability.validate_output_path"):
                run.side_effect = subprocess.TimeoutExpired("worker", 1)
                with self.assertRaises(SystemExit) as failure:
                    main()
                self.assertEqual(failure.exception.code, 1)


if __name__ == "__main__":
    unittest.main()
