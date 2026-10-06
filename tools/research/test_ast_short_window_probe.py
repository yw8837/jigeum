"""Prevent invalid screening evidence, hidden conflicts and score-guided cropping."""
import unittest
import ast_short_window_probe as probe


def window(start, end, **scores):
    labels = probe.CONTRARY + ('Television', 'Babbling', 'Child speech, kid speaking')
    return dict(start_ms=start, end_ms=end, scores={**dict.fromkeys(labels, 0.0), **scores})


class FixedWindowEvidenceTest(unittest.TestCase):
    def test_fixed_crops_are_disjoint_and_ignore_short_four_second_tail(self):
        self.assertEqual(probe.short_ranges(160000, 64000), [(0, 64000), (64000, 128000)])
        self.assertEqual(probe.short_ranges(160000, 32000), [(n, n + 32000) for n in range(0, 160000, 32000)])

    def test_invalid_lengths_and_counts_fail_before_measurement(self):
        for size, length in [(0, 32000), (320001, 32000), (16000, 48000), (True, 32000)]:
            with self.subTest(size=size, length=length), self.assertRaises(ValueError):
                probe.short_ranges(size, length)

    def test_conflict_in_full_selection_cannot_be_hidden_by_positive_crop(self):
        short = [window(0, 2000, Babbling=.8)]
        baseline = [window(0, 10000, Television=.3)]
        result = probe.candidate_summary(short, baseline)
        self.assertTrue(result['whole_selection_blocked'])
        self.assertFalse(result['positive_not_product_acceptance'])

    def test_added_short_window_can_only_tighten_complete_selection_guard(self):
        baseline = [window(0, 10000)]
        short = [window(0, 2000, Babbling=.8), window(2000, 4000, **{'Baby laughter': .3})]
        self.assertFalse(probe.candidate_summary(short, baseline)['positive_not_product_acceptance'])

    def test_conflict_in_other_context_length_blocks_positive_variant(self):
        variants = [dict(seconds=2, windows=[window(0, 2000, Babbling=.8)]),
                    dict(seconds=4, windows=[window(0, 4000, Television=.3)])]
        result = probe.summarize_variants(variants, [window(0, 10000)])
        self.assertTrue(all(v['summary']['whole_selection_blocked'] for v in result))
        self.assertFalse(any(v['summary']['positive_not_product_acceptance'] for v in result))

    def test_adult_speech_or_child_speech_alone_is_not_positive_babble(self):
        self.assertFalse(probe.candidate_summary([window(0, 2000, **{'Child speech, kid speaking': .99})], [])['positive_not_product_acceptance'])

    def test_local_subclass_conflict_is_preserved_below_global_guard(self):
        # .2 laughter >= .1 and >= .6*.3 even though < .3 global block.
        result = probe.candidate_summary([window(0, 2000, Babbling=.3, Giggle=.2)], [])
        self.assertFalse(result['whole_selection_blocked'])
        self.assertFalse(result['positive_not_product_acceptance'])


if __name__ == '__main__':
    unittest.main()
