"""0.5 disposable-device UI smoke. Synthetic video; never evidence of baby classification accuracy."""
from pathlib import Path
import json
import time
import xml.etree.ElementTree as ET
import re
import qa_harness as q
from qa_config import ROOT


def launch():
    q.shell('am', 'force-stop', q.PACKAGE)
    q.shell('am', 'start', '-W', '-n', q.PACKAGE+'/.MainActivity')
    q.wait_ui('아기말 번역하기')


def import_fixture():
    launch()
    q.tap_text('아기말 번역하기')
    q.tap_text('저장된 영상 가져오기')
    entries=q.nodes()
    roots=next((n for n in entries if n['desc']=='Show roots'), None)
    if roots:
        q.tap(*roots['center'])
        downloads=[n for n in q.wait_ui('Downloads') if n['text']=='Downloads']
        q.tap(*downloads[-1]['center'])
    q.wait_ui('Files in Downloads')
    q.tap_text('agattok-tone.mp4', attempts=10)
    q.wait_ui('어느 순간을 들어볼까요?')


def visual_switch():
    q.tap_text('표정도 함께 보기')  # scroll the label into view, then target the real switch.
    q.nodes()
    root=ET.fromstring(q.adb('exec-out','cat','/sdcard/moa-ultraqa.xml').stdout)
    switch=next(n for n in root.iter('node') if n.attrib.get('checkable')=='true')
    # Compose exposes a checkable View; class is not android.widget.Switch.
    q.swipe_up()
    q.nodes()
    root=ET.fromstring(q.adb('exec-out','cat','/sdcard/moa-ultraqa.xml').stdout)
    switch=next(n for n in root.iter('node') if n.attrib.get('checkable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',switch.attrib['bounds']))
    q.tap((x1+x2)//2,(y1+y2)//2)


def main():
    display=q.display_settings()
    original=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    verdict={'status':'running','scenarios':[], 'synthetic_media':True, 'baby_accuracy_tested':False}
    fixture=ROOT/'outputs/MoaAndroid/app/src/androidTest/assets/range-silence-tone.mp4'
    q.adb('push',str(fixture),'/sdcard/Download/agattok-tone.mp4')
    try:
        for permission in ['android.permission.CAMERA','android.permission.RECORD_AUDIO']:
            q.shell('pm','grant',q.PACKAGE,permission)
        for label,size,density,font in [('small','720x1280','320','1.0'),('font200','1080x2400','420','2.0')]:
            q.shell('wm','size',size); q.shell('wm','density',density)
            q.shell('settings','put','system','font_scale',font)
            launch(); q.screenshot(label+'-home')
            q.tap_text('아기말 번역하기'); q.screenshot(label+'-capture')
            if label=='small':
                q.tap_text('카메라 준비하기'); q.wait_ui('녹화 시작')
                q.screenshot('camera-preview'); q.tap_text('녹화 시작'); time.sleep(3)
                q.tap_text('촬영 마치기'); q.wait_ui('어느 순간을 들어볼까요?')
                q.screenshot('recorded-video-review')
                q.tap_text('선택 구간 다시 듣기',exact=False); q.trace('captured-video-play-control')
            import_fixture(); q.screenshot(label+'-review')
            visual_switch(); q.wait_ui('지금의 표정도 함께 살펴봐요'); q.screenshot(label+'-paywall')
            q.tap_text('개인정보처리방침'); q.tap_text('돌아가기')
            q.tap_text('같은 구간 소리로 계속하기'); q.wait_ui('어느 순간을 들어볼까요?')
            q.tap_text('이 구간 소리로 번역하기')
            # Fixture contains only digital silence + a sine tone. There must be no baby result.
            q.wait_ui('아기 소리 단서',seconds=35)
            texts=[n['text'] for n in q.nodes()]
            assert not any('이 문장 저장하기' in t for t in texts), texts
            q.screenshot(label+'-no-evidence'); q.trace(label+'-no-evidence')
            verdict['scenarios'].append(label+' capture/import/paywall/policy/abstain')
        verdict['status']='pass'
    except Exception as error:
        verdict['status']='fail'; verdict['error']=str(error)
        q.screenshot('failure')
        raise
    finally:
        q.restore_display(display)
        if original=='null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',original)
        (q.RUN/'capture-ui-result.json').write_text(json.dumps(verdict,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(verdict,ensure_ascii=False))


if __name__=='__main__':
    main()
