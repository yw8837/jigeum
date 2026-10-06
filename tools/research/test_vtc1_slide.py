"""Synthetic audit of original Model.slide averaging and short-input forward."""
import json
from pathlib import Path
from types import MethodType
import unittest

import numpy as np
import torch
from vtc1_probe import native_coverage, native_model, PIN, verify_assets


class NativeSlideTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assets = Path(__file__).resolve().parents[2]/'work/vtc1-20261006'
        pin = json.loads(PIN.read_text('utf-8'))
        verify_assets(assets, pin)
        torch.set_num_threads(4)
        cls.model, cls.ns, _ = native_model(assets, pin)

    def test_actual_short_synthetic_forward_uses_581_frames(self):
        with torch.inference_mode():
            scores = self.model(torch.zeros(1,16000,1))
        self.assertEqual(tuple(scores.shape), (1,581,5))
        self.assertTrue(bool(torch.isfinite(scores).all()))

    def test_native_slide_averages_every_overlapping_chunk_equally(self):
        model = self.model
        original = model.forward
        def fake_forward(unused_self, inputs, return_intermediate=None):
            # A varying, deterministic value per synthetic chunk exposes sums,
            # lost coverage, duplicate tail handling, and wrong averaging.
            frames = (inputs.shape[1]-325)//27+1
            value = float(inputs[0,0,0])
            return torch.full((1,frames,5), value, dtype=torch.float32)
        try:
            model.forward = MethodType(fake_forward, model)
            for samples in (16000,32000,34768,160000,320000):
                with self.subTest(samples=samples):
                    pcm = np.arange(samples, dtype=np.float32)/samples
                    features = self.ns['SlidingWindowFeature'](pcm[:,None], self.ns['SlidingWindow'](
                        start=-.5/16000, duration=1/16000, step=1/16000))
                    chunks = self.ns['SlidingWindow'](duration=2., step=.5)
                    count, rows = native_coverage(features, model.resolution, chunks, model.alignment)
                    expected = np.zeros(len(count), dtype=np.float32)
                    fixed = min(features.extent.duration,2.)
                    for row in rows:
                        chunk = self.ns['Segment'](row['start'], row['end'])
                        values = features.crop(chunk, mode='center', fixed=fixed)
                        indices = model.resolution.crop(chunk, mode=model.alignment, fixed=fixed)
                        self.assertEqual(len(indices), (len(values)-325)//27+1)
                        expected[indices] += values[0,0]
                    expected /= np.maximum(count,1)
                    with torch.inference_mode():
                        actual = model.slide(features, chunks, batch_size=1, device='cpu').data
                    self.assertEqual(actual.shape, (len(count),5))
                    np.testing.assert_allclose(actual, np.broadcast_to(expected[:,None],actual.shape), rtol=0,atol=0)
                    self.assertTrue(np.all(actual[count==0]==0))
        finally:
            model.forward = original


if __name__ == '__main__':
    unittest.main()
