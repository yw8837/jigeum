import unittest

from ast_device_benchmark import MODEL_SHA
from ast_public_probe import validate_paired_results


def fixture():
    entry = dict(id='synthetic', pcm_sha256='b'*64, pcm_bytes=1280000,
                 pcm_samples=320000, duration_ms=20000)
    case = dict(id='synthetic', run_id='a'*32, status='measured', received_sha256='b'*64,
                received_bytes=1280000, samples=320000, duration_ms=20000,
                base=dict(events=[], decision_issue='INFANT_UNCONFIRMED', has_interpretation=False),
                candidate=dict(events=['BABBLE'], decision_issue=None, has_interpretation=True),
                ast=dict(model_sha256=MODEL_SHA, problem=None, returned_windows=3,
                         windows=[dict(start_ms=a,end_ms=b) for a,b in [(0,10000),(5000,15000),(10000,20000)]]))
    report = dict(run_id='a'*32, status='completed', completed=True, case_count=1, measured_count=1,
                  model_sha256=MODEL_SHA, quality_validated=False, production_babble_policy_enabled=False, cases=[case])
    return report, [entry]


class AstPublicProbeTest(unittest.TestCase):
    def test_complete_same_input_pair_accepted_as_measurement_only(self):
        report, entries = fixture()
        validate_paired_results(report, entries, 'a'*32)

    def test_stale_run_incomplete_counts_and_model_substitution_rejected(self):
        for key, value in [('run_id','c'*32),('case_count',0),('measured_count',0),
                           ('model_sha256','0'*64),('quality_validated',True),('completed',False)]:
            report, entries=fixture(); report[key]=value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_paired_results(report,entries,'a'*32)

    def test_missing_pair_input_mismatch_and_failed_case_rejected(self):
        for key,value in [('received_sha256','0'*64),('received_bytes',640000),('samples',160000),
                          ('duration_ms',10000),('status','error'),('run_id','c'*32)]:
            report,entries=fixture();report['cases'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):validate_paired_results(report,entries,'a'*32)
        report,entries=fixture();report['cases']=[]
        with self.assertRaises(ValueError):validate_paired_results(report,entries,'a'*32)

    def test_model_failure_cannot_be_a_successful_abstention(self):
        for stage in ['base','candidate']:
            report,entries=fixture();report['cases'][0][stage]['decision_issue']='MODEL_FAILURE'
            with self.assertRaises(ValueError):validate_paired_results(report,entries,'a'*32)
        report,entries=fixture();report['cases'][0]['ast']['problem']='runtime error'
        with self.assertRaises(ValueError):validate_paired_results(report,entries,'a'*32)

    def test_ast_missing_tail_window_rejected(self):
        report,entries=fixture();report['cases'][0]['ast']['windows'].pop()
        with self.assertRaises(ValueError):validate_paired_results(report,entries,'a'*32)


if __name__ == '__main__':unittest.main()
