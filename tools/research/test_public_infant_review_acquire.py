"""Public acquisition and level boundaries; no network, audio files or inference."""
import copy
from email.message import Message
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

import public_infant_review_acquire as probe


def page(row):
    return ('<h1>'+row['title']+'</h1><div id="soundDescriptionSection">'+row['description']+
        '</div><a href="'+row['current_license_url']+'">Creative Commons 0</a>'+
        '<span data-mp3="'+row['preview_url']+'"></span>').encode()


class PublicReviewBoundaries(unittest.TestCase):
    def test_source_mutation_cannot_enable_training_or_another_url(self):
        self.assertEqual([r['id'] for r in probe.sources()], ['9437', '244237'])
        for key, value in [('id', '../private'), ('preview_url', 'https://127.0.0.1/private'),
                ('page_url', 'https://freesound.org/people/other/sounds/1/'),
                ('current_license_url', 'https://creativecommons.org/licenses/by-nc/4.0/'),
                ('training_blocked', False), ('approved_training_label', 'babble')]:
            rows = copy.deepcopy(probe.SOURCE_ROWS)
            rows[0][key] = value
            with patch.object(probe, 'SOURCE_ROWS', rows), self.subTest(key=key), self.assertRaises(ValueError):
                probe.sources()

    def test_path_and_direct_worker_reject_before_network(self):
        for path in (probe.ROOT/'docs/run01', probe.ROOT/'work/other/run01', probe.EVIDENCE_ROOT,
                     probe.EVIDENCE_ROOT/'run01/nested'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                probe.safe_path(path, run=True)
        with patch.dict(probe.os.environ, {}, clear=True), patch.object(probe, 'fetch') as fetch, self.assertRaises(ValueError):
            probe.main(['--output', str(probe.EVIDENCE_ROOT/'run97'), '--ffmpeg', str(probe.FFMPEG), '--worker'])
        fetch.assert_not_called()

    def test_exact_allowlist_and_redirect_guard(self):
        for url in ('https://127.0.0.1/private', probe.sources()[0]['preview_url']+'?x=1',
                    'http://cdn.freesound.org/previews/9/9437_383-lq.mp3'):
            with patch.object(probe.urllib.request, 'build_opener') as network, self.assertRaises(ValueError):
                probe.fetch(url)
            network.assert_not_called()
        with self.assertRaises(ValueError):
            probe.NoRedirects().redirect_request(None, None, None, None, None, 'https://example.com/')

    def test_http_status_type_length_body_cap_and_deadline(self):
        url = probe.sources()[0]['preview_url']
        def response(data=b'public', status=200, response_url=url, mime='audio/mpeg', length=None):
            obj = MagicMock()
            obj.__enter__.return_value = obj
            obj.status, obj.url = status, response_url
            obj.headers = Message()
            obj.headers['Content-Type'] = mime
            if length is not None:
                obj.headers['Content-Length'] = str(length)
            obj.read1.side_effect = [data, b'']
            return obj
        good = response(length=6)
        with patch.object(probe.urllib.request, 'build_opener') as network:
            network.return_value.open.return_value = good
            self.assertEqual(probe.fetch(url), b'public')
            self.assertEqual(network.return_value.open.call_args.kwargs['timeout'], 20)
        for obj in (response(status=201), response(response_url='https://example.com/'),
                    response(mime='text/html'), response(length=probe.CAP+1),
                    response(data=b'x'*(probe.CAP+1)), response(length=7)):
            with patch.object(probe.urllib.request, 'build_opener') as network, self.assertRaises(ValueError):
                network.return_value.open.return_value = obj
                probe.fetch(url)
        with patch.object(probe.urllib.request, 'build_opener') as network, \
                patch.object(probe.time, 'monotonic', side_effect=[0, 21]), self.assertRaises(TimeoutError):
            network.return_value.open.return_value = response()
            probe.fetch(url)

    def test_current_official_rights_description_and_preview_must_match(self):
        row = probe.sources()[0]
        verified = probe.confirm_page(row, page(row))
        self.assertEqual(verified['description_links'], [])
        self.assertFalse(verified['age_verified'])
        for key, value in [('description', 'changed'), ('title', 'changed'),
                ('current_license_url', 'https://creativecommons.org/licenses/by-nc/4.0/'),
                ('preview_url', 'https://cdn.freesound.org/other.mp3')]:
            changed = dict(row, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                probe.confirm_page(row, page(changed))
        linked = page(row).replace(b'</div>', b'<a href="https://example.com/">source</a></div>')
        with self.assertRaises(ValueError):
            probe.confirm_page(row, linked)

    def test_completed_http_response_closes_socket_after_last_body_read(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status, response.url = 200, probe.sources()[0]['preview_url']
        response.headers = Message()
        response.headers['Content-Type'] = 'audio/mpeg'
        response.headers['Content-Length'] = '6'
        def final_read(unused):
            response.fp = None
            return b'public'
        response.read1.side_effect = final_read
        with patch.object(probe.urllib.request, 'build_opener') as network:
            network.return_value.open.return_value = response
            self.assertEqual(probe.fetch(response.url), b'public')
        self.assertEqual(response.read1.call_count, 1)

    def test_reuse_same_path_requires_original_url_bytes_sha_and_record(self):
        row = probe.sources()[0]
        path = MagicMock()
        path.exists.return_value = True
        path.is_symlink.return_value = False
        path.stat.return_value.st_size = 17
        root = MagicMock()
        root.__truediv__.return_value = path
        old = dict(row, bytes=17, sha256='a'*64)
        with patch.object(probe, 'AUDIO_ROOT', root), patch.object(probe, 'safe_path'), \
                patch.object(probe, 'digest', return_value='a'*64), patch.object(probe, 'fetch') as fetch:
            result = probe.acquire(row, [old])
            self.assertTrue(result['reused_existing_copy'])
            fetch.assert_not_called()
            path.open.assert_not_called()
            for records in ([], [old, old], [dict(old, sha256='b'*64)],
                    [dict(old, preview_url='https://example.com/')], [dict(old, bytes=18)]):
                with self.subTest(records=len(records)), self.assertRaises(ValueError):
                    probe.reuse_audio(row, records)

    def test_levels_worked_values_and_partial_bin_are_acoustic_only(self):
        report = probe.level_report(b'\x00\x20\x00\xe0')  # +8192 and -8192, normalized +/-0.25
        self.assertEqual(report['actual_samples'], 2)
        self.assertEqual(report['full']['rms'], .25)
        self.assertEqual(report['full']['peak'], .25)
        self.assertEqual(report['full']['dc'], 0)
        self.assertEqual(report['full']['rail_clipping_samples'], 0)
        probe.validate_levels(report)
        report = probe.level_report(b'\x00\x00' * 16001)
        self.assertEqual([(b['start_sample'], b['end_sample']) for b in report['bins']], [(0, 16000), (16000, 16001)])
        probe.validate_levels(report)
        rails = probe.level_report(b'\x00\x80\xff\x7f')
        self.assertEqual(rails['full']['rail_clipping_samples'], 2)
        self.assertEqual(rails['full']['rail_clipping_fraction'], 1)
        self.assertEqual(rails['full']['dc'], -1/65536)
        for bad in (b'', b'\x00', b'\x00'*(probe.MAX_SECONDS*probe.RATE*2+2)):
            with self.assertRaises(ValueError):
                probe.level_report(bad)

    def test_missing_nonfinite_and_incomplete_bin_fields_rejected(self):
        valid = probe.level_report(b'\x00\x00' * 16001)
        for edit in ('rms', 'dc', 'peak', 'rail_clipping_samples', 'samples'):
            changed = copy.deepcopy(valid)
            del changed['bins'][0][edit]
            with self.subTest(edit=edit), self.assertRaises(ValueError):
                probe.validate_levels(changed)
        for edit, value in [('rms', float('nan')), ('dc', float('inf')), ('peak', -.1)]:
            changed = copy.deepcopy(valid)
            changed['full'][edit] = value
            with self.subTest(edit=edit), self.assertRaises(ValueError):
                probe.validate_levels(changed)
        changed = copy.deepcopy(valid)
        changed['bins'].pop()
        with self.assertRaises(ValueError):
            probe.validate_levels(changed)

    def test_binary_path_and_final_pin_drift_are_rejected(self):
        with self.assertRaises(ValueError):
            probe.verify_ffmpeg(probe.ROOT/'private.exe')
        before = dict(source_sha256=probe.SOURCE_SHA, code_sha256={'a': 'a'}, binary_sha256=probe.BINARY_SHA)
        with patch.object(probe, 'capture_pins', return_value=before):
            self.assertEqual(probe.verify_pins(before), before)
        for field in ('source_sha256', 'code_sha256', 'binary_sha256'):
            changed = copy.deepcopy(before)
            changed[field] = 'changed'
            with patch.object(probe, 'capture_pins', return_value=changed), self.subTest(field=field), self.assertRaises(ValueError):
                probe.verify_pins(before)

    def test_completion_requires_every_source_rights_format_stat_and_blocked_flag(self):
        pins = {}
        cases = []
        for row in probe.sources():
            cases.append(dict(row, bytes=17, sha256='a'*64, reused_existing_copy=False,
                current_page_evidence=probe.confirm_page(row, page(row)),
                acquired_format=dict(codec_name='mp3', sample_rate=44100, channels=2,
                    format_duration_seconds=1.02, format_size_bytes=17, bit_rate=128000),
                **probe.level_report(b'\x00\x00'*16000)))
        done = dict(status='completed', requested_files=2, decoded_files=2, cases=cases,
            pins=pins, listening_api_available=False, **{f:False for f in probe.FALSE_FLAGS})
        root, audio_path = MagicMock(), MagicMock()
        root.__truediv__.return_value = audio_path
        root.glob.return_value = [Path('244237.mp3'), Path('9437.mp3')]
        audio_path.stat.return_value.st_size = 17
        with patch.object(probe, 'AUDIO_ROOT', root), patch.object(probe, 'digest', return_value='a'*64), \
                patch.object(probe, 'verify_pins'):
            probe.validate_completion(done, pins)
            for key in ('cases', 'pins', 'audio_review_completed', 'training', 'decoded_files'):
                bad = copy.deepcopy(done)
                del bad[key]
                with self.subTest(key=key), self.assertRaises(ValueError):
                    probe.validate_completion(bad, pins)
            for key in ('current_page_evidence', 'acquired_format', 'sha256', 'full', 'bins', 'training_blocked'):
                bad = copy.deepcopy(done)
                del bad['cases'][0][key]
                with self.subTest(key=key), self.assertRaises(ValueError):
                    probe.validate_completion(bad, pins)


if __name__ == '__main__':
    unittest.main()
