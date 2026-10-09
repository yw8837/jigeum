import unittest
import tempfile
import json
import hashlib
from pathlib import Path
from visual_cadence_probe import changes, select_times, prepared_frames


class CadenceProbeTests(unittest.TestCase):
    def test_sparse_decoder_cannot_duplicate_a_frame(self):
        self.assertEqual([0, 2, 4], select_times([0, 100, 900, 900, 1500], 500))

    def test_backwards_pts_and_invalid_step_are_rejected(self):
        for times, step in [([0, 500, 400], 100), ([0], 0), ([-1, 20], 100)]:
            with self.assertRaises(ValueError):
                select_times(times, step)

    def test_half_second_sampling_can_lose_short_mouth_motion(self):
        rows = [{"pts_ms": t, "faces": 1, "mouth": .25 if t % 500 < 200 else .04}
                for t in range(0, 2000, 100)]
        sparse = [rows[i] for i in select_times([r["pts_ms"] for r in rows], 500)]
        self.assertEqual(7, len(changes(rows)))
        self.assertEqual(0, len(changes(sparse)))

    def test_no_motion_across_missing_or_multiple_faces_or_large_gap(self):
        for middle in [{"pts_ms": 100, "faces": 0, "mouth": None},
                       {"pts_ms": 100, "faces": 2, "mouth": .2},
                       {"pts_ms": 100, "faces": 1, "mouth": float("nan")}]:
            self.assertEqual([], changes([{"pts_ms": 0, "faces": 1, "mouth": .04}, middle,
                                          {"pts_ms": 200, "faces": 1, "mouth": .25}]))
        self.assertEqual([], changes([{"pts_ms": 0, "faces": 1, "mouth": .04},
                                      {"pts_ms": 1000, "faces": 1, "mouth": .25}]))

    def test_prepared_frames_reject_changed_bytes_before_model(self):
        import cv2
        import numpy as np
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "frames").mkdir()
            ok, data = cv2.imencode('.jpg', np.zeros((8, 8, 3), dtype=np.uint8))
            self.assertTrue(ok)
            image = root / "frames/000001.jpg"
            image.write_bytes(data.tobytes())
            (root / "media-report.json").write_text(json.dumps({"frames": 1,
                "frame_pixel_hashes": [hashlib.sha256(data).hexdigest()]}))
            (root / "source-pts-map-private.json").write_text(json.dumps([
                {"sample_frame": 0, "source_pts": 0, "ambiguity": 1}]))
            self.assertEqual(1, len(list(prepared_frames(root))))
            image.write_bytes(b'altered')
            with self.assertRaisesRegex(ValueError, 'provenance mismatch'):
                list(prepared_frames(root))


if __name__ == "__main__":
    unittest.main()
