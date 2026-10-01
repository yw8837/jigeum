"""Full-access sideload delivery QA on an explicitly owned disposable device.

Uses the real signed MainActivity, DeviceClipAnalyzer and PDF exporter. The caller
supplies a local QA video with audible infant cues. It is never redistributed.
Does not establish infant intent accuracy or physical-device quality.
"""
import argparse
import hashlib
import json
import time
from pathlib import Path
import qa_harness as q
import capture_ui as flow
from qa_config import ROOT, adb_command
from instrument import installed_provenance


def choose_file(name):
    entries = q.nodes()
    roots = next((n for n in entries if n['desc'] == 'Show roots'), None)
    if roots:
        q.tap(*roots['center'])
        downloads = [n for n in q.wait_ui('Downloads') if n['text'] == 'Downloads']
        q.tap(*downloads[-1]['center'])
    q.tap_text(name, attempts=10)


def import_video(name):
    flow.launch()
    q.tap_text('아기말 번역하기')
    q.tap_text('저장된 영상 가져오기')
    choose_file(name)
    q.wait_ui('어느 순간을 들어볼까요?')


def install(apk):
    q.adb('install', '-r', str(apk), timeout=120)
    evidence = installed_provenance(adb_command(), q.PACKAGE, hashlib.sha256(apk.read_bytes()).hexdigest())
    assert evidence['verified'], evidence
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-apk', type=Path, required=True)
    parser.add_argument('--preview-apk', type=Path, required=True)
    parser.add_argument('--release-apk', type=Path, required=True)
    parser.add_argument('--cry-video', type=Path, required=True)
    parser.add_argument('--skip-layout', action='store_true', help='Focused rerun after unchanged layout/abstention paths passed; retain the earlier evidence.')
    args = parser.parse_args()
    display = q.display_settings()
    font = q.shell('settings', 'get', 'system', 'font_scale').stdout.decode().strip()
    verdict = dict(status='running', scenarios=[], installs=[], actual_infant_intent_accuracy_tested=False,
                   layout_retested=not args.skip_layout,
                   visual_fixture='CC0 public cry with generated plain-color video: intentionally no face')
    owned = ['agattok-qa-cry.mp4', 'agattok-qa-upgrade.json', 'agattok-tone.mp4']
    seed = dict(format='jigeum-observations', version=1, ageBand='', records=[dict(
        id=int(time.time()*1000), sound='옹알이', context='0:00–0:03', note='Owned QA upgrade fixture',
        phrase='업데이트 뒤에도 남는 테스트 기록', source='AUDIO', modelCue='')])
    seed_path = q.RUN / 'upgrade-fixture.json'
    seed_path.write_text(json.dumps(seed, ensure_ascii=False), encoding='utf-8')
    try:
        verdict['installs'].append(install(args.baseline_apk))
        q.adb('push', str(seed_path), '/sdcard/Download/'+owned[1])
        q.adb('push', str(args.cry_video), '/sdcard/Download/'+owned[0])
        q.adb('push', str(ROOT/'outputs/MoaAndroid/app/src/androidTest/assets/range-silence-tone.mp4'), '/sdcard/Download/'+owned[2])
        flow.launch(); q.tap_text('돌봄'); q.tap_text('백업 파일 가져오기')
        choose_file(owned[1]); q.wait_ui('기록을 합칠까요?'); q.tap_text('기록 합치기')
        q.wait_ui('백업의 기록을 합쳤어요')
        flow.launch(); q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        q.screenshot('baseline-record')
        verdict['installs'].append(install(args.preview_apk))
        flow.launch(); q.wait_ui('테스트 버전 · 전체 기능 열림')
        q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        verdict['scenarios'].append('baseline to preview same-package signed update preserves records')
        for permission in ['android.permission.CAMERA', 'android.permission.RECORD_AUDIO']:
            q.shell('pm', 'grant', q.PACKAGE, permission)
        layouts = [] if args.skip_layout else [('small','720x1280','320','1.0'), ('font200','1080x2400','420','2.0')]
        for label, size, density, scale in layouts:
            q.shell('wm','size',size); q.shell('wm','density',density)
            q.shell('settings','put','system','font_scale',scale)
            flow.launch(); q.screenshot(label+'-test-home')
            q.tap_text('아기말 번역하기'); q.wait_ui('녹화 시작')
            assert not any('촬영 마치기' in n['text'] for n in q.nodes())
            q.screenshot(label+'-ready-without-preparation-tap')
            if label == 'small':
                q.tap_text('녹화 시작'); time.sleep(3); q.tap_text('촬영 마치기')
                q.wait_ui('어느 순간을 들어볼까요?')
                q.tap_text('선택 구간 다시 듣기', exact=False)
                q.screenshot('preview-recorded-video-playback')
            import_video(owned[2])
            q.tap_text('영상 + 소리로 번역하기')
            q.wait_ui('아기 소리 단서', seconds=50)
            assert not any('이 문장 저장하기' in n['text'] for n in q.nodes())
            q.screenshot(label+'-preview-abstains'); q.trace(label+'-preview-abstains')
            verdict['scenarios'].append(label+' authorized capture, preview video analysis selection, non-infant abstention')
        q.restore_display(display)
        q.shell('settings','put','system','font_scale','1.0')
        import_video(owned[0]); q.screenshot('preview-cry-review')
        q.tap_text('영상 + 소리로 번역하기')
        q.wait_ui('이 문장 저장하기', seconds=55)
        q.screenshot('actual-model-result'); q.trace('actual-model-result')
        q.tap_text('어떤 소리를 들었나요?')
        q.wait_ui('얼굴을 찾지 못했어요')
        q.screenshot('actual-visual-no-face-evidence'); q.trace('actual-visual-no-face-evidence')
        q.tap_text('이 문장 저장하기'); q.wait_ui('우리가 나눈 아기말')
        q.tap_text('아기말북'); q.tap_text('이 달의 아기말북 PDF 만들기')
        # The real exporter has created a PDF before handing its content URI to Android.
        deadline = time.monotonic()+25
        while time.monotonic() < deadline:
            activity = q.shell('dumpsys','activity','activities').stdout.decode('utf-8','replace')
            # API35 nests the SEND intent; dumpsys omits its MIME but exposes the
            # read grant to the generated PDF. Require both it and the actual chooser.
            if ('content://com.moa.babylanguage.files/exports/dispatched/jigeum-album-' in activity
                    and '.pdf' in activity and ('ChooserActivity' in activity or 'ResolverActivity' in activity)): break
            time.sleep(.4)
        else: raise AssertionError('Actual PDF share intent/chooser not observed')
        (q.RUN/'pdf-share-activity.txt').write_text(activity,encoding='utf-8')
        q.screenshot('preview-real-pdf-share'); q.trace('preview-real-pdf-share')
        verdict['scenarios'].append('unpaid real YAMNet + face model no-face result, save, actual PDF export/share')
        q.shell('input','keyevent','KEYCODE_BACK')
        flow.launch(); q.wait_ui('테스트 버전 · 전체 기능 열림')
        q.tap_text('아기말북'); q.tap_text('이 달의 아기말북 PDF 만들기')
        q.shell('input','keyevent','KEYCODE_BACK')
        verdict['scenarios'].append('cold restart keeps test access without a code')
        verdict['installs'].append(install(args.release_apk))
        flow.launch()
        assert not any('테스트 버전' in n['text'] for n in q.nodes())
        q.tap_text('기록'); q.wait_ui(seed['records'][0]['phrase'])
        q.tap_text('아기말북'); q.tap_text('전체 이용권 보기')
        q.screenshot('production-pdf-relocked'); q.trace('production-pdf-relocked')
        import_video(owned[2]); flow.visual_switch()
        q.wait_ui('지금의 표정도 함께 살펴봐요'); q.screenshot('production-visual-relocked')
        verdict['scenarios'].append('preview to production update keeps records and restores PDF/visual gates')
        verdict['status'] = 'pass'
    except Exception as error:
        verdict['status']='fail'; verdict['error']=str(error)
        q.screenshot('preview-failure'); q.trace('preview-failure')
        raise
    finally:
        q.restore_display(display)
        if font == 'null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',font)
        q.shell('rm','-f',*['/sdcard/Download/'+name for name in owned])
        (q.RUN/'preview-ui-result.json').write_text(json.dumps(verdict,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(verdict,ensure_ascii=False))


if __name__ == '__main__':
    main()
