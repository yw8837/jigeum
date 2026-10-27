"""Check the real invitation flow on an owned emulator without logging a code.

No Play purchase, ad click, private baby media, or entitlement bypass is used.
"""
import argparse
import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--code-file', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Only an owned emulator is allowed.')
    code = re.search(r'AGATTOK-FRIENDS-[A-Za-z0-9_-]{32}', args.code_file.read_text(encoding='utf-8')).group(0)
    args.evidence.mkdir(parents=True, exist_ok=True)
    package = 'com.moa.babylanguage'
    remote = '/sdcard/agattok-access-code-smoke.xml'
    result = dict(status='running', real_server=True, real_play_purchase=False,
                  real_discount_codes_consumed=0, secret_written_to_evidence=False)

    def adb(*items):
        proc = subprocess.run([str(args.adb), '-s', args.serial, *map(str, items)], capture_output=True, timeout=45)
        if proc.returncode:
            raise RuntimeError('ADB operation failed; secret-bearing arguments are intentionally omitted.')
        return proc.stdout

    def nodes():
        adb('shell', 'uiautomator', 'dump', remote)
        raw = adb('exec-out', 'cat', remote)
        return list(ET.fromstring(raw).iter('node'))

    def find(text, attempts=18, scroll=False):
        for _ in range(attempts):
            found = next((n for n in nodes() if text in n.get('text', '')), None)
            if found is not None:
                return found
            if scroll:
                adb('shell', 'input', 'swipe', '530', '1550', '530', '500', '350')
            time.sleep(.3)
        raise AssertionError('Expected UI not shown: ' + text)

    def tap_node(node):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
        adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)

    def tap(text, scroll=False):
        tap_node(find(text, scroll=scroll))

    def launch():
        adb('shell', 'am', 'force-stop', package)
        adb('shell', 'am', 'start', '-W', '-n', package+'/.MainActivity')
        find('아기말 번역하기')

    def screenshot(name):
        (args.evidence/name).write_bytes(adb('exec-out', 'screencap', '-p'))

    try:
        package_info = adb('shell', 'dumpsys', 'package', package).decode('utf-8', 'replace')
        assert 'versionCode=42 ' in package_info and 'versionName=0.5.28' in package_info
        result['version_code'] = 42
        launch()
        find('무료 · 1분 촬영 · 10초 분석')
        tap('전체 이용권')
        tap('이용 코드 입력', scroll=True)
        find('이용 코드 등록', scroll=True)
        input_node = next(n for n in nodes() if n.get('class') == 'android.widget.EditText')
        screenshot('code-entry-empty.png')
        tap_node(input_node)
        # Deliberately bypass logging wrappers for the secret-bearing input command.
        adb('shell', 'input', 'text', code)
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        tap('이용 코드 등록', scroll=True)
        find('초대 이용권 · 기간 제한 없이 무료')
        result['invitation_accepted'] = True
        screenshot('invitation-active.png')
        launch()
        find('전체 이용권 · 2분 촬영 · 20초 분석')
        result['restart_retained'] = True
        screenshot('invitation-home.png')
        tap('내 이용권')
        tap('이 기기에서 코드 해제', scroll=True)
        launch()
        find('무료 · 1분 촬영 · 10초 분석')
        result['local_removal_relocks'] = True
        screenshot('code-removed-free.png')
        result['status'] = 'pass'
    except Exception as error:
        result['status'] = 'fail'
        result['error'] = str(error).replace(code, '[REDACTED]')
        raise RuntimeError(result['error']) from None
    finally:
        adb('shell', 'rm', '-f', remote)
        (args.evidence/'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
