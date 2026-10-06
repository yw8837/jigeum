"""Boundary and invariant tests for the quiet-window research adapter."""
import unittest
import numpy as np
from vtc1_guarded_probe import cleanup, strongest_second_rms, validate_pcm, make_guarded_forward


class GuardedInputTest(unittest.TestCase):
    def test_invalid_pcm_rejected_before_inference(self):
        cases=[np.zeros(15999,np.float32),np.zeros(320001,np.float32),
            np.zeros(16000,np.float64),np.zeros((16000,1),np.float32)]
        for value in (float('nan'),float('inf'),1.01):
            pcm=np.zeros(16000,np.float32)
            pcm[-1]=value
            cases.append(pcm)
        for pcm in cases:
            with self.subTest(shape=pcm.shape,dtype=pcm.dtype):
                with self.assertRaises(ValueError):
                    cleanup(pcm)

    def test_dc_is_removed_without_modifying_input_or_length(self):
        pcm=np.full(16000,.15,np.float32)
        old=pcm.tobytes()
        cleaned=cleanup(pcm)
        self.assertEqual(pcm.tobytes(),old)
        self.assertEqual(cleaned.shape,pcm.shape)
        self.assertEqual(cleaned.dtype,np.float32)
        self.assertTrue(np.all(cleaned==0))

    def test_short_burst_not_diluted_by_remaining_silence(self):
        pcm=np.zeros(320000,np.float32)
        pcm[152000:168000]=.02
        self.assertAlmostEqual(strongest_second_rms(pcm),.02,places=8)
        self.assertLess(float(np.sqrt(np.mean(pcm.astype(np.float64)**2))),.005)

    def test_rms_matches_all_shifted_windows(self):
        pcm=np.random.default_rng(5).uniform(-.1,.1,16200).astype(np.float32)
        reference=max(float(np.sqrt(np.mean(pcm[i:i+16000].astype(np.float64)**2))) for i in range(201))
        self.assertAlmostEqual(strongest_second_rms(pcm),reference,places=12)

    def test_quiet_path_never_calls_classifier(self):
        import torch
        def forbidden(*unused):
            raise AssertionError('Quiet audio must not reach classifier')
        counters=dict(classified=0,skipped=0)
        forward=make_guarded_forward(forbidden,None,counters,'torch')
        output=forward(None,torch.zeros((1,16000,1),dtype=torch.float32))
        self.assertEqual(tuple(output.shape),(1,581,5))
        self.assertTrue(bool((output==0).all()))
        self.assertEqual(counters,dict(classified=0,skipped=1))

    def test_nonquiet_invalid_classifier_output_rejected(self):
        import torch
        counters=dict(classified=0,skipped=0)
        forward=make_guarded_forward(lambda unused:torch.full((1,581,5),float('nan')),None,counters,'torch')
        with self.assertRaises(ValueError):
            forward(None,torch.full((1,16000,1),.02,dtype=torch.float32))
        self.assertEqual(counters,dict(classified=1,skipped=0))


if __name__=='__main__':
    unittest.main()
