"""Exact two public CC0 previews and acoustic levels; no inference or event labels."""
import argparse
from array import array
from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
import urllib.request
import uuid

from ast_device_benchmark import storage_snapshot
from bounded_process import run_bounded
from infant_head_screen import NoRedirects, ROOT, digest, write

EVIDENCE_ROOT = ROOT / 'work/new-infant-review-20261007'
AUDIO_ROOT = EVIDENCE_ROOT / 'audio'
FFMPEG = Path(os.environ['LOCALAPPDATA']) / 'Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-9.0-full_build/bin/ffmpeg.exe'
FFPROBE = FFMPEG.with_name('ffprobe.exe')
BINARY_SHA = {'ffmpeg': '05f4251bce9293c2ab492cb17ca7724a0ffd0d06c881ba2ee83b82a89c2fc740',
             'ffprobe': '51e0780cd881f83749b029ed716cbb841c2eac6289f418050f2f2961b158896b'}
CAP = 3 * 1024**2
PAGE_CAP = 512 * 1024
RATE = 16000
MAX_SECONDS = 120
SOURCE_ROWS = [
    dict(id='9437', sourcegroup='Freesound:Rodcencko', uploader='Rodcencko',
         page_url='https://freesound.org/people/Rodcencko/sounds/9437/',
         preview_url='https://cdn.freesound.org/previews/9/9437_383-lq.mp3',
         title='childVoice001.aiff', caption_age_months=18,
         description='18 month female child babbling recorded with sony dat and mono microphone'),
    dict(id='244237', sourcegroup='Freesound:Sauron974', uploader='Sauron974',
         page_url='https://freesound.org/people/Sauron974/sounds/244237/',
         preview_url='https://cdn.freesound.org/previews/244/244237_152878-lq.mp3',
         title='Baby Girl 1 year old - Coos, Gurgles, Drops Toys', caption_age_months=12,
         description='A one-year-old American baby girl coos and gurgles while playing with a toy. Recorded in 2011 with an ElectroVoice CL-42 shotgun mic into a Zoom H4n portable recorder.'),
]
for _row in SOURCE_ROWS:
    _row.update(current_license_url='http://creativecommons.org/publicdomain/zero/1.0/',
                training_blocked=True, approved_training_label=None,
                role='future_train_review_candidate', age_verified=False,
                naturalness='caption_only_unreviewed', audio_review_completed=False,
                event_ground_truth=False)
SOURCE_SHA = '91e47d4fabbedea6bf4dc8735b8fd3398398b86da6b2330589f609e7de7ce960'
CODE_FILES = ('tools/research/public_infant_review_acquire.py',
              'tools/research/test_public_infant_review_acquire.py',
              'tools/research/infant_head_screen.py', 'tools/research/infant_preview_interface.py',
              'tools/research/ast_device_benchmark.py', 'tools/research/ondevice_av_probe.py',
              'tools/research/bounded_process.py', 'outputs/QA/instrument.py')
FALSE_FLAGS = ('training', 'calibration', 'inference', 'private_media_read', 'audio_review_completed',
               'event_ground_truth', 'quality_validated', 'product_changed', 'android_validated')


