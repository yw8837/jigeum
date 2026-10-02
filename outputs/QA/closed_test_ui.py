"""Final signed Play-beta access and production relock on an owned QA device.

The public CC0 cry fixture stays local and is never bundled or uploaded. This
checks runnable access, not infant intent accuracy or actual Play split installs.
"""
import argparse
import hashlib
import json
import re
import shutil
import time
from pathlib import Path
import qa_harness as q
import capture_ui as flow
import preview_ui as delivery


def verify_capture_timeline():
    for _ in range(8):
        for node in q.nodes():
            match = re.fullmatch(r'(\d+):(\d{2})\.(\d)', node['text'])
            if match:
                duration = int(match[1])*60_000 + int(match[2])*1_000 + int(match[3])*100
                if 119_000 <= duration <= 120_500:
                    return dict(end_label=node['text'], lower_ms=duration, precision_ms=100)
        q.swipe_up()
    raise AssertionError('Full timeline does not show a real 119–120.5-second clip')


def download_media(owned, label, expected_name=None):
    q.tap_text('번역 영상 내려받기', attempts=12)
    entries = q.nodes()
    name = next(n['text'] for n in entries if re.fullmatch(r'아가톡-\d+\.mp4', n['text']))
    if expected_name is not None:
        assert name == expected_name, 'Release viewer is not the exact beta-created record'
    remote = '/sdcard/Download/' + name
    assert q.shell('test', '-e', remote, check=False).returncode != 0, 'Refuse to replace an existing QA document'
    owned.append(remote)
    roots = next((n for n in entries if n['desc'] == 'Show roots'), None)
    if roots:
        q.tap(*roots['center'])
        downloads = [n for n in q.wait_ui('Downloads') if n['text'] == 'Downloads']
        q.tap(*downloads[-1]['center'])
    save = next(n for n in q.nodes() if n['text'].lower() == 'save' and n['enabled'])
    q.tap(*save['center'])
    q.wait_ui('그때의 아기말', seconds=30)
    for _ in range(12):
        if any('번역 영상을 선택한 위치에 내려받았어요' in node['text'] for node in q.nodes()):
            break
        q.swipe_up()
    else:
        raise AssertionError('Saved-file completion notice not reachable after scrolling')
    downloaded = q.RUN / (label + '-media.mp4')
    q.adb('pull', remote, str(downloaded))
    metadata = json.loads(q.cmd(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(downloaded)]).stdout)
    assert {s['codec_type'] for s in metadata['streams']} >= {'audio', 'video'}
    assert 1 <= float(metadata['format']['duration']) <= 20.15
    proof = dict(name=name, bytes=downloaded.stat().st_size, sha256=hashlib.sha256(downloaded.read_bytes()).hexdigest(),
                 duration=metadata['format']['duration'], streams=[s['codec_type'] for s in metadata['streams']])
    q.screenshot(label+'-media-downloaded')
    q.shell('rm', '-f', remote)
    downloaded.unlink()
    return proof


