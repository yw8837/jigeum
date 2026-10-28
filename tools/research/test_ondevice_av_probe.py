import hashlib
import tempfile
import unittest
import shutil
import subprocess
import wave
import copy
from pathlib import Path

from ondevice_av_probe import (validate_interval, validate_result, verify_model, prepare_clip,
                               validate_perception, validate_bundle, validate_output_path,
                               validate_whole_interval, response_status, build_contents)


class InputAndResponseTests(unittest.TestCase):
    def test_interval_boundaries(self):
        self.assertEqual(validate_interval(0, 1000), 1000)
        self.assertEqual(validate_interval(12500, 32500), 20000)

    def test_invalid_intervals(self):
        for start, end in [(-1, 1000), (0, 999), (0, 20001), (5000, 4000),
                           (False, 1000), (0, 1000.5)]:
            with self.subTest(start=start, end=end), self.assertRaises(ValueError):
                validate_interval(start, end)

    def valid(self):
        return dict(status="generated", observations=[
            dict(id="o1", start_ms=0, end_ms=1000, audio="Laughter", visual="Smiling")],
            phrase="헤헤! 나랑 같이 웃어요.", evidence_ids=["o1"], limitations=[])

    def test_valid_generated(self):
        self.assertEqual(validate_result(self.valid(), 10000), [])

    def test_valid_withheld(self):
        self.assertEqual(validate_result(dict(status="withheld", observations=[],
                         phrase="", evidence_ids=[], limitations=["No baby"]), 10000), [])

    def test_withheld_cannot_contain_phrase(self):
        value = self.valid()
        value["status"] = "withheld"
        self.assertTrue(validate_result(value, 10000))

    def test_generated_requires_evidence(self):
        value = self.valid()
        value["evidence_ids"] = ["missing"]
        self.assertTrue(validate_result(value, 10000))
        value["evidence_ids"] = []
        self.assertTrue(validate_result(value, 10000))

    def test_invalid_timestamp(self):
        for start, end in [(-1, 1000), (0, 10001), (1000, 1000), (True, 1000)]:
            value = self.valid()
            value["observations"][0].update(start_ms=start, end_ms=end)
            self.assertTrue(validate_result(value, 10000))

    def test_duplicate_observations(self):
        value = self.valid()
        value["observations"].append(value["observations"][0].copy())
        self.assertTrue(validate_result(value, 10000))

    def test_malformed_shape(self):
        for value in [None, [], {}, dict(status="generated", observations=None),
                      dict(status="ok", phrase=123)]:
            self.assertTrue(validate_result(value, 10000))

    def test_model_integrity_positive_and_negative(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.litertlm"
            path.write_bytes(b"known-content")
            manifest = dict(size_bytes=path.stat().st_size,
                            sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            verify_model(path, manifest)
            path.write_bytes(b"other-content")
            with self.assertRaises(ValueError):
                verify_model(path, manifest)
            path.write_bytes(b"short")
            with self.assertRaises(ValueError):
                verify_model(path, manifest)

    def test_observation_probe_rejects_generated_phrase(self):
        value = self.valid()
        self.assertTrue(validate_perception(value, 10000))

    def test_observation_probe_accepts_events_without_baby_phrase(self):
        value = self.valid()
        value = {k: value[k] for k in ('observations', 'limitations')}
        self.assertEqual(validate_perception(value, 10000), [])
        value['observations'][0]['end_ms'] = 10001
        self.assertTrue(validate_perception(value, 10000))

    def test_perception_rejects_zero_or_more_than_three_events(self):
        for count in (0, 4):
            value = dict(observations=[dict(id=f'o{i}', start_ms=i * 1000,
                         end_ms=(i + 1) * 1000, audio='Tone', visual='Red field')
                         for i in range(count)], limitations=[])
            with self.subTest(count=count):
                errors = validate_perception(value, 10000)
                self.assertTrue(errors)
                self.assertEqual(response_status(value, errors, True), 'invalid')

    def test_perception_requires_exact_observation_fields(self):
        event = self.valid()['observations'][0]
        variants = [dict(event, confidence=.9)] + [
            {key: value for key, value in event.items() if key != missing}
            for missing in ('id', 'start_ms', 'end_ms', 'audio', 'visual')]
        for observation in variants:
            with self.subTest(fields=sorted(observation)):
                self.assertTrue(validate_perception(
                    dict(observations=[observation], limitations=[]), 10000))

    def test_perception_rejects_invalid_or_more_than_three_limitations(self):
        for limitations in (None, 'none', {}, [1], ['a', 'b', 'c', 'd']):
            value = dict(observations=self.valid()['observations'], limitations=limitations)
            with self.subTest(limitations=limitations):
                self.assertTrue(validate_perception(value, 10000))

    def test_perception_accepts_three_events_and_three_limitations(self):
        value = dict(observations=[dict(id=f'o{i}', start_ms=i * 1000,
                     end_ms=(i + 1) * 1000, audio='Tone', visual='Red field')
                     for i in range(3)], limitations=['Unclear source', 'Sampled images', 'No identity'])
        errors = validate_perception(value, 10000)
        self.assertEqual(errors, [])
        self.assertEqual(response_status(value, errors, True), 'observed')

    def test_whole_interval_requires_both_modalities_and_explicit_limits(self):
        value = dict(audio_summary="A steady tone throughout.",
                     visual_sequence="Red first, then blue.", limitations=[])
        self.assertEqual(validate_whole_interval(value), [])
        for bad in [dict(value, phrase="invented"), dict(value, audio_summary=""),
                    dict(value, limitations="none"), dict(value, visual_sequence=[])]:
            self.assertTrue(validate_whole_interval(bad))

    def test_schema_failure_never_reports_generated_success(self):
        self.assertEqual(response_status(self.valid(), ["invalid_observation_time"], False), "invalid")
        self.assertEqual(response_status(self.valid(), [], False), "generated")

    def test_recommended_order_preserves_every_frame_then_prompt_then_audio(self):
        bundle = dict(audio="local.wav", frames=[dict(path="first.jpg", offset_ms=0),
                                                dict(path="last.jpg", offset_ms=9000)])
        value = build_contents("observe", bundle, media_order="recommended")
        self.assertEqual([c["path"] for c in value if c["type"] == "image"], ["first.jpg", "last.jpg"])
        self.assertEqual(value[0]["type"], "image")
        self.assertEqual(value[-2:], [dict(type="text", text="observe"), dict(type="audio", path="local.wav")])
        reverse = build_contents("observe", bundle, reverse=True, media_order="recommended")
        self.assertEqual([c["path"] for c in reverse if c["type"] == "image"], ["last.jpg", "first.jpg"])
        self.assertEqual([c["text"] for c in reverse if c["type"] == "text"][:2],
                         ["Frame at 0 ms", "Frame at 9000 ms"])
        audio = build_contents("observe", bundle, audio_only=True, media_order="recommended")
        self.assertEqual([c["type"] for c in audio], ["text", "audio"])


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg required")
class DecoderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.source = cls.root / "delayed-audio.mp4"
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
                        "-f", "lavfi", "-i", "color=c=red:s=64x64:r=10:d=4",
                        "-itsoffset", "0.2", "-f", "lavfi", "-i",
                        "sine=frequency=440:sample_rate=48000:duration=3.8",
                        "-c:v", "libx264", "-c:a", "aac", "-pix_fmt", "yuv420p",
                        str(cls.source)], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_nonzero_selection_uses_real_in_range_pts(self):
        result = prepare_clip(self.source, 2100, 3100, 2, self.root / "selected")
        self.assertEqual(result["duration_ms"], 1000)
        self.assertEqual([f["offset_ms"] for f in result["frames"]], [0, 500])
        with wave.open(result["audio"], "rb") as wav:
            self.assertEqual(wav.getframerate(), 16000)
            self.assertEqual(wav.getnframes(), 16000)

    def test_delayed_audio_is_not_shifted_to_start(self):
        result = prepare_clip(self.source, 0, 1000, 1, self.root / "delayed")
        with wave.open(result["audio"], "rb") as wav:
            first150ms = wav.readframes(2400)
        self.assertTrue(all(byte == 0 for byte in first150ms))

    def test_selection_beyond_source_is_rejected(self):
        with self.assertRaises(ValueError):
            prepare_clip(self.source, 3000, 5000, 1, self.root / "too-long")

    def test_absent_audio_is_explicit_in_video_only_bundle(self):
        source = self.root / "no-audio.mp4"
        subprocess.run(['ffmpeg', '-v', 'error', '-i', str(self.source), '-an',
                        '-c:v', 'copy', str(source)], check=True, capture_output=True)
        with self.assertRaises(ValueError):
            prepare_clip(source, 0, 1000, 1, self.root / 'missing-audio')
        result = prepare_clip(source, 0, 1000, 1, self.root / 'video-only', require_audio=False)
        self.assertIsNone(result['audio'])
        self.assertEqual(len(result['frames']), 1)
        self.assertFalse((self.root / 'video-only' / 'audio.wav').exists())

    def test_bundles_reject_bad_duration_timestamps_and_audio_length(self):
        valid = prepare_clip(self.source, 0, 1000, 2, self.root / 'bundle-validation')
        validate_bundle(valid)
        for changes in [dict(duration_ms=30000), dict(duration_ms=2000),
                        dict(frames=[dict(offset_ms=50000, path=valid['frames'][0]['path'])]),
                        dict(frames=list(reversed(valid['frames']))), dict(frames=[]),
                        dict(audio='https://example.test/baby.wav')]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_bundle(dict(valid, **changes))

    def test_ambiguous_audio_tracks_are_rejected(self):
        multi = self.root / 'multiple-audio.mp4'
        subprocess.run(['ffmpeg', '-v', 'error', '-i', str(self.source),
                        '-map', '0:v', '-map', '0:a', '-map', '0:a', '-c', 'copy',
                        str(multi)], check=True, capture_output=True)
        with self.assertRaisesRegex(ValueError, 'Multiple audio tracks'):
            prepare_clip(multi, 0, 1000, 1, self.root / 'ambiguous-tracks')


class BundleAudioTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2] / 'work/review-standards'
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix='wav-validation-', dir=root)
        self.addCleanup(self.temp.cleanup)
        self.wav_path = Path(self.temp.name) / 'synthetic.wav'
        with wave.open(str(self.wav_path), 'wb') as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(16000)
            wav.writeframes(bytes(32000))
        self.bundle = dict(duration_ms=1000, frames=[], audio=str(self.wav_path))

    def test_complete_audio_only_pcm_is_accepted(self):
        validate_bundle(self.bundle, audio_only=True)

    def test_truncated_pcm_payload_is_rejected_even_with_valid_duration_header(self):
        original = self.wav_path.read_bytes()
        for data in (original[:44], original[:100], original[:-1]):
            with self.subTest(file_bytes=len(data)):
                self.wav_path.write_bytes(data)
                with self.assertRaises(ValueError):
                    validate_bundle(self.bundle, audio_only=True)

    def test_malformed_wav_headers_raise_input_validation_error(self):
        oversized_chunk = b'RIFF' + (12).to_bytes(4, 'little') + b'WAVEJUNK' + (100).to_bytes(4, 'little')
        for data in (b'', b'RIFF', b'not a WAV header', oversized_chunk):
            with self.subTest(file_bytes=len(data)):
                self.wav_path.write_bytes(data)
                with self.assertRaises(ValueError):
                    validate_bundle(self.bundle, audio_only=True)

    def test_pcm_data_chunk_must_contain_whole_frames(self):
        data = bytearray(self.wav_path.read_bytes() + b'\x00')
        data[4:8] = (len(data) - 8).to_bytes(4, 'little')
        data[40:44] = (32001).to_bytes(4, 'little')
        self.wav_path.write_bytes(data)
        with self.assertRaises(ValueError):
            validate_bundle(self.bundle, audio_only=True)


class StorageBoundaryTests(unittest.TestCase):
    def test_repo_private_outputs_must_be_in_ignored_work(self):
        repo = Path(__file__).resolve().parents[2]
        for path in [repo / 'docs/research/leak', repo / 'tools/research/leak',
                     repo / 'work/../docs/leak', Path('C:/unrestricted-output')]:
            with self.subTest(path=path), self.assertRaises(ValueError):
                validate_output_path(path)
        validate_output_path(repo / 'work/ondevice-av/allowed')


if __name__ == "__main__":
    unittest.main()
