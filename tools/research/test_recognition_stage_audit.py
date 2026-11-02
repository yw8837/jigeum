"""Audit contracts, not tests of acoustic accuracy."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import recognition_stage_audit as audit
from test_presence_context_replay import report


def fixture():
    data = report()
    case = data['cases'][0]
    case.update(received_sha256='b' * 64, base=copy.deepcopy(case['phase']))
    for key in ('base', 'prior_phase', 'phase'):
        case[key].update(analysis_issue=None, decision_issue='INFANT_UNCONFIRMED', sound=None)
    return data


class RecognitionStageAuditTest(unittest.TestCase):
    def run_audit(self, data):
        return audit.summarize(data)

    def test_completed_pcm_does_not_claim_video_decode_visual_or_accuracy(self):
        result = self.run_audit(fixture())
        self.assertEqual({'measured_pcm': 1}, result['input_scope'])
        self.assertFalse(result['container_decode_measured'])
        self.assertFalse(result['visual_measured'])
        self.assertFalse(result['accuracy_measured'])
        self.assertFalse(result['production_ast_execution_measured'])
        self.assertEqual({'held_infant_unconfirmed': 1}, result['final_outcomes'])

    def test_partial_output_is_not_counted_as_mixed_failure(self):
        data = fixture()
        phase = data['cases'][0]['phase']
        phase.update(has_interpretation=True, issue=None, decision_issue='MIXED_VOCAL',
                     sound='아기 발성', phrase='a private phrase', partial=False)
        result = self.run_audit(data)
        self.assertEqual({'output_after_whole_decision_hold': 1}, result['final_outcomes'])
        self.assertEqual({'MIXED_VOCAL': 1}, result['whole_decision_issues'])
        self.assertEqual(1, result['transitions']['held_to_output'])
        self.assertNotIn('a private phrase', json.dumps(result))

    def test_approved_event_can_be_held_by_later_audibility_gate(self):
        data = fixture()
        data['cases'][0]['phase'].update(issue='QUIET', decision_issue=None)
        result = self.run_audit(data)
        self.assertEqual({'held_final_audibility': 1}, result['final_outcomes'])

    def test_analysis_silence_is_not_a_detector_miss(self):
        data = fixture()
        data['cases'][0]['phase'].update(issue='SILENCE', decision_issue='SILENCE', analysis_issue='SILENCE')
        self.assertEqual({'held_analysis_silence': 1}, self.run_audit(data)['final_outcomes'])

    def test_unknown_or_contradictory_reasons_fail_closed(self):
        for change in (dict(issue='UNKNOWN'), dict(issue='QUIET', analysis_issue='MODEL_FAILURE'),
                       dict(issue='SILENCE', decision_issue='INFANT_UNCONFIRMED'),
                       dict(has_interpretation=True, phrase='x', sound='x', issue=None,
                            analysis_issue='MODEL_FAILURE'), dict(decision_issue='new_gate')):
            data = fixture()
            data['cases'][0]['phase'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.run_audit(data)

    def test_missing_stage_or_sound_fields_are_unknown_not_success(self):
        for field in ('analysis_issue', 'decision_issue', 'sound'):
            data = fixture()
            del data['cases'][0]['phase'][field]
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.run_audit(data)

    def test_overlap_is_union_coverage_not_thirty_seconds_for_twenty(self):
        data = fixture()
        other = report(20000)['cases'][0]
        data['cases'][0].update({key: other[key] for key in
                               ('samples', 'duration_ms', 'received_bytes', 'ast')})
        result = self.run_audit(data)
        self.assertEqual(20000, result['recorded_ast_union_ms'])
        self.assertEqual(30000, result['recorded_ast_window_ms'])

    def test_duplicate_pcm_is_not_an_independent_input_or_phrase_collision(self):
        data = fixture()
        for p in ('base', 'prior_phase', 'phase'):
            data['cases'][0][p].update(has_interpretation=True, issue=None,
                decision_issue=None, sound='same kind', phrase='private duplicate text')
        data['cases'].append(copy.deepcopy(data['cases'][0]))
        data['cases'][1]['id'] = 'second_condition'
        data['case_count'] = data['measured_count'] = 2
        result = self.run_audit(data)
        self.assertEqual(2, result['conditions'])
        self.assertEqual(1, result['unique_pcm_hashes'])
        self.assertEqual(0, result['phrase_groups_across_distinct_pcm'])
        data['cases'][1]['received_sha256'] = 'c' * 64
        result = self.run_audit(data)
        self.assertEqual(1, result['phrase_groups_across_distinct_pcm'])
        self.assertFalse(result['phrase_collision_proves_wrong_result'])
        self.assertNotIn('private duplicate text', json.dumps(result))

    def test_failed_stale_partial_reports_and_missing_pcm_identity_are_rejected(self):
        for target, field, value in (('report', 'completed', False), ('report', 'measured_count', 0),
                                    ('case', 'run_id', 'c'*32), ('case', 'received_sha256', None)):
            data = fixture()
            (data if target == 'report' else data['cases'][0])[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.run_audit(data)

    def test_input_and_output_do_not_contain_each_others_private_content(self):
        data = fixture()
        data['cases'][0]['id'] = 'private_case'
        before = copy.deepcopy(data)
        result = self.run_audit(data)
        self.assertEqual(before, data)
        for forbidden in ('private_case', 'received_sha256', 'run_id', 'frames', 'cues'):
            self.assertNotIn(forbidden, json.dumps(result))

    def test_cli_requires_hash_and_never_overwrites_existing_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source, output = root/'input.json', root/'audit.json'
            source.write_text(json.dumps(fixture()), encoding='utf-8')
            sha = hashlib.sha256(source.read_bytes()).hexdigest()
            args = ['--input', str(source), '--sha256', sha, '--label', 'synthetic contract',
                    '--output', str(output)]
            self.assertEqual(0, audit.main(args))
            before = output.read_bytes()
            with self.assertRaises(FileExistsError):
                audit.main(args)
            self.assertEqual(before, output.read_bytes())
            with self.assertRaises(ValueError):
                audit.main(['--input', str(source), '--sha256', '0'*64, '--label', 'wrong hash',
                            '--output', str(root/'wrong.json')])
            self.assertFalse((root/'wrong.json').exists())


if __name__ == '__main__':
    unittest.main()