def select_twenty_seconds():
    q.tap_text('0.5초씩 맞추기', attempts=12)
    for _ in range(20):
        for attempt in range(12):
            target = next((node for node in q.nodes() if node['desc'] == '끝 0.5초 뒤로'), None)
            if target:
                assert target['enabled'], '20-second beta range extension disabled'
                q.tap(*target['center'])
                break
            q.swipe_up()
        else:
            raise AssertionError('End-range fine control not reachable')
    q.wait_ui('0:00.0 – 0:20.0 · 20초 선택')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--beta-apk', type=Path, required=True)
    parser.add_argument('--release-apk', type=Path, required=True)
    parser.add_argument('--cry-video', type=Path, required=True)
    parser.add_argument('--access-proof', type=Path,
                        help='Resume only the remaining media/PDF/relock scenarios after this exact APK passed the first four scenarios.')
    args = parser.parse_args()
    for path in (args.beta_apk, args.release_apk, args.cry_video):
        if not path.is_file(): raise RuntimeError('Missing owned local artifact: ' + str(path))
    display = q.display_settings()
    font = q.shell('settings', 'get', 'system', 'font_scale').stdout.decode().strip()
    result = dict(status='running', scenarios=[], installs=[], actual_play_install_tested=False,
                  infant_intent_accuracy_tested=False, fixture='the_yura CC0 cry + plain-color video')
    if args.access_proof:
        prior = json.loads(args.access_proof.read_text(encoding='utf-8'))
        assert prior['installs'][0]['sha256'] == hashlib.sha256(args.beta_apk.read_bytes()).hexdigest()
        assert prior['installs'][0]['verified'] and prior['installs'][0]['version_name'] == '0.5.8-beta'
        assert len(prior['scenarios']) == 4 and '200MiB+1 byte' in prior['scenarios'][3]
        assert 119_000 <= prior['capture_timeline']['lower_ms'] <= 120_500
        assert prior['large_fixture_bytes'] == 200*1024*1024 and prior['oversized_fixture_bytes'] == 200*1024*1024+1
        result['scenarios'] = list(prior['scenarios'])
        for key in ('observed_capture_seconds', 'capture_timeline', 'abstention_message', 'large_fixture_bytes', 'oversized_fixture_bytes'):
            result[key] = prior[key]
        result['resumed_access_proof'] = dict(path=str(args.access_proof.resolve()),
                                            sha256=hashlib.sha256(args.access_proof.read_bytes()).hexdigest())
    owned = ['/sdcard/Download/agattok-beta058-upgrade.json', '/sdcard/Download/agattok-beta058-cry.mp4',
             '/sdcard/Download/agattok-beta058-large.mp4', '/sdcard/Download/agattok-beta058-oversized.mp4']
    seed = dict(format='jigeum-observations', version=1, ageBand='', records=[dict(
        id=int(time.time()*1000), sound='옹알이', context='QA', note='Owned synthetic beta relock fixture',
        phrase='정식 업데이트 뒤에도 남는 테스트 기록', source='AUDIO', modelCue='')])
    seed_path = q.RUN / 'upgrade-fixture.json'
    seed_path.write_text(json.dumps(seed, ensure_ascii=False), encoding='utf-8')
    large = q.RUN / 'large-fixture.mp4'
    oversized = q.RUN / 'oversized-fixture.mp4'
    for target, target_size in ((large, 200*1024*1024), (oversized, 200*1024*1024+1)):
        shutil.copyfile(args.cry_video, target)
        original_size = target.stat().st_size
        assert original_size + 8 < target_size
        with target.open('r+b') as file:
            file.seek(0, 2)
            file.write((target_size-original_size).to_bytes(4, 'big') + b'free')
            file.truncate(target_size)
    try:
        result['installs'].append(delivery.install(args.beta_apk))
        assert result['installs'][-1]['version_name'].endswith('-beta')
        for permission in ('android.permission.CAMERA', 'android.permission.RECORD_AUDIO'):
            q.shell('pm', 'grant', q.PACKAGE, permission)
        for path, remote in ((seed_path, owned[0]), (args.cry_video, owned[1]), (large, owned[2]), (oversized, owned[3])):
            q.adb('push', str(path), remote, timeout=60)
        flow.launch(); q.tap_text('돌봄'); q.tap_text('백업 파일 가져오기')
        delivery.choose_file(Path(owned[0]).name); q.wait_ui('기록을 합칠까요?'); q.tap_text('기록 합치기')
        q.wait_ui('백업의 기록을 합쳤어요')
        layouts = () if args.access_proof else (('small','720x1280','320','1.0'), ('font200','1080x2400','420','2.0'))
        for label, size, density, scale in layouts:
            q.shell('wm','size',size); q.shell('wm','density',density); q.shell('settings','put','system','font_scale',scale)
            flow.launch(); q.wait_ui('전체 기능 테스트 · 2분 촬영 · 20초 분석'); q.screenshot(label+'-beta-home')
            q.tap_text('테스트 이용 안내'); q.wait_ui('이 설치본은 전체 기능 테스트용이에요.')
            collected = []
            for _ in range(12):
                texts = [n['text'] for n in q.nodes() if n['text']]
                collected.extend(texts)
                if any('정식 버전으로 업데이트하면' in text for text in texts): break
                q.swipe_up()
            else: raise AssertionError('Beta ending/relock explanation not reachable')
            assert '구매 복원' not in collected and 'Google Play에서 구독 관리' not in collected
            q.screenshot(label+'-beta-test-explanation'); q.trace(label+'-test-explanation')
            flow.launch(); q.tap_text('아기말 번역하기'); q.wait_ui('녹화 시작', seconds=20)
            q.wait_ui('최대 2분 담고, 20초까지 골라요.'); q.screenshot(label+'-beta-capture')
            result['scenarios'].append(label+': full access visible, purchase/restore hidden, explanation/capture reachable')
        q.restore_display(display); q.shell('settings','put','system','font_scale','1.0')
        if not args.access_proof:
            flow.launch(); q.tap_text('아기말 번역하기'); q.wait_ui('녹화 시작', seconds=20); q.tap_text('녹화 시작')
            begun = time.monotonic(); print('Signed beta real two-minute video started.', flush=True)
            q.wait_ui('어느 순간을 들어볼까요?', seconds=140)
            result['observed_capture_seconds'] = round(time.monotonic()-begun,2)
            assert result['observed_capture_seconds'] >= 118
            result['capture_timeline'] = verify_capture_timeline()
            select_twenty_seconds(); q.screenshot('beta-two-minute-twenty-second-selection')
            q.tap_text('영상 + 소리로 번역하기')
            deadline = time.monotonic()+65
            while time.monotonic() < deadline:
                messages = [n['text'] for n in q.nodes()]
                accepted = ('선택한 구간이 거의 무음이에요.', '선택한 구간이 너무 조용해요.', '이 구간에서는 아기 소리 단서를 충분히 찾지 못했어요.')
                abstention = next((message for message in messages if message.startswith(accepted)), None)
                if abstention:
                    result['abstention_message'] = abstention
                    break
            else:
                raise AssertionError('Twenty-second quiet analysis did not complete with a supported abstention')
            assert not any(n['text']=='장면과 함께 저장하기' for n in q.nodes())
            q.screenshot('beta-twenty-second-real-model-abstention')
            result['scenarios'].append('real signed beta: 120s capture, 20s actual analysis without a paywall, no invented silence phrase')
            delivery.import_video(Path(owned[2]).name)
            q.wait_ui('어느 순간을 들어볼까요?'); q.screenshot('beta-over-free-size-import')
            result['large_fixture_bytes'] = large.stat().st_size
            flow.launch(); q.tap_text('아기말 번역하기'); q.tap_text('저장된 영상 가져오기')
            delivery.choose_file(Path(owned[3]).name); q.wait_ui('200MB')
            assert not any(n['text']=='어느 순간을 들어볼까요?' for n in q.nodes())
            result['oversized_fixture_bytes'] = oversized.stat().st_size
            q.screenshot('beta-two-hundred-plus-one-byte-rejected')
            result['scenarios'].append('beta imports valid exact 200MiB video and rejects 200MiB+1 byte')
        delivery.import_video(Path(owned[1]).name)
        q.tap_text('영상 + 소리로 번역하기'); q.wait_ui('장면과 함께 저장하기', seconds=65)
        q.tap_text('어떤 소리를 들었나요?'); q.wait_ui('얼굴을 찾지 못했어요')
        q.screenshot('beta-actual-visual-result'); q.trace('beta-actual-visual-result')
        q.tap_text('장면과 함께 저장하기'); q.wait_ui('우리가 나눈 아기말', seconds=45)
        q.tap_text('장면 보기 · 내려받기'); q.wait_ui('그때의 아기말')
        result['beta_media'] = download_media(owned, 'beta')
        q.shell('input', 'keyevent', 'KEYCODE_BACK'); q.wait_ui('우리가 나눈 아기말')
        q.tap_text('아기말북'); q.tap_text('이 달의 아기말북 PDF 만들기', attempts=12)
        deadline = time.monotonic()+30
        while time.monotonic() < deadline:
            activity = q.shell('dumpsys','activity','activities').stdout.decode('utf-8','replace')
            if ('content://com.moa.babylanguage.files/exports/dispatched/jigeum-album-' in activity
                    and '.pdf' in activity and ('ChooserActivity' in activity or 'ResolverActivity' in activity)): break
            time.sleep(.4)
        else: raise AssertionError('Real PDF file/read grant/share chooser not observed')
        (q.RUN/'pdf-share-activity.txt').write_text(activity, encoding='utf-8')
        q.screenshot('beta-actual-pdf-share'); q.shell('input','keyevent','KEYCODE_BACK')
        result['scenarios'].append('beta runs real YAMNet + face model, saves clip/result and exports actual PDF without purchase')
        flow.launch(); q.wait_ui('전체 기능 테스트 · 2분 촬영 · 20초 분석')
        q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        result['scenarios'].append('cold restart retains distribution access and records without a purchase or code')
        result['installs'].append(delivery.install(args.release_apk))
        flow.launch(); q.wait_ui('무료 · 1분 촬영 · 10초 분석')
        assert not any('테스트 버전' in n['text'] for n in q.nodes())
        q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase']); q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('앨범에 영상 저장'); q.screenshot('release-preserved-record-media')
        result['release_media'] = download_media(owned, 'release', result['beta_media']['name'])
        assert result['release_media']['sha256'] == result['beta_media']['sha256'], 'Saved beta movie changed or was lost in release update'
        flow.launch(); q.tap_text('아기말북'); q.tap_text('전체 이용권 보기', attempts=12)
        q.wait_ui('쌓인 아기말을 한 권으로'); q.screenshot('release-pdf-relocked')
        delivery.import_video(Path(owned[1]).name); flow.visual_switch()
        q.wait_ui('지금의 표정도 함께 살펴봐요'); q.screenshot('release-visual-relocked')
        flow.launch(); q.tap_text('아기말 번역하기'); q.tap_text('저장된 영상 가져오기')
        delivery.choose_file(Path(owned[2]).name)
        q.wait_ui('100MB'); assert not any(n['text']=='어느 순간을 들어볼까요?' for n in q.nodes())
        q.screenshot('release-over-free-size-rejected')
        flow.launch(); q.tap_text('아기말 번역하기'); q.wait_ui('녹화 시작', seconds=20)
        q.wait_ui('최대 1분 담고, 10초까지 골라요.'); q.screenshot('release-free-capture')
        result['scenarios'].append('same-key release update restores 60/10/100, visual/PDF gates and preserves records/media')
        result['status'] = 'pass'
    except Exception as error:
        result['status']='fail'; result['error']=str(error); q.screenshot('beta-ui-failure'); q.trace('beta-ui-failure')
        raise
    finally:
        q.shell('am','force-stop',q.PACKAGE)
        q.restore_display(display)
        if font == 'null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',font)
        q.shell('rm','-f',*owned)
        large.unlink(missing_ok=True)
        oversized.unlink(missing_ok=True)
        for label in ('beta', 'release'):
            (q.RUN / (label + '-media.mp4')).unlink(missing_ok=True)
        remaining = [path for path in owned if q.shell('test', '-e', path, check=False).returncode == 0]
        assert not remaining, 'Owned external fixtures survived cleanup: ' + str(remaining)
        assert q.display_settings() == display, 'Display settings were not restored'
        assert q.shell('settings', 'get', 'system', 'font_scale').stdout.decode().strip() == font, 'Font scale was not restored'
        result['cleanup'] = dict(owned_external_files_removed=True, large_local_files_removed=True,
                                 raw_downloaded_media_removed=True, display_restored=True, font_restored=True)
        (q.RUN/'closed-test-ui-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2), encoding='utf-8')
        print(json.dumps(result,ensure_ascii=False),flush=True)


if __name__ == '__main__': main()
