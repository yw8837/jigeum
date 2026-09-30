"""Rebuild the approved trailer soundtrack without re-encoding its picture."""
from pathlib import Path
import hashlib
import json
import subprocess
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
AUDIO = HERE / 'audio'
ASSETS = HERE / 'assets'
CACHE = ROOT / 'work/marketing-rebuild/cache'
FINAL = ROOT / 'outputs/Marketing/jigeum-trailer-b-family.mp4'
REBUILD = ROOT / 'work/marketing-rebuild/jigeum-trailer-b-family.mp4'
RATE, DURATION = 48000, 26.5


def run(args):
    return subprocess.run(args, check=True, capture_output=True)


def decode(path, process=False):
    args = ['ffmpeg', '-v', 'error', '-i', str(path)]
    if process:
        args += ['-af', 'highpass=f=90,lowpass=f=7500,acompressor=threshold=0.12:ratio=3:attack=3:release=65:makeup=1']
    raw = run(args + ['-f', 'f32le', '-ac', '2', '-ar', str(RATE), '-']).stdout
    return np.frombuffer(raw, dtype='<f4').reshape(-1, 2).copy()


def video_hash(path):
    return run(['ffmpeg', '-v', 'error', '-i', str(path), '-map', '0:v:0',
                '-c:v', 'copy', '-f', 'hash', '-hash', 'sha256', '-']).stdout.decode().strip()


def level(samples, rms_db, peak_db):
    rms = np.sqrt(np.mean(samples.astype(np.float64) ** 2))
    peak = np.max(np.abs(samples))
    return samples * min(10 ** (rms_db / 20) / max(rms, 1e-9),
                         10 ** (peak_db / 20) / max(peak, 1e-9))


def measure(path):
    result = run(['ffmpeg', '-hide_banner', '-i', str(path), '-vn', '-af',
                  'loudnorm=I=-20:TP=-2:LRA=9:print_format=json', '-f', 'null', '-'])
    log = result.stderr.decode('utf-8', errors='replace')
    return json.loads(log[log.rfind('{'):log.rfind('}') + 1])


def mix_audio(video=FINAL, destination=REBUILD):
    destination.parent.mkdir(parents=True, exist_ok=True)
    CACHE.mkdir(parents=True, exist_ok=True)
    count = round(RATE * DURATION)
    timeline = np.arange(count) / RATE
    music = decode(ASSETS / 'music.m4a')[:count]
    music = np.pad(music, ((0, max(0, count - len(music))), (0, 0)))
    music = level(music, -28, -12)
    envelope = np.ones(count)
    events = [('baby-fuss.wav', .22, 3.0), ('baby-giggle.wav', 19.62, 3.0)]
    for _, start, length in events:
        # Ease down just before each voice, then recover gently after it.
        dip = np.interp(timeline, [0, max(0, start-.16), start, start+length,
                                  start+length+.4, DURATION], [1, 1, .42, .42, 1, 1])
        envelope = np.minimum(envelope, dip)
    envelope *= np.minimum(timeline/.12, 1)
    envelope *= np.clip((DURATION-timeline)/.65, 0, 1)
    mixed = music * envelope[:, None]
    for name, start, length in events:
        clip = level(decode(AUDIO / name, True)[:round(length * RATE)], -23, -7)
        ct = np.arange(len(clip)) / RATE
        fade = np.minimum(ct/.025, 1) * np.clip((length-ct)/.15, 0, 1)
        clip *= fade[:, None]
        offset = round(start * RATE)
        mixed[offset:offset+len(clip)] += clip
    premix = CACHE / 'premix.wav'
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-f', 'f32le', '-ar', str(RATE),
                    '-ac', '2', '-i', '-', '-c:a', 'pcm_f32le', str(premix)],
                   input=mixed.astype('<f4').tobytes(), check=True, capture_output=True)
    measured = measure(premix)
    normalization = ('loudnorm=I=-20:TP=-2:LRA=9:linear=true:'
                     f'measured_I={measured["input_i"]}:measured_TP={measured["input_tp"]}:'
                     f'measured_LRA={measured["input_lra"]}:measured_thresh={measured["input_thresh"]}:'
                     f'offset={measured["target_offset"]}')
    candidate = CACHE / 'audio-verified.mp4'
    picture_before = video_hash(video)
    run(['ffmpeg', '-y', '-v', 'error', '-i', str(video), '-i', str(premix),
         '-map', '0:v:0', '-map', '1:a:0', '-c:v', 'copy', '-af', normalization,
         '-c:a', 'aac', '-b:a', '192k', '-ar', str(RATE), '-t', str(DURATION),
         '-movflags', '+faststart', str(candidate)])
    run(['ffmpeg', '-v', 'error', '-xerror', '-i', str(candidate), '-f', 'null', '-'])
    assert picture_before == video_hash(candidate), 'Picture stream changed'
    final_audio = measure(candidate)
    assert float(final_audio['input_tp']) <= -1.5, 'Audio peak too high'
    assert abs(float(final_audio['input_i']) + 20) < 1.0, 'Audio loudness outside target'
    probe = json.loads(run(['ffprobe', '-v', 'error', '-show_streams', '-show_format',
                           '-of', 'json', str(candidate)]).stdout)
    assert abs(float(probe['format']['duration']) - DURATION) < .05
    candidate.replace(destination)
    report = {'duration_seconds': DURATION, 'events': events, 'picture_stream_unchanged': True,
              'integrated_lufs': float(final_audio['input_i']), 'true_peak_dbtp': float(final_audio['input_tp']),
              'decode_errors': 0, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
              'source': 'ElevenLabs Sound Effects, generated from text; no real baby recording',
              'subjective_listening': 'Not performed by this agent; audio input unavailable'}
    (CACHE / 'mix-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == '__main__':
    mix_audio()