def canonical_sha(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True,
        separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def sources():
    rows = json.loads(json.dumps(SOURCE_ROWS))
    if canonical_sha(rows) != SOURCE_SHA:
        raise ValueError('Exact two CC0 review source configuration changed')
    return rows


def safe_path(path, *, run=False):
    """No Git subprocess: require the recorded work/ ignore rule and owned root."""
    absolute = path.absolute()
    for ancestor in (absolute, *absolute.parents):
        if ancestor.exists() and (ancestor.is_symlink() or ancestor.is_junction()):
            raise ValueError('Symlink/junction evidence paths prohibited')
    resolved = absolute.resolve()
    if (not resolved.is_relative_to(EVIDENCE_ROOT.resolve()) or resolved == EVIDENCE_ROOT.resolve()
            or 'work/' not in (ROOT / '.gitignore').read_text('utf-8').splitlines()):
        raise ValueError('Only the dedicated ignored evidence root is allowed')
    if run and (resolved.parent != EVIDENCE_ROOT.resolve() or not re.fullmatch(r'run[0-9]{2}', resolved.name)):
        raise ValueError('Use a fresh direct runNN directory in the dedicated evidence root')
    return resolved


def load(path):
    safe_path(path)
    if path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= 1024**2:
        raise ValueError('Evidence JSON file/size differs')
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(path.read_text('utf-8'), object_pairs_hook=pairs,
        parse_constant=lambda unused: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def capture_pins():
    sources()
    return dict(source_sha256=SOURCE_SHA,
        code_sha256={name: digest(ROOT / name) for name in CODE_FILES},
        binary_sha256={name: digest(path) for name, path in (('ffmpeg', FFMPEG), ('ffprobe', FFPROBE))},
        runtime=dict(python=platform.python_version(), beautifulsoup4=importlib.metadata.version('beautifulsoup4')))


def verify_pins(before):
    actual = capture_pins()
    if actual != before or actual['binary_sha256'] != BINARY_SHA:
        raise ValueError('Source/code/FFmpeg final provenance changed')
    return actual


def verify_ffmpeg(path):
    if path.absolute() != FFMPEG.absolute():
        raise ValueError('Only the existing exact full FFmpeg9.0 binary is allowed')
    for name, binary in (('ffmpeg', FFMPEG), ('ffprobe', FFPROBE)):
        if binary.is_symlink() or not binary.is_file() or digest(binary) != BINARY_SHA[name]:
            raise ValueError('Pinned full FFmpeg/ffprobe differs')
    result = subprocess.run([str(FFMPEG), '-hide_banner', '-muxers'], capture_output=True, timeout=10)
    if result.returncode or len(result.stdout + result.stderr) > 256*1024 or not re.search(
            rb'^\s*\S*E\s+s16le\s', result.stdout + result.stderr, re.MULTILINE):
        raise ValueError('FFmpeg s16le output capability missing before network')


def fetch(url, *, page=False):
    rows = sources()
    if url not in {r['page_url' if page else 'preview_url'] for r in rows}:
        raise ValueError('Only exact pinned official page/CDN paths are allowed')
    cap = PAGE_CAP if page else CAP
    expected_types = ('text/html',) if page else ('audio/mpeg', 'application/octet-stream')
    opener = urllib.request.build_opener(NoRedirects())
    started = time.monotonic()
    with opener.open(urllib.request.Request(url, headers={'User-Agent': 'Agattok-local-review/1.0'}), timeout=20) as response:
        if response.status != 200 or response.url != url or response.headers.get_content_type() not in expected_types:
            raise ValueError('Exact response status/URL/content type differs')
        length = response.headers.get('Content-Length')
        if length is not None and (not length.isdigit() or not 1 <= int(length) <= cap):
            raise ValueError('Response advertised byte cap exceeded')
        data = bytearray()
        while len(data) <= cap:
            # HTTPResponse closes fp after a read consumes the final content-length bytes.
            if response.fp is None:
                break
            remaining = 20 - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError('HTTP total20sec deadline exceeded')
            # Reapply the remaining deadline to each socket read, including slow streams.
            response.fp.raw._sock.settimeout(remaining)
            block = response.read1(min(65536, cap + 1 - len(data)))
            if not block:
                break
            data.extend(block)
        if not 1 <= len(data) <= cap or (length is not None and len(data) != int(length)):
            raise ValueError('Response body byte cap/length differs')
    if time.monotonic() - started > 20:
        raise TimeoutError('HTTP total20sec deadline exceeded')
    return bytes(data)


def confirm_page(row, body):
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(body, 'html.parser')
    description = soup.select_one('#soundDescriptionSection')
    heading = soup.select_one('h1')
    licenses = {a.get('href') for a in soup.select('a[href]') if 'creativecommons.org' in a.get('href', '')}
    previews = {t.get('data-mp3') for t in soup.select('[data-mp3]')}
    if (description is None or description.get_text(' ', strip=True) != row['description'] or
            heading is None or heading.get_text(' ', strip=True) != row['title'] or
            licenses != {row['current_license_url']} or previews != {row['preview_url']} or
            description.select('a[href]')):
        raise ValueError('Current official description/title/CC0/preview/lineage differs')
    return dict(verified_at_utc=datetime.now(timezone.utc).isoformat(), page_url=row['page_url'],
        current_license_url=row['current_license_url'], description=row['description'],
        preview_url=row['preview_url'], html_bytes=len(body), html_sha256=hashlib.sha256(body).hexdigest(),
        description_links=[], age_verified=False, naturalness_verified=False)


def reuse_audio(row, records):
    path = AUDIO_ROOT / (row['id'] + '.mp3')
    safe_path(path)
    matches = [r for r in records if r.get('id') == row['id']]
    if not path.exists():
        if matches:
            raise ValueError('Recorded acquired audio is missing')
        return None
    if (len(matches) != 1 or path.is_symlink() or not 1 <= path.stat().st_size <= CAP):
        raise ValueError('Unrecorded/duplicate/invalid audio cannot be reused or overwritten')
    old = matches[0]
    if (any(old.get(k) != v for k, v in row.items()) or old.get('bytes') != path.stat().st_size or
            old.get('sha256') != digest(path)):
        raise ValueError('Reused source/URL/audio bytes/SHA differ')
    return dict(old, reused_existing_copy=True)


def acquire(row, records):
    previous = reuse_audio(row, records)
    if previous is not None:
        return previous
    data = fetch(row['preview_url'])
    path = AUDIO_ROOT / (row['id'] + '.mp3')
    with path.open('xb') as stream:
        stream.write(data)
    return dict(row, bytes=len(data), sha256=digest(path), reused_existing_copy=False,
        lossy_preview_not_original_aiff_wav=True)


def stats(samples):
    if not samples:
        raise ValueError('Empty PCM block')
    count = len(samples)
    return dict(samples=count, rms=math.sqrt(math.fsum(float(v)*v for v in samples)/count)/32768,
        peak=max(abs(v) for v in samples)/32768, dc=math.fsum(samples)/count/32768,
        rail_clipping_samples=sum(v in (-32768, 32767) for v in samples),
        rail_clipping_fraction=sum(v in (-32768, 32767) for v in samples)/count)


def level_report(pcm_bytes):
    if not 2 <= len(pcm_bytes) <= MAX_SECONDS*RATE*2 or len(pcm_bytes) % 2:
        raise ValueError('PCM size/duration/format invalid')
    pcm = array('h')
    pcm.frombytes(pcm_bytes)
    if sys.byteorder != 'little':
        pcm.byteswap()
    try:
        return dict(actual_samples=len(pcm), actual_duration_seconds=len(pcm)/RATE,
            decoded_format=dict(sample_rate=RATE, channels=1, encoding='s16le'),
            full=stats(pcm), bins=[dict(start_sample=start, end_sample=min(start+RATE, len(pcm)),
                start_seconds=start/RATE, end_seconds=min(start+RATE, len(pcm))/RATE,
                **stats(pcm[start:start+RATE])) for start in range(0, len(pcm), RATE)],
            acoustic_levels_only=True, event_ground_truth=False, audio_review_completed=False)
    finally:
        for index in range(len(pcm)):
            pcm[index] = 0


def decode(row):
    path = AUDIO_ROOT / (row['id'] + '.mp3')
    if digest(path) != row['sha256']:
        raise ValueError('Audio changed before decode')
    meta = subprocess.run([str(FFPROBE), '-v', 'error', '-protocol_whitelist', 'file,pipe',
        '-format_whitelist', 'mp3', '-show_format', '-show_streams', '-of', 'json', str(path)],
        capture_output=True, timeout=20)
    if meta.returncode or len(meta.stdout + meta.stderr) > 256*1024:
        raise ValueError('Bounded MP3 format inspection failed')
    metadata = json.loads(meta.stdout)
    streams = metadata.get('streams', [])
    fmt = metadata.get('format', {})
    duration = float(fmt.get('duration', 'nan'))
    if (fmt.get('format_name') != 'mp3' or len(streams) != 1 or streams[0].get('codec_type') != 'audio' or
            streams[0].get('codec_name') != 'mp3' or not math.isfinite(duration) or not 0 < duration <= MAX_SECONDS):
        raise ValueError('Require one whole MP3 audio stream within120sec')
    decoded = subprocess.run([str(FFMPEG), '-v', 'error', '-nostdin', '-threads', '1',
        '-protocol_whitelist', 'file,pipe', '-format_whitelist', 'mp3', '-i', str(path),
        '-map', '0:a:0', '-vn', '-t', str(MAX_SECONDS), '-ac', '1', '-ar', str(RATE),
        '-f', 's16le', 'pipe:1'], capture_output=True, timeout=20)
    if decoded.returncode or len(decoded.stderr) > 64*1024:
        raise ValueError('Memory-only MP3 decode failed')
    report = level_report(decoded.stdout)
    if abs(report['actual_duration_seconds'] - duration) > .25 or digest(path) != row['sha256']:
        raise ValueError('Whole audio duration/hash changed or incomplete decode')
    audio_format = dict(codec_name='mp3', sample_rate=int(streams[0]['sample_rate']),
        channels=streams[0]['channels'], format_duration_seconds=duration,
        format_size_bytes=int(fmt['size']), bit_rate=int(fmt['bit_rate']))
    return dict(row, acquired_format=audio_format, **report)


def validate_levels(case):
    count = case.get('actual_samples')
    if (type(count) is not int or not 1 <= count <= MAX_SECONDS*RATE or
            case.get('actual_duration_seconds') != count/RATE or
            case.get('decoded_format') != dict(sample_rate=RATE, channels=1, encoding='s16le') or
            case.get('acoustic_levels_only') is not True):
        raise ValueError('Incomplete decoded duration/format')
    bins = case.get('bins')
    if not isinstance(bins, list) or len(bins) != math.ceil(count/RATE):
        raise ValueError('Incomplete one-second bins')
    for index, block in enumerate(bins):
        start, end = index*RATE, min((index+1)*RATE, count)
        if any(block.get(k) != v for k, v in dict(start_sample=start, end_sample=end,
                start_seconds=start/RATE, end_seconds=end/RATE, samples=end-start).items()):
            raise ValueError('Bin coverage/sample count differs')
    for block in [case.get('full'), *bins]:
        if not isinstance(block, dict) or type(block.get('samples')) is not int or block['samples'] < 1:
            raise ValueError('Missing acoustic sample count')
        for key in ('rms', 'peak', 'dc', 'rail_clipping_fraction'):
            v = block.get(key)
            if type(v) not in (int, float) or not math.isfinite(v) or not (-1 <= v <= 1 if key == 'dc' else 0 <= v <= 1):
                raise ValueError('Missing/nonfinite acoustic statistic')
        clipping = block.get('rail_clipping_samples')
        if type(clipping) is not int or not 0 <= clipping <= block['samples'] or block['rail_clipping_fraction'] != clipping/block['samples']:
            raise ValueError('Invalid rail clipping count/fraction')
        if abs(block['dc']) > block['rms'] + 1e-12 or block['rms'] > block['peak'] + 1e-12:
            raise ValueError('Inconsistent level inequalities')
    if case['full']['samples'] != count or sum(b['rail_clipping_samples'] for b in bins) != case['full']['rail_clipping_samples']:
        raise ValueError('Whole/bin counts differ')
    if abs(math.fsum(b['dc']*b['samples'] for b in bins)/count - case['full']['dc']) > 1e-12 or abs(
            math.fsum(b['rms']**2*b['samples'] for b in bins)/count - case['full']['rms']**2) > 1e-12 or max(b['peak'] for b in bins) != case['full']['peak']:
        raise ValueError('Whole/bin acoustic aggregate differs')


def validate_completion(done, pins):
    if (done.get('status') != 'completed' or done.get('requested_files') != 2 or done.get('decoded_files') != 2 or
            done.get('pins') != pins or any(done.get(flag) is not False for flag in FALSE_FLAGS) or
            done.get('listening_api_available') is not False):
        raise ValueError('Incomplete/unsafe completion flags or pins')
    cases = done.get('cases')
    if not isinstance(cases, list) or len(cases) != 2:
        raise ValueError('Missing exact cases')
    for row, case in zip(sources(), cases):
        if any(case.get(k) != v for k, v in row.items()):
            raise ValueError('Completion source/training boundary differs')
        path = AUDIO_ROOT / (row['id'] + '.mp3')
        if (type(case.get('bytes')) is not int or not 1 <= case['bytes'] <= CAP or
                path.stat().st_size != case['bytes'] or digest(path) != case.get('sha256') or
                type(case.get('reused_existing_copy')) is not bool):
            raise ValueError('Completion audio bytes/hash missing or changed')
        page = case.get('current_page_evidence')
        if (not isinstance(page, dict) or any(page.get(k) != row[k] for k in
                ('page_url', 'preview_url', 'description', 'current_license_url')) or
                type(page.get('html_bytes')) is not int or not 1 <= page['html_bytes'] <= PAGE_CAP or
                not re.fullmatch(r'[a-f0-9]{64}', page.get('html_sha256', '')) or
                not page.get('verified_at_utc') or page.get('description_links') != [] or
                page.get('age_verified') is not False or page.get('naturalness_verified') is not False):
            raise ValueError('Missing official current rights/page evidence')
        fmt = case.get('acquired_format')
        if (not isinstance(fmt, dict) or fmt.get('codec_name') != 'mp3' or
                type(fmt.get('sample_rate')) is not int or fmt['sample_rate'] not in (16000, 22050, 24000, 32000, 44100, 48000) or
                type(fmt.get('channels')) is not int or not 1 <= fmt['channels'] <= 2 or
                fmt.get('format_size_bytes') != case['bytes'] or
                type(fmt.get('bit_rate')) is not int or fmt['bit_rate'] < 1 or
                type(fmt.get('format_duration_seconds')) not in (int, float) or
                not math.isfinite(fmt['format_duration_seconds']) or not 0 < fmt['format_duration_seconds'] <= MAX_SECONDS):
            raise ValueError('Missing/invalid acquired MP3 format')
        validate_levels(case)
        if abs(fmt['format_duration_seconds'] - case['actual_duration_seconds']) > .25:
            raise ValueError('Full decode duration differs')
    if sorted(p.name for p in AUDIO_ROOT.glob('*.mp3')) != ['244237.mp3', '9437.mp3']:
        raise ValueError('Require exactly one MP3 copy per source')
    verify_pins(pins)


def worker(args):
    stage = 'launch'
    identity = None
    requests = []
    try:
        launch = load(args.output / 'launch.json')
        if os.environ.get('AGATTOK_REVIEW_WORKER_TOKEN') != launch['token']:
            raise ValueError('Worker must originate from this fresh bounded launch')
        pins = launch['pins']
        verify_pins(pins)
        AUDIO_ROOT.mkdir(exist_ok=True)
        previous = load(AUDIO_ROOT / 'acquisition.json') if (AUDIO_ROOT / 'acquisition.json').exists() else dict(source_sha256=SOURCE_SHA, files=[])
        if previous.get('source_sha256') != SOURCE_SHA or not isinstance(previous.get('files'), list):
            raise ValueError('Previous audio manifest/source pin differs')
        records = previous['files']
        ids = [record.get('id') for record in records if isinstance(record, dict)]
        if (len(ids) != len(records) or len(ids) != len(set(ids)) or not set(ids) <= {'9437', '244237'} or
                not {p.name for p in AUDIO_ROOT.glob('*.mp3')} <= {'9437.mp3', '244237.mp3'}):
            raise ValueError('Unexpected/duplicate acquired source records or MP3 files')
        cases = []
        for row in sources():
            identity = row['id']
            stage = 'reconfirm_page'
            requests.append(dict(id=identity, url=row['page_url'], kind='official_html', status='started'))
            write(args.output / 'requests.json', requests)
            body = fetch(row['page_url'], page=True)
            page = confirm_page(row, body)
            (args.output / (identity + '-official.html')).write_bytes(body)
            requests[-1].update(status='completed', bytes=len(body), sha256=page['html_sha256'])
            write(args.output / 'requests.json', requests)
            stage = 'acquire'
            reuse = reuse_audio(row, records)
            if reuse is None:
                requests.append(dict(id=identity, url=row['preview_url'], kind='exact_mp3', status='started'))
                write(args.output / 'requests.json', requests)
            record = reuse if reuse is not None else acquire(row, records)
            record['current_page_evidence'] = page
            records = [r for r in records if r.get('id') != identity] + [record]
            write(AUDIO_ROOT / 'acquisition.json', dict(source_sha256=SOURCE_SHA, files=records,
                status='complete' if len(records) == 2 else 'partial', training=False, approved_training_label=None))
            if reuse is None:
                requests[-1].update(status='completed', bytes=record['bytes'], sha256=record['sha256'])
                write(args.output / 'requests.json', requests)
            stage = 'decode_levels'
            cases.append(decode(record))
            write(args.output / 'progress.json', dict(status='partial', cases=cases, pins=pins,
                **{flag: False for flag in FALSE_FLAGS}))
        stage = 'completion'
        done = dict(status='completed', requested_files=2, decoded_files=2, cases=cases,
            pins=verify_pins(pins), listening_api_available=False, **{flag: False for flag in FALSE_FLAGS})
        validate_completion(done, pins)
        write(args.output / 'levels.json', done)
    except Exception as error:
        if requests and requests[-1]['status'] == 'started':
            requests[-1].update(status='failed', error_type=type(error).__name__, error=str(error))
            write(args.output / 'requests.json', requests)
        write(args.output / 'failure.json', dict(status='failed', stage=stage, id=identity,
            error_type=type(error).__name__, error=str(error), requests=requests,
            **{flag: False for flag in FALSE_FLAGS}))
        raise


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--ffmpeg', type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args(argv)
    args.output = safe_path(args.output, run=True)
    sources()
    if args.worker:
        if not os.environ.get('AGATTOK_REVIEW_WORKER_TOKEN') or not (args.output / 'launch.json').is_file():
            raise ValueError('Direct worker invocation prohibited before acquisition')
        verify_ffmpeg(args.ffmpeg)
        worker(args)
        return 0
    if args.output.exists():
        raise ValueError('Require a fresh run; preserved audio is reused in place')
    verify_ffmpeg(args.ffmpeg)
    pins = capture_pins()
    verify_pins(pins)
    args.output.mkdir(parents=True, exist_ok=False)
    write(args.output / 'storage-start.json', storage_snapshot(EVIDENCE_ROOT))
    write(args.output / 'launch.json', dict(token=uuid.uuid4().hex, pins=pins,
        output=str(args.output), http_timeout_seconds=20, max_mp3_bytes=CAP,
        time_limit_seconds=180, memory_limit_bytes=512*1024**2, log_limit_bytes=1024**2))
    launch = load(args.output / 'launch.json')
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', AGATTOK_REVIEW_WORKER_TOKEN=launch['token'])
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    try:
        result = run_bounded([sys.executable, '-B', str(Path(__file__).resolve()), '--worker',
            '--output', str(args.output), '--ffmpeg', str(args.ffmpeg)], cwd=ROOT, env=env,
            log_path=args.output / 'run.log', timeout_seconds=180,
            memory_limit_bytes=512*1024**2, max_output_bytes=1024**2)
        write(args.output / 'process.json', asdict(result))
        if (result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes
                or result.output_truncated or result.memory_limit_hit):
            raise RuntimeError('Bounded acquisition failed; preserve and inspect partial evidence')
        done = load(args.output / 'levels.json')
        validate_completion(done, pins)
        for case in done['cases']:
            if digest(args.output / (case['id'] + '-official.html')) != case['current_page_evidence']['html_sha256']:
                raise ValueError('Current official HTML evidence changed')
        write(args.output / 'validated-completion.json', dict(status='completed', pins=pins,
            levels_sha256=digest(args.output / 'levels.json'), process_sha256=digest(args.output / 'process.json'),
            requested_files=2, decoded_files=2, **{flag: False for flag in FALSE_FLAGS}))
    except Exception as error:
        write(args.output / 'parent-failure.json', dict(status='failed', error_type=type(error).__name__,
            error=str(error), **{flag: False for flag in FALSE_FLAGS}))
        raise
    finally:
        write(args.output / 'storage-end.json', storage_snapshot(EVIDENCE_ROOT))
    print(json.dumps(dict(status='completed', decoded_files=2, source_sha256=SOURCE_SHA,
        audio_review_completed=False, training=False, product_changed=False)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
