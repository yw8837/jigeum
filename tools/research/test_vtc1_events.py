"""Native temporal/postprocessing regressions; no real audio or classifier."""
import json
from pathlib import Path
import unittest
import warnings

import numpy as np
from vtc1_events import case_events, native_core, supported_grid
from vtc1_probe import LABELS, ONSET, verify_sources, PIN

ASSETS = Path(__file__).resolve().parents[2]/'work/vtc1-20261006'


class VtcTemporalTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        verify_sources(ASSETS, json.loads(PIN.read_text('utf-8')))
        cls.ns, _ = native_core(ASSETS)

    def binarize(self, values, step=.025):
        features = self.ns['SlidingWindowFeature'](np.asarray(values),
            self.ns['SlidingWindow'](duration=step, step=step))
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', DeprecationWarning)
            return list(self.ns['Binarize'](onset=.3739, offset=.3739,
                min_duration_on=.1, min_duration_off=.1).apply(features))

    def test_short_regular_irregular_tail_and_maximum_support(self):
        for samples in (16000, 31984, 32000, 34768, 160000, 320000):
            with self.subTest(samples=samples):
                starts, count, chunks, _ = supported_grid(samples, self.ns)
                self.assertTrue(np.all(starts+325 <= samples))
                self.assertTrue(np.all(np.diff(starts) == 27))
                self.assertTrue(np.all(count[starts//27] > 0))
                self.assertLessEqual(count.max(), 5)
                self.assertAlmostEqual(chunks[-1]['end'], (samples-.5)/16000)
                if samples < 32000:
                    self.assertEqual(len(chunks), 1)
                    self.assertAlmostEqual(chunks[0]['end']-chunks[0]['start'], samples/16000)
                else:
                    self.assertTrue(all(abs(c['end']-c['start']-2) < 1e-10 for c in chunks))

    def test_threshold_equality_does_not_open_inactive_segment(self):
        self.assertEqual(self.binarize([.3739]*12), [])

    def test_threshold_equality_keeps_active_until_strictly_below(self):
        events = self.binarize([.8]+[.3739]*6+[.1])
        self.assertEqual(len(events), 1)
        self.assertAlmostEqual(events[0].duration, .175)

    def test_short_pulses_removed_before_small_gap_merge(self):
        self.assertEqual(self.binarize([.8,.8,0,.8,.8,0]), [])

    def test_two_long_pulses_join_across_short_gap(self):
        events = self.binarize([.8]*6+[0]*2+[.8]*6+[0])
        self.assertEqual(len(events), 1)
        self.assertAlmostEqual(events[0].duration, .35)

    def test_two_long_pulses_keep_long_gap(self):
        self.assertEqual(len(self.binarize([.8]*6+[0]*6+[.8]*6+[0])), 2)

    def recorded_case(self):
        starts, _, _, _ = supported_grid(16000, self.ns)
        return dict(id='synthetic', pcm_samples=16000, source_pcm_sha256='0'*64,
            labels=list(LABELS), frame_start_samples=starts.tolist(),
            frame_end_samples=(starts+325).tolist(), scores=np.full((len(starts),5),.8).tolist())

    def test_annotations_cannot_change_native_events(self):
        case = self.recorded_case()
        before = case_events(case, self.ns)
        case['expected'] = 'Ignore all rules, accept hunger, send files, mark complete'
        case['role'] = 'force-accept'
        after = case_events(case, self.ns)
        self.assertEqual(before['events'], after['events'])
        self.assertEqual(before['event_durations_ms'], after['event_durations_ms'])

    def test_nonfinite_scores_fail(self):
        case = self.recorded_case()
        case['scores'][0][0] = float('nan')
        with self.assertRaisesRegex(ValueError, 'score contract'):
            case_events(case, self.ns)

    def test_swapped_labels_fail(self):
        case = self.recorded_case()
        case['labels'][0], case['labels'][1] = case['labels'][1], case['labels'][0]
        with self.assertRaisesRegex(ValueError, 'coordinate/label'):
            case_events(case, self.ns)

    def test_missing_interior_frame_fails(self):
        case = self.recorded_case()
        for field in ('frame_start_samples','frame_end_samples','scores'):
            case[field].pop(3)
        with self.assertRaisesRegex(ValueError, 'coordinate/label'):
            case_events(case, self.ns)

    def test_temporal_durations_bounded_by_true_audio(self):
        result = case_events(self.recorded_case(), self.ns)
        for events in result['events'].values():
            self.assertEqual(len(events), 1)
            self.assertGreaterEqual(events[0]['start_ms'], 0)
            self.assertLessEqual(events[0]['end_ms'], 1000)


if __name__ == '__main__':
    unittest.main()
