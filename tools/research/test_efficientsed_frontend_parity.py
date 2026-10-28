"""Fail-closed result gate tests. No model, compiler, or network required."""
import copy
import unittest

from efficientsed_frontend_parity import CASES, MEL_ATOL, completed


def good_result():
    return dict(contracts_passed=True, tables_exact=True, cases=[
        dict(case=name, mel_shape=[1, 1, 128, 1000], mel_finite=True, mel_passed=True,
             mel_max_absolute=1e-5, mel_mean_absolute=1e-6, outputs=[
                 dict(comparison=kind, output=index,
                      shape=[1, 447, 250] if index == 0 else [1, 447], finite=True, passed=True,
                      max_absolute=1e-5, mean_absolute=1e-6, max_scaled_error=.1)
                 for kind in ('frontend_delta_ort', 'end_to_end_torch') for index in (0, 1)])
        for name in CASES])


class GateTests(unittest.TestCase):
    def test_complete(self):
        self.assertTrue(completed(good_result()))

    def test_failure_flags(self):
        for field in ('contracts_passed', 'tables_exact'):
            value = good_result()
            value[field] = False
            self.assertFalse(completed(value))

    def test_incomplete_or_duplicate_case(self):
        for mode in ('missing', 'duplicate'):
            value = good_result()
            if mode == 'missing':
                value['cases'].pop()
            else:
                value['cases'][-1] = copy.deepcopy(value['cases'][0])
            self.assertFalse(completed(value))

    def test_mel_failure_with_forged_flag(self):
        for field, bad in [('mel_max_absolute', MEL_ATOL * 2), ('mel_max_absolute', float('nan')),
                           ('mel_mean_absolute', float('inf')), ('mel_max_absolute', -1),
                           ('mel_finite', False), ('mel_passed', False), ('mel_shape', [1, 1000, 128])]:
            value = good_result()
            value['cases'][0][field] = bad
            self.assertFalse(completed(value))

    def test_output_failure_with_forged_flag(self):
        for field, bad in [('max_scaled_error', 1.00001), ('max_scaled_error', float('nan')),
                           ('max_absolute', float('inf')), ('mean_absolute', -1),
                           ('shape', [1, 447, 249]), ('finite', False), ('passed', False)]:
            value = good_result()
            value['cases'][0]['outputs'][0][field] = bad
            self.assertFalse(completed(value))

    def test_missing_and_duplicate_output(self):
        for mode in ('missing', 'duplicate'):
            value = good_result()
            outputs = value['cases'][0]['outputs']
            if mode == 'missing':
                outputs.pop()
            else:
                outputs[-1] = copy.deepcopy(outputs[0])
            self.assertFalse(completed(value))


if __name__ == '__main__':
    unittest.main()
