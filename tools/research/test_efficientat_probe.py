"""Corpus integrity and complete-time-axis tests for local comparison, not model accuracy."""
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest

import efficientat_probe as probe


class CorpusContractTest(unittest.TestCase):
    def fixture(self, root):
        data = struct.pack('<f', 0.125) * 16000
        entry = dict(id='signal', pcm_samples=16000, pcm_bytes=len(data),
                     pcm_sha256=hashlib.sha256(data).hexdigest(), duration_ms=1000,
                     sample_rate_hz=16000, channels=1, encoding='float32le')
        (root / 'signal.f32').write_bytes(data)
        (root / 'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
        return entry

    def test_same_pcm_is_required_before_inference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            self.assertEqual(probe.load_corpus(root)[0]['id'], 'signal')
            (root / 'signal.f32').write_bytes(struct.pack('<f', 0.25) * 16000)
            with self.assertRaisesRegex(ValueError, 'hash'):
                probe.load_corpus(root)

    def test_corrupt_contract_never_becomes_a_normal_model_rejection(self):
        for change in ({'id': '../signal'}, {'pcm_samples': 16001}, {'channels': 2},
                       {'sample_rate_hz': 32000}, {'duration_ms': 20000}, {'id': ''}):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                entry = self.fixture(root)
                (root / 'manifest.json').write_text(json.dumps([{**entry, **change}]), encoding='utf-8')
                with self.subTest(change=change), self.assertRaises(ValueError):
                    probe.load_corpus(root)

    def test_nonfinite_audio_is_rejected_even_with_matching_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            entry = self.fixture(root)
            data = struct.pack('<f', float('nan')) * 16000
            (root / 'signal.f32').write_bytes(data)
            entry['pcm_sha256'] = hashlib.sha256(data).hexdigest()
            (root / 'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'finite'):
                probe.load_corpus(root)

    def test_twenty_seconds_and_partial_tail_keep_full_coverage(self):
        self.assertEqual(probe.window_ranges(320000), [(0,160000), (80000,240000), (160000,320000)])
        ranges = probe.window_ranges(176000)
        self.assertEqual(ranges, [(0,160000), (16000,176000)])
        self.assertEqual(probe.window_ranges(16000), [(0,16000)])

    def test_manifest_duration_uses_ceiling_for_last_partial_millisecond(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            entry = self.fixture(root)
            data = struct.pack('<f', 0.125) * 16001
            (root / 'signal.f32').write_bytes(data)
            entry.update(pcm_samples=16001, pcm_bytes=len(data), duration_ms=1001,
                         pcm_sha256=hashlib.sha256(data).hexdigest())
            (root / 'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
            self.assertEqual(probe.load_corpus(root)[0]['pcm_samples'], 16001)


if __name__ == '__main__':
    unittest.main()
