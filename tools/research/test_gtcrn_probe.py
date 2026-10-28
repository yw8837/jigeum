"""GTCRN DSP/carry contract tests, independent of any perceptual accuracy claim."""
import unittest
import numpy as np
import gtcrn_probe as probe


class Passthrough:
    def __init__(self, invalid=False):
        self.first_caches = []
        self.count = 0
        self.invalid = invalid

    def run(self, names, inputs):
        self.count += 1
        if self.count == 1:
            self.first_caches = [inputs[key].copy() for key in probe.CACHES]
        for key in probe.CACHES:
            if not np.all(inputs[key] == self.count - 1):
                raise AssertionError('Caches were not carried frame-to-frame')
        enhanced = inputs['mix'].copy()
        if self.invalid:
            enhanced.flat[0] = np.nan
        return [enhanced] + [inputs[key] + 1 for key in probe.CACHES]


class DenoiseContractTest(unittest.TestCase):
    def test_passthrough_preserves_timeline_and_both_ends(self):
        for count in (16000, 16001, 159999, 320000):
            samples = np.random.default_rng(7).uniform(-.5, .5, count).astype(np.float32)
            samples[0], samples[-1] = .75, -.75
            before = samples.copy()
            session = Passthrough()
            result = probe.enhance(samples, session)
            self.assertEqual(result.shape, samples.shape)
            # Float32 complex bins amplify rounding at a partial final Hann hop.
            # Bound reconstruction below one PCM16 quantum, including the endpoints.
            np.testing.assert_allclose(result, samples, atol=1 / 32768, rtol=0)
            np.testing.assert_array_equal(samples, before)
            self.assertTrue(all(np.count_nonzero(c) == 0 for c in session.first_caches))

    def test_each_new_clip_starts_with_zero_caches(self):
        for _ in range(2):
            session = Passthrough()
            result = probe.enhance(np.zeros(16000, dtype=np.float32), session)
            self.assertEqual(np.count_nonzero(result), 0)
            self.assertTrue(all(np.count_nonzero(c) == 0 for c in session.first_caches))

    def test_invalid_samples_rejected_before_runtime(self):
        for samples in (np.zeros(15999, dtype=np.float32), np.zeros(320001, dtype=np.float32),
                        np.zeros((1, 16000), dtype=np.float32), np.full(16000, np.nan),
                        np.ones(16000) * 1.1):
            runtime = Passthrough()
            with self.assertRaises(ValueError):
                probe.enhance(samples, runtime)
            self.assertEqual(runtime.count, 0)

    def test_nonfinite_model_output_is_failure_not_silence(self):
        with self.assertRaisesRegex(ValueError, 'finite'):
            probe.enhance(np.zeros(16000, dtype=np.float32), Passthrough(invalid=True))

    def test_analysis_matches_official_torch_periodic_window(self):
        import torch
        samples = np.random.default_rng(3).uniform(-.3, .3, 16001).astype(np.float32)
        expected = torch.stft(torch.from_numpy(samples), 512, 256, 512,
                              torch.hann_window(512).sqrt(), return_complex=True).numpy().T
        actual = probe.spectrogram(samples)
        np.testing.assert_allclose(actual, expected, atol=4e-6, rtol=2e-5)


if __name__ == '__main__':
    unittest.main()
