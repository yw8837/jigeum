"""Synthetic recorded decisions only; no media/model/framework imports."""
import unittest
from pathlib import Path
from unittest.mock import patch

import ast_candidate_delta as audit


class DeltaTest(unittest.TestCase):
    def test_added_acceptance_is_not_a_preserved_baseline_or_independent_win(self):
        value = audit.classify_change({'state': 'abstained', 'events': []},
            {'state': 'accepted', 'events': ['BABBLE']}, 'user_asserted_development', ['BABBLE'])
        self.assertEqual('added_acceptance', value['transition'])
        self.assertEqual('matches_user_development_label', value['label_assessment'])
        self.assertFalse(value['independent_positive'])

    def test_preserved_existing_mismatch_is_not_a_new_candidate_error(self):
        value = audit.classify_change({'state': 'accepted', 'events': ['CRY']},
            {'state': 'accepted', 'events': ['CRY']}, 'independent_exact', ['BABBLE'])
        self.assertEqual('preserved_acceptance', value['transition'])
        self.assertEqual('preserved_baseline_label_mismatch', value['label_assessment'])

    def test_weak_title_cannot_certify_a_new_false_acceptance(self):
        value = audit.classify_change({'state': 'abstained', 'events': []},
            {'state': 'accepted', 'events': ['BABBLE']}, 'weak_source_title', ['LAUGH'])
        self.assertEqual('weak_label_disagreement', value['label_assessment'])
        self.assertFalse(value['confirmed_new_false_acceptance'])

    def test_baseline_missing_remains_unknown_even_with_ast_scores(self):
        value = audit.classify_change(None, None, 'weak_source_title', ['BABBLE'])
        self.assertEqual('unknown', value['transition'])
        self.assertFalse(value['independent_positive'])

    def test_lost_existing_acceptance_is_a_preservation_violation(self):
        value = audit.classify_change({'state': 'accepted', 'events': ['LAUGH']},
            {'state': 'abstained', 'events': []}, 'none', None)
        self.assertEqual('lost_acceptance', value['transition'])
        self.assertTrue(value['preservation_violation'])

    def test_retained_abstention_is_not_proven_babble_miss_without_exact_label(self):
        value = audit.classify_change({'state': 'abstained', 'events': []},
            {'state': 'abstained', 'events': []}, 'user_reported_vocalization', None)
        self.assertEqual('retained_abstention', value['transition'])
        self.assertEqual('exact_label_unknown', value['label_assessment'])

    def test_two_overlapping_segments_count_as_one_source_per_mode(self):
        common = dict(source_sha256='a'*64, group='one-source', role='new_source', mode='raw_timeline_replay',
            policy_provenance='v2_source_verified', label_evidence='none', transition='retained_abstention',
            independent_positive=False, confirmed_new_false_acceptance=False, preservation_violation=False)
        rows = [dict(common, id='first', start_ms=0, end_ms=10000),
                dict(common, id='last', start_ms=7000, end_ms=17000),
                dict(common, id='first', start_ms=0, end_ms=10000, mode='filtered_aggregate_fallback')]
        summary = audit.summarize(rows)
        self.assertEqual(1, summary['unique_sources'])
        self.assertEqual(2, summary['unique_source_segments'])
        self.assertEqual(1, summary['overlapping_segment_pairs'])

    def test_policy_hash_mismatch_and_model_mismatch_are_rejected(self):
        with self.assertRaises(ValueError):
            audit.verify_source_hashes({'Policy.kt': 'a'*64}, {'Policy.kt': 'b'*64})
        with self.assertRaises(ValueError):
            audit.verify_model_hash('0'*64)

    def test_completed_no_audio_is_retained_but_short_successful_decode_rejected(self):
        case = dict(operational_status='completed', decoded_samples=0,
                    decoded_matches_selected_length=False)
        blocked = dict(state='blocked', events=[], issue='NO_AUDIO')
        audit.validate_android_completion(case, blocked, blocked)
        self.assertEqual('retained_blocked_input',
            audit.classify_change(blocked, blocked, 'none', None)['transition'])
        accepted = dict(state='accepted', events=['BABBLE'], issue=None)
        with self.assertRaises(ValueError):
            audit.validate_android_completion(dict(case, decoded_samples=159000), accepted, accepted)

    def test_retained_audit_requires_every_source_and_both_replay_modes(self):
        metadata = [dict(id='source', duplicate_owner=None, source_sha256='a'*64,
                         start_ms=0, end_ms=10000)]
        recorded = dict(id='source', mode='raw_timeline', before_issue='INFANT_UNCONFIRMED',
                        after_issue='INFANT_UNCONFIRMED', before_events='', after_events='', supported_windows=0)
        for entries in ([], [recorded]):
            report = dict(model_sha256=audit.MODEL_SHA256, source_sha256={}, before=entries, after=entries)
            with self.subTest(entries=len(entries)), patch.object(audit, 'read_json', side_effect=[report, metadata]):
                with self.assertRaises(ValueError): audit.retained_rows(Path('unused'), {})


if __name__ == '__main__':
    unittest.main()
