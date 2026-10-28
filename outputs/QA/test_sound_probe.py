"""Host contract tests; Android/ADB is replaced only at the external boundary."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import sound_probe


class SoundProbeContractTest(unittest.TestCase):
    def failed_instrumentation(self, timeout=False):
        commands = []
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            pcm = bytes(64000)
            (root / 'a.f32').write_bytes(pcm)
            (root / 'manifest.json').write_text(json.dumps([dict(id='a', expected='cry', allowed=['cry'],
                pcm_sha256=hashlib.sha256(pcm).hexdigest())]), encoding='utf-8')
            apk = root / 'fake.apk'; apk.write_bytes(b'apk')
            def fake(argv, **kwargs):
                commands.append(argv)
                if 'instrument' in argv:
                    if timeout: raise subprocess.TimeoutExpired(argv, 300, output=b'partial status')
                    return subprocess.CompletedProcess(argv, 0, b'FAILURES\n', b'')
                if 'pidof' in argv: return subprocess.CompletedProcess(argv, 1, b'', b'')
                return subprocess.CompletedProcess(argv, 0, b'', b'')
            with patch.object(sound_probe, 'adb_command', return_value=['fake-adb']), \
                 patch.object(sound_probe, 'evidence_dir', return_value=root), \
                 patch.object(sound_probe, 'APK', apk), patch.object(sound_probe, 'TEST_APK', apk), \
                 patch.object(sound_probe, 'installed_provenance', return_value={'verified': True}), \
                 patch.object(sound_probe.subprocess, 'run', side_effect=fake), \
                 patch('sys.argv', ['sound_probe', '--corpus', str(root)]), \
                 contextlib.redirect_stdout(io.StringIO()):
                code = sound_probe.main()
            report = json.loads(next(root.glob('sound-probe-*/sound-report.json')).read_text(encoding='utf-8'))
        return code, commands, report

    def test_failed_instrumentation_never_reads_or_counts_old_device_results(self):
        code, commands, report = self.failed_instrumentation()
        self.assertEqual(1, code)
        self.assertEqual(0, report['cases'])
        self.assertFalse(any('cat' in argv for argv in commands))
        self.assertEqual('fail', report['execution']['status'])

    def test_timeout_stops_owned_packages_and_verifies_no_remaining_process(self):
        code, commands, report = self.failed_instrumentation(timeout=True)
        self.assertEqual(1, code)
        for package in (sound_probe.PACKAGE, sound_probe.PACKAGE + '.test'):
            self.assertTrue(any(argv[-2:] == ['force-stop', package] for argv in commands))
            self.assertTrue(any(argv[-2:] == ['pidof', package] for argv in commands))
        self.assertTrue(report['timeout_cleanup']['complete'])
        self.assertFalse(any('cat' in argv for argv in commands))

    def test_rejects_overlong_nonfinite_unaligned_and_out_of_range_pcm(self):
        minimum = bytes(64000)
        cases = {
            '20001ms': bytes(1280064),
            'nan': struct.pack('<f', float('nan')) + minimum[4:],
            'infinity': struct.pack('<f', float('inf')) + minimum[4:],
            'amplitude': struct.pack('<f', 1.002) + minimum[4:],
            'truncated_float': minimum[:-1],
            'below_one_second': minimum[:-4],
        }
        for name, pcm in cases.items():
            with self.subTest(name=name), self.assertRaises(ValueError):
                sound_probe.validate_pcm(pcm, hashlib.sha256(pcm).hexdigest(), name)

    def test_rejects_pcm_hash_mismatch(self):
        with self.assertRaisesRegex(ValueError, 'hash'):
            sound_probe.validate_pcm(bytes(64000), '0' * 64, 'wrong_hash')

    def test_preserves_existing_amplitude_tolerance_and_fractional_ms_tail(self):
        pcm = struct.pack('<f', 1.001) + bytes(64000)
        metadata = sound_probe.validate_pcm(pcm, hashlib.sha256(pcm).hexdigest(), 'edge')
        self.assertEqual(16001, metadata['pcm_samples'])
        self.assertEqual(1001, metadata['duration_ms'])

    def test_rejects_stale_incomplete_or_misreceived_device_results(self):
        expected = dict(id='a', pcm_bytes=64000, pcm_samples=16000, duration_ms=1000,
                        pcm_sha256='a' * 64, expected='cry', allowed=['cry'])
        valid = dict(expected, run_id='b' * 32, status='measured', observed='cry',
                     received_bytes=64000, received_sha256='a' * 64, samples=16000, range_end_ms=1000)
        for field, value in [('run_id', 'c' * 32), ('received_bytes', 63996),
                             ('received_sha256', 'c' * 64), ('samples', 15999),
                             ('range_end_ms', 999), ('status', 'error')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                sound_probe.validate_measurements([dict(valid, **{field: value})], [expected], 'b' * 32)
        with self.assertRaises(ValueError):
            sound_probe.validate_measurements([], [expected], 'b' * 32)
        self.assertEqual([], sound_probe.validate_measurements([valid], [expected], 'b' * 32))

    def test_strict_mismatch_exits_nonzero_even_with_successful_instrumentation(self):
        self.assertEqual(1, sound_probe.probe_exit_code({'status': 'pass'}, [{'id': 'a'}], True))
        self.assertEqual(0, sound_probe.probe_exit_code({'status': 'pass'}, [{'id': 'a'}], False))
        self.assertEqual(1, sound_probe.probe_exit_code({'status': 'fail'}, [], False))

    def test_twenty_second_tail_event_reaches_measurement_without_truncation(self):
        # A 15-second restriction loses the event in the final second.
        pcm = bytes(19 * 64000) + struct.pack('<f', .25) * 16000
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            corpus = root / 'corpus'
            corpus.mkdir()
            entry = dict(id='tail_event', pcm_sha256=hashlib.sha256(pcm).hexdigest(),
                         expected='babble', allowed=['babble'])
            (corpus / 'tail_event.f32').write_bytes(pcm)
            (corpus / 'manifest.json').write_text(json.dumps([entry]), encoding='utf-8')
            apk = root / 'test.apk'
            apk.write_bytes(b'apk-placeholder')
            sent = {}

            def fake_adb(argv, **kwargs):
                if 'exec-in' in argv:
                    destination = argv[-1].split('> ', 1)[1]
                    sent[Path(destination).name] = kwargs['input']
                if 'instrument' in argv:
                    raw = ('INSTRUMENTATION_STATUS: class=example.PublicAudioProbeTest\n'
                           'INSTRUMENTATION_STATUS: test=reportPublicSoundEventProbes\n'
                           'INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: current=1\n'
                           'INSTRUMENTATION_STATUS_CODE: 1\n'
                           'INSTRUMENTATION_STATUS: class=example.PublicAudioProbeTest\n'
                           'INSTRUMENTATION_STATUS: test=reportPublicSoundEventProbes\n'
                           'INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: current=1\n'
                           'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n')
                    return subprocess.CompletedProcess(argv, 0, raw.encode(), b'')
                if 'cat' in argv:
                    entries = json.loads(sent['manifest.json'])
                    rows = [dict(e, observed='babble', status='measured', received_bytes=len(pcm),
                                 received_sha256=hashlib.sha256(pcm).hexdigest(), samples=320000,
                                 range_end_ms=20000) for e in entries]
                    return subprocess.CompletedProcess(argv, 0, json.dumps(rows).encode(), b'')
                return subprocess.CompletedProcess(argv, 0, b'', b'')

            caught = None
            with patch.object(sound_probe, 'adb_command', return_value=['fake-adb']), \
                 patch.object(sound_probe, 'evidence_dir', return_value=root), \
                 patch.object(sound_probe, 'APK', apk), patch.object(sound_probe, 'TEST_APK', apk), \
                 patch.object(sound_probe, 'installed_provenance', return_value={'verified': True}), \
                 patch.object(sound_probe.subprocess, 'run', side_effect=fake_adb), \
                 patch('sys.argv', ['sound_probe', '--corpus', str(corpus)]), \
                 contextlib.redirect_stdout(io.StringIO()):
                try:
                    code = sound_probe.main()
                except Exception as error:
                    caught = error
                    code = None
            self.assertIsNone(caught, f'Valid full 20-second PCM rejected: {caught}')
            self.assertEqual(0, code)
            self.assertEqual(pcm, sent['tail_event.f32'])


if __name__ == '__main__':
    unittest.main()
