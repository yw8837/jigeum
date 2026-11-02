"""Verify a signed version increase preserves an actual locally saved MP4 and legacy text.

Use an exclusively owned QA device and a rights-cleared local fixture. This does
not upload media, clear app data, establish intent accuracy, or test Play delivery.
"""
import argparse
import json
import time
from pathlib import Path

import qa_harness as q
import capture_ui as flow
import preview_ui as delivery
from closed_test_ui import download_media, verify_saved_record


def verify_install(install, code, name):
    assert install['verified'] and install['package'] == 'com.moa.babylanguage'
    assert install['version_code'] == code and install['version_name'] == name


def verify_legacy_record(backup, seed):
    assert backup['format'] == 'jigeum-observations' and backup['version'] == 2
    matches = [row for row in backup['records'] if row['id'] == seed['id']]
    assert len(matches) == 1, 'This run record id is missing or ambiguous'
    row = matches[0]
    assert all(row[key] == value for key, value in seed.items()), 'This run record changed'
    assert row['basis'] == 'LEGACY', 'This exact record lost its legacy basis'
    return row


def export_backup(owned):
    name = 'agattok-upgrade-export-' + str(time.time_ns()) + '.json'
    remote = '/sdcard/Download/' + name
    assert q.shell('test', '-e', remote, check=False).returncode != 0
    owned.append(remote)
    flow.launch(); q.tap_text('돌봄'); q.tap_text('전체 기록 백업하기')
    q.wait_ui('SAVE')
    entries = q.nodes()
    roots = next((n for n in entries if n['desc'] == 'Show roots'), None)
    if roots:
        q.tap(*roots['center'])
        downloads = [n for n in q.wait_ui('Downloads') if n['text'] == 'Downloads']
        q.tap(*downloads[-1]['center'])
    field = next(n for n in q.nodes() if n['text'].startswith('아기말-백업-'))
    q.tap(*field['center']); q.shell('input', 'keycombination', '113', '29')
    q.shell('input', 'text', name); q.shell('input', 'keyevent', 'KEYCODE_BACK')
    q.tap_text('SAVE', scroll=False); q.wait_ui('기록 백업을 저장했어요', seconds=20)
    return json.loads(q.adb('exec-out', 'cat', remote).stdout)


