"""Frozen public evaluation boundaries; no network or private media in these tests."""
import copy
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

import numpy as np

import public_infant_followup as probe
from extra_babble_evaluation import acquire, SOURCES_SHA
from infant_head_screen import NoRedirects


class PublicFollowupGuards(unittest.TestCase):
    def test_fixed_sources_keep_caption_only_and_training_blocked(self):
        rows = probe.sources()
        self.assertEqual([r['id'] for r in rows], ['388407', '135185', '475589'])
        self.assertTrue(all(r['training_blocked'] and r['approved_training_label'] is None and
                            r['role'] == 'evaluation_only' and not r['age_verified'] for r in rows))
        rows[0]['training_blocked'] = False
        self.assertTrue(probe.sources()[0]['training_blocked'])

    def test_source_mutation_rights_ssrf_and_training_approval_are_rejected(self):
        for key, value in [('preview_url', 'https://127.0.0.1/private'), ('id', '../private'),
                           ('current_license_url', 'https://creativecommons.org/licenses/by-nc/4.0/'),
                           ('training_blocked', False), ('approved_training_label', 'babble')]:
            rows = copy.deepcopy(probe.SOURCE_ROWS)
            rows[0][key] = value
            with patch.object(probe, 'SOURCE_ROWS', rows), self.subTest(key=key), self.assertRaises(ValueError):
                probe.sources()

    def test_preselected_ranges_use_actual_duration_without_score_input(self):
        self.assertEqual(probe.selected_ranges(16000, three_locations=True), [(0, 16000)])
        self.assertEqual(probe.selected_ranges(320000, three_locations=True), [(0, 320000)])
        self.assertEqual(probe.selected_ranges(4354576, three_locations=True),
                         [(0, 320000), (2017288, 2337288), (4034576, 4354576)])
        self.assertEqual(probe.selected_ranges(351808), [(0, 320000)])
        for count in (True, 15999, 4800001, 1.5, '16000'):
            with self.subTest(count=count), self.assertRaises(ValueError):
                probe.selected_ranges(count)

    def test_original_multilabel_scores_reject_nonfinite_wrong_tensor(self):
        names = probe.labels()
        logits = np.zeros((1, 527), dtype=np.float32)
        logits[0, names.index('Babbling')] = 1000
        logits[0, names.index('Baby cry, infant cry')] = -1000
        result = probe.probabilities(logits, names)
        self.assertEqual(result['Babbling'], 1)
        self.assertEqual(result['Baby cry, infant cry'], 0)
        self.assertEqual(result['Speech'], .5)
        for value in (np.zeros((527,), np.float32), logits.astype(np.float64),
                      np.full((1, 527), np.nan, np.float32), np.full((1, 527), np.inf, np.float32)):
            with self.assertRaises(ValueError):
                probe.probabilities(value, names)

    def test_head_and_original_scores_checked_then_tensors_wiped(self):
        outputs = [np.zeros((1, 527), np.float32), np.ones((1, 768), np.float32),
                   np.array([[.9]], np.float32)]
        session = MagicMock()
        session.run.return_value = outputs
        reference = dict(infant_score=float(outputs[2][0, 0]), infant_support=True)
        with patch.object(probe, 'extract', return_value=np.ones((2, 2), np.float32)), \
                patch.object(probe, 'score', return_value=reference):
            result = probe.observe_window(session, np.ones(16000, np.float32), probe.labels(), None, None)
        self.assertTrue(result['infant_support'])
        self.assertEqual(result['head_max_absolute_error'], 0)
        self.assertTrue(all(not v.any() for v in outputs))
        quiet = probe.observe_window(session, np.zeros(16000, np.float32), probe.labels(), None, None)
        self.assertFalse(quiet['infant_support'])
        self.assertIsNone(quiet['infant_score'])
        self.assertEqual(session.run.call_count, 1)

    def test_parity_failure_is_not_support_and_still_wipes_tensors(self):
        outputs = [np.zeros((1, 527), np.float32), np.ones((1, 768), np.float32),
                   np.array([[.9]], np.float32)]
        session = MagicMock()
        session.run.return_value = outputs
        with patch.object(probe, 'extract', return_value=np.ones((2, 2), np.float32)), \
                patch.object(probe, 'score', return_value=dict(infant_score=.1, infant_support=False)), \
                self.assertRaises(ValueError):
            probe.observe_window(session, np.ones(16000, np.float32), probe.labels(), None, None)
        self.assertTrue(all(not v.any() for v in outputs))

    def test_acquisition_cap_checked_before_network_or_reuse(self):
        for cap in (True, 0, -1, 3 * 1024**2 + 1, 1.5):
            with patch('extra_babble_evaluation.verified_reuse') as reuse, \
                    patch('extra_babble_evaluation.urllib.request.build_opener') as network, \
                    self.subTest(cap=cap), self.assertRaises(ValueError):
                acquire(SimpleNamespace(), [], max_bytes=cap)
            reuse.assert_not_called()
            network.assert_not_called()

    def test_default_and_followup_caps_and_source_hashes_stay_separate(self):
        class MemoryPath:
            def __init__(self):
                self.data = bytearray()
            def __enter__(self):
                return self
            def __exit__(self, *unused):
                pass
            def __truediv__(self, unused):
                return self
            def open(self, mode):
                assert mode == 'xb'
                return self
            def write(self, data):
                self.data.extend(data)
        for kwargs, expected_cap, expected_sha in (({}, 2*1024**2, SOURCES_SHA),
                (dict(max_bytes=3*1024**2, source_sha=probe.SOURCE_SHA), 3*1024**2, probe.SOURCE_SHA)):
            out = MemoryPath()
            args = SimpleNamespace(output=out, reuse=Path('earlier'))
            with patch('extra_babble_evaluation.verified_reuse', return_value=b'public-only') as reuse, \
                    patch('extra_babble_evaluation.digest', return_value='0'*64), \
                    patch('extra_babble_evaluation.write') as write:
                records = acquire(args, probe.sources()[:1], **kwargs)
            self.assertEqual(records[0]['bytes'], 11)
            reuse.assert_called_once_with(args.reuse, probe.sources()[0], max_bytes=expected_cap)
            self.assertTrue(all(call.args[1]['source_sha256'] == expected_sha for call in write.call_args_list))
        with self.assertRaises(ValueError):
            NoRedirects().redirect_request(None, None, None, None, None, 'https://127.0.0.1/private')

    def test_final_drift_in_any_pin_prevents_completion(self):
        args = SimpleNamespace(model=Path('model'), head=Path('head'), ffmpeg=Path('ffmpeg'))
        values = {args.model: probe.CANDIDATE_SHA, args.head/'head-ast-presence-public.npz': probe.HEAD_SHA,
                  args.head/'fit.json': 'fit', probe.LABELS: 'labels', args.ffmpeg: 'ffmpeg'}
        before = dict(model=probe.CANDIDATE_SHA, head=probe.HEAD_SHA, fit='fit', labels='labels', sources=probe.SOURCE_SHA,
                      ffmpeg='ffmpeg')
        with patch.object(probe, 'digest', side_effect=values.__getitem__):
            self.assertEqual(probe.verify_pins(args, before), before)
        for path in values:
            changed = dict(values)
            changed[path] = 'changed'
            with patch.object(probe, 'digest', side_effect=changed.__getitem__), self.assertRaises(ValueError):
                probe.verify_pins(args, before)

    def test_ffmpeg_capability_failure_is_rejected_before_acquisition(self):
        path = MagicMock()
        path.is_file.return_value = True
        path.is_symlink.return_value = False
        for code, value in ((0, b'  E  mp4 movie\n'), (1, b'  E  s16le pcm\n'),
                            (0, b'x'*262145)):
            with patch.object(probe.subprocess, 'run', return_value=SimpleNamespace(
                    returncode=code, stdout=value, stderr=b'')), self.assertRaises(ValueError):
                probe.validate_ffmpeg(path)
        with patch.object(probe.subprocess, 'run', return_value=SimpleNamespace(
                returncode=0, stdout=b'  E  s16le PCM signed 16-bit little-endian\n', stderr=b'')) as run:
            probe.validate_ffmpeg(path)
            self.assertEqual(run.call_args.kwargs['timeout'], 10)

    def test_partial_false_flags_missing_code_pins_and_window_drop_are_rejected(self):
        done = dict(status='completed', requested_cases=3, decoded_cases=3, runtime=probe.RUNTIME,
                    pins={}, source_code_sha256={name: 'sha' for name in probe.CODE_FILES},
                    training=False, calibration=False, private_media_read=False, strong_ground_truth=False,
                    quality_validated=False, product_changed=False, android_validated=False, cases=[])
        for source in probe.sources():
            done['cases'].append(dict(source, decoded_total_samples=16000, selected_ranges=[[0, 16000]],
                windows=[dict(selection_index=0, start_sample=0, end_sample=16000)],
                event_ground_truth=False, android_pipeline_executed=False))
        with patch.object(probe, 'verify_pins', return_value={}), patch.object(probe, 'digest', return_value='sha'):
            probe.validate_completion(done, None, None)
            edits = [('status', 'partial'), ('decoded_cases', 2), ('training', True),
                     ('quality_validated', None), ('source_code_sha256', {}), ('cases', done['cases'][:-1])]
            for key, value in edits:
                changed = copy.deepcopy(done)
                changed[key] = value
                with self.subTest(key=key), self.assertRaises(ValueError):
                    probe.validate_completion(changed, None, None)
            for key, value in [('windows', []), ('selected_ranges', [[0, 16001]]), ('event_ground_truth', True)]:
                changed = copy.deepcopy(done)
                changed['cases'][0][key] = value
                with self.subTest(key=key), self.assertRaises(ValueError):
                    probe.validate_completion(changed, None, None)


if __name__ == '__main__':
    unittest.main()
