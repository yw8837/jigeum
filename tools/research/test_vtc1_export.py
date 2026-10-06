"""Synthetic metadata gate regressions; no models or media."""
import copy
import unittest
from vtc1_export import float_parity_passed


class FloatGateTest(unittest.TestCase):
    def valid(self):
        return dict(status='passed',cases=[dict(id=i,pcm_samples=n,shape=[1,f,5],finite=True,
            passed=True,max_absolute_error=1e-6) for i,n,f in
            [('silence_1s',16000,581),('tone_2s',32000,1174),('noise_1999ms',31984,1173)]])

    def test_all_complete_fixed_outputs_required(self):
        report = self.valid()
        self.assertTrue(float_parity_passed(report))
        report['cases'].pop()
        self.assertFalse(float_parity_passed(report))

    def test_success_label_cannot_hide_failed_precision(self):
        report = self.valid()
        report['cases'][0]['max_absolute_error']=.05575
        self.assertFalse(float_parity_passed(report))

    def test_nonfinite_negative_and_wrong_shape_rejected(self):
        for error in (float('nan'),float('inf'),-1):
            report = self.valid()
            report['cases'][0]['max_absolute_error']=error
            self.assertFalse(float_parity_passed(report))
        report=self.valid()
        report['cases'][0]['shape']=[1,1174,5]
        self.assertFalse(float_parity_passed(report))

    def test_duplicate_cases_cannot_replace_missing_case(self):
        report=self.valid()
        report['cases'][2]=copy.deepcopy(report['cases'][1])
        self.assertFalse(float_parity_passed(report))


if __name__=='__main__':
    unittest.main()