def finish_status(result, cleanup_error=None):
    if cleanup_error is not None:
        result.update(status='fail', cleanup_error=str(cleanup_error))
    elif result.get('status') != 'fail':
        result['status'] = 'pass' if result.get('checks_passed') else 'fail'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--old-apk', type=Path, required=True)
    parser.add_argument('--new-apk', type=Path, required=True)
    parser.add_argument('--cry-video', type=Path, required=True)
    parser.add_argument('--old-version-code', type=int, required=True)
    parser.add_argument('--old-version-name', required=True)
    parser.add_argument('--new-version-code', type=int, required=True)
    parser.add_argument('--new-version-name', required=True)
    parser.add_argument('--new-distribution', choices=['beta', 'release'], default='beta')
    args = parser.parse_args()
    assert args.new_version_code > args.old_version_code
    for file in (args.old_apk, args.new_apk, args.cry_video):
        if not file.is_file():
            raise RuntimeError('Missing owned local fixture or APK: ' + str(file))
    run_id = str(time.time_ns())
    remote_seed = '/sdcard/Download/agattok-upgrade-' + run_id + '.json'
    remote_video = '/sdcard/Download/agattok-upgrade-' + run_id + '.mp4'
    owned = [remote_seed, remote_video]
    for remote in owned:
        assert q.shell('test', '-e', remote, check=False).returncode != 0
    phrase = '이전 버전의 검사 기록 ' + run_id
    seed = dict(format='jigeum-observations', version=1, ageBand='', records=[dict(
        id=int(time.time()*1000), sound='옹알이', context='QA', note='Synthetic signed upgrade fixture',
        phrase=phrase, source='VIDEO_FILE', modelCue='')])
    local = q.RUN / 'upgrade-fixture.json'
    local.write_text(json.dumps(seed, ensure_ascii=False), encoding='utf-8')
    result = dict(status='running', installs=[], actual_play_install_tested=False,
                  app_data_cleared=False, infant_intent_accuracy_tested=False,
                  seed_record_id=seed['records'][0]['id'])
    try:
        result['installs'].append(delivery.install(args.old_apk))
        verify_install(result['installs'][-1], args.old_version_code, args.old_version_name)
        q.adb('push', str(local), remote_seed)
        q.adb('push', str(args.cry_video), remote_video)
        flow.launch(); q.tap_text('돌봄'); q.tap_text('백업 파일 가져오기')
        delivery.choose_file(Path(remote_seed).name)
        q.wait_ui('기록을 합칠까요?'); q.tap_text('기록 합치기')
        q.wait_ui('백업의 기록을 합쳤어요')
        result['old_legacy_record'] = verify_legacy_record(export_backup(owned), seed['records'][0])
        flow.launch(); q.tap_text('기록'); verify_saved_record(phrase, 'baseline-legacy-record')
        delivery.import_video(Path(remote_video).name)
        q.tap_text('영상 + 소리로 번역하기' if args.old_version_name.endswith('-beta') else '이 구간 소리로 번역하기')
        q.wait_ui('장면과 함께 저장하기', seconds=65)
        q.tap_text('장면과 함께 저장하기')
        # Since code39, saving keeps the result open until the explicit scene action.
        q.wait_ui('기록 저장 완료', seconds=45)
        # Receipt insertion can move this button while bringIntoView settles.
        # Require stable observed bounds before tapping the navigation action.
        previous_center = None
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            entries = q.nodes()
            button = next((n for n in entries if n['text'] == '저장한 장면 보기' and n['enabled']), None)
            if button and button['center'] == previous_center:
                q.tap(*button['center'])
                break
            previous_center = button['center'] if button else None
        else:
            raise AssertionError('Saved-scene action did not settle after completed save')
        q.wait_ui('그때의 아기말')
        result['old_media'] = download_media(owned, 'before-upgrade')
        result['installs'].append(delivery.install(args.new_apk))
        verify_install(result['installs'][-1], args.new_version_code, args.new_version_name)
        expected_home = ('전체 기능 테스트 · 2분 촬영 · 20초 분석' if args.new_distribution == 'beta'
                         else '무료 · 1분 촬영 · 10초 분석')
        flow.launch(); q.wait_ui(expected_home)
        q.tap_text('기록'); q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('그때의 아기말')
        result['new_media'] = download_media(owned, 'after-upgrade', result['old_media']['name'])
        assert result['old_media']['sha256'] == result['new_media']['sha256']
        result['new_legacy_record'] = verify_legacy_record(export_backup(owned), seed['records'][0])
        flow.launch(); q.tap_text('기록'); verify_saved_record(phrase, 'upgraded-legacy-record')
        result.update(checks_passed=True, legacy_text_preserved=True, media_bytes_preserved=True,
                      beta_full_access_retained=args.new_distribution == 'beta',
                      release_free_limits_restored=args.new_distribution == 'release')
    except Exception as error:
        result.update(status='fail', error=str(error))
        try:
            q.screenshot('upgrade-failure'); q.trace('upgrade-failure')
        except Exception as evidence_error:
            result['diagnostic_error'] = str(evidence_error)
        raise
    finally:
        cleanup_error = None
        try:
            q.shell('am', 'force-stop', q.PACKAGE)
            q.shell('rm', '-f', *owned)
            for name in ('before-upgrade-media.mp4', 'after-upgrade-media.mp4'):
                (q.RUN / name).unlink(missing_ok=True)
            remaining = [p for p in owned if q.shell('test', '-e', p, check=False).returncode == 0]
            result['owned_external_files_removed'] = not remaining
            assert not remaining, 'Owned upgrade fixtures survived cleanup'
        except Exception as error:
            cleanup_error = error
        finish_status(result, cleanup_error)
        (q.RUN / 'signed-media-upgrade-result.json').write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        if cleanup_error is not None:
            raise cleanup_error
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
