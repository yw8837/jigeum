"""Capture the current signed release UI on an explicitly owned QA emulator.

The fixture is licensed local QA audio inside a plain-color video. No personal
media, database injection, entitlement override, or preview distribution.
Screens show actual UI, not evidence of infant intent accuracy.
"""
import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "outputs/QA"))
import qa_harness as q
import capture_ui as flow
from preview_ui import import_video
from instrument import installed_provenance
from qa_config import adb_command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--clear-owned-emulator', action='store_true', required=True,
                        help='Explicit reset of synthetic data on this dedicated QA emulator.')
    args = parser.parse_args()
    adb = adb_command()
    if not adb[-1].startswith('emulator-'):
        parser.error('This fixture reset is restricted to a dedicated emulator.')
    for path in (args.apk, args.fixture):
        if not path.is_file(): parser.error(f'Missing local input: {path}')
    q.adb('install', '-r', str(args.apk), timeout=120)
    proof = installed_provenance(adb, q.PACKAGE, hashlib.sha256(args.apk.read_bytes()).hexdigest())
    if not proof['verified'] or proof['version_name'].endswith('-test'):
        raise RuntimeError('Store screenshots require the identified release APK, never preview.')
    display = q.display_settings()
    font = q.shell('settings', 'get', 'system', 'font_scale').stdout.decode().strip()
    fixture_name = 'agattok-store-source.mp4'
    result = {'status': 'running', 'provenance': proof, 'screens': [],
              'fixture_sha256': hashlib.sha256(args.fixture.read_bytes()).hexdigest(),
              'actual_infant_intent_accuracy_tested': False}
    try:
        q.shell('pm', 'clear', q.PACKAGE)
        q.shell('wm', 'size', '1080x2400')
        q.shell('wm', 'density', '420')
        q.shell('settings', 'put', 'system', 'font_scale', '1.0')
        q.adb('push', str(args.fixture), '/sdcard/Download/' + fixture_name)
        flow.launch()
        q.screenshot('store-01-home'); result['screens'].append('store-01-home.png')
        import_video(fixture_name)
        q.screenshot('store-02-range'); result['screens'].append('store-02-range.png')
        q.tap_text('이 구간 소리로 번역하기')
        q.wait_ui('장면과 함께 저장하기', seconds=65)
        # A small scroll keeps the entire generated phrase visible.
        q.shell('input', 'swipe', '540', '1600', '540', '1200', '360')
        q.screenshot('store-03-expression'); result['screens'].append('store-03-expression.png')
        q.trace('store-actual-model-expression')
        q.tap_text('장면과 함께 저장하기'); q.wait_ui('기록 저장 완료', seconds=45)
        flow.launch(); q.tap_text('기록'); q.wait_ui('우리가 나눈 아기말')
        deadline = time.monotonic() + 12
        while any('우리 아이의 한마디를 남겼어요' in n['text'] for n in q.nodes()):
            if time.monotonic() >= deadline:
                raise RuntimeError('Transient save notice did not disappear before store capture')
            time.sleep(.25)
        q.screenshot('store-04-record'); result['screens'].append('store-04-record.png')
        flow.launch()
        q.wait_ui('우리 아이 자랑 · 영상카드 함께 나누기')
        q.screenshot('store-06-community'); result['screens'].append('store-06-community.png')
        result['status'] = 'pass'
    except Exception as error:
        result['status'] = 'fail'; result['error'] = str(error)
        q.screenshot('store-capture-failure')
        raise
    finally:
        q.restore_display(display)
        if font == 'null': q.shell('settings', 'delete', 'system', 'font_scale')
        else: q.shell('settings', 'put', 'system', 'font_scale', font)
        q.shell('rm', '-f', '/sdcard/Download/' + fixture_name)
        (q.RUN / 'store-capture-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
