import unittest

from ondevice_audio_candidate import window_ranges


class WindowCoverageTests(unittest.TestCase):
    def test_short_interval_is_not_dropped_or_repeated(self):
        self.assertEqual(window_ranges(16000), [(0, 16000)])
        self.assertEqual(window_ranges(160000), [(0, 160000)])

    def test_twenty_seconds_includes_middle_and_tail(self):
        self.assertEqual(window_ranges(320000),
                         [(0, 160000), (80000, 240000), (160000, 320000)])

    def test_nonintegral_tail_is_covered_without_padding_extra_window(self):
        self.assertEqual(window_ranges(200000), [(0, 160000), (40000, 200000)])

    def test_reject_invalid_length(self):
        for count in [0, 15999, 320001, True, 16000.0]:
            with self.assertRaises(ValueError):
                window_ranges(count)


if __name__ == '__main__':
    unittest.main()
