"""Real signed APK schema upgrade on an explicitly owned, empty QA device.

Imports a synthetic v1 backup in the older app, updates without clearing data,
then checks the retained phrase and unknown historical evidence label. No Play
split-install or physical-device claim is made.
"""
import argparse
import json
import time
from pathlib import Path
import qa_harness as q
import capture_ui as flow
import preview_ui as delivery


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--old-apk', type=Path, required=True)
    parser.add_argument('--new-apk', type=Path, required=True)
    args = parser.parse_args()
    seed = dict(format='jigeum-observations', version=1, ageBand='', records=[dict(
        id=int(time.time()*1000), sound='옹알이', context='QA', note='Owned schema upgrade fixture',
        phrase='업데이트 전부터 간직한 아기말', source='VIDEO_FILE', modelCue='')])
    local = q.RUN/'upgrade-fixture.json'
    local.write_text(json.dumps(seed, ensure_ascii=False), encoding='utf-8')
    remote = '/sdcard/Download/agattok-schema-upgrade.json'
    result = dict(status='running', installs=[], actual_play_install_tested=False)
    try:
        result['installs'].append(delivery.install(args.old_apk))
        q.adb('push', str(local), remote)
        flow.launch(); q.tap_text('돌봄'); q.tap_text('백업 파일 가져오기')
        delivery.choose_file(Path(remote).name); q.wait_ui('기록을 합칠까요?'); q.tap_text('기록 합치기')
        q.wait_ui('백업의 기록을 합쳤어요')
        flow.launch(); q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        q.screenshot('before-upgrade')
        result['installs'].append(delivery.install(args.new_apk))
        assert result['installs'][1]['version_code'] > result['installs'][0]['version_code']
        flow.launch(); q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        q.wait_ui('이전에 남긴 아기말 · 분석 근거 미기록')
        q.screenshot('after-upgrade'); q.trace('after-upgrade')
        result.update(status='pass', phrase_preserved=True, legacy_basis=True, app_data_cleared=False)
    except Exception as error:
        result.update(status='fail', error=str(error)); raise
    finally:
        q.shell('rm', '-f', remote, check=False)
        (q.RUN/'record-upgrade-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
