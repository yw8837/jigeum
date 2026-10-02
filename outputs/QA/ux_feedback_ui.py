import json, re, sys, time
from pathlib import Path
repo=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(repo/'outputs/QA'))
import qa_harness as q
import preview_ui as delivery
import capture_ui as flow

result=dict(status='running',scenarios=[],installs=[],infant_intent_accuracy_tested=False)
display=q.display_settings()
font=q.shell('settings','get','system','font_scale').stdout.decode().strip()
preview=repo/'outputs/Agattok-0.5.7-test-code21.apk'
release=repo/'outputs/Agattok-0.5.7-release-code21.apk'
source=repo/'outputs/MoaAndroid/app/src/androidTest/assets/range-silence-tone.mp4'
owned='/sdcard/Download/agattok-ux057-synthetic.mp4'

def home_proof(label, full):
    flow.launch();q.wait_ui('아기말 번역하기');q.screenshot(label+'-home')
    info=q.nodes();main=next(n for n in info if n['text']=='아기말 번역하기')
    size=q.shell('wm','size').stdout.decode();width,height=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    assert main['center'][1] > height*.60,(label,main,height)
    summary='2분 촬영 · 20초 분석' if full else '1분 촬영 · 10초 분석'
    assert any(summary in n['text'] for n in info),(summary,info)
    q.tap_text('테스트 이용 안내' if full else '전체 이용권')
    q.wait_ui('전체 이용권을 확인해요');q.screenshot(label+'-pass')
    q.tap_text('닫기 · 원래 화면으로');q.wait_ui('아기말 번역하기')
    q.tap_text('돌봄');q.wait_ui('돌봄 · 내 정보');q.screenshot(label+'-care')
    q.tap_text('아기말북');q.tap_text('분홍빛 미소',attempts=12);q.screenshot(label+'-covers')
    assert any(n['text']=='선택됨' for n in q.nodes())
    result['scenarios'].append(label+': lower primary action, current allowance, pass/care routing, fourth cover')

try:
    result['installs'].append(delivery.install(preview))
    for permission in ['android.permission.CAMERA','android.permission.RECORD_AUDIO']:
        q.shell('pm','grant',q.PACKAGE,permission)
    for label,size,density,scale in [('small','720x1280','320','1.0'),('font200','1080x2400','420','2.0'),('tablet','1600x2560','240','1.0')]:
        q.shell('wm','size',size);q.shell('wm','density',density);q.shell('settings','put','system','font_scale',scale)
        home_proof(label,True)
    q.shell('wm','size','1080x2400');q.shell('wm','density','420');q.shell('settings','put','system','font_scale','1.0')
    flow.launch();q.tap_text('어떤 근거로 아기말을 만들까요?');q.wait_ui('아기말을 만드는 근거');q.screenshot('about');q.shell('input','keyevent','KEYCODE_BACK');q.wait_ui('아기말 번역하기')
    q.tap_text('아기말 번역하기');q.wait_ui('녹화 시작',seconds=20)
    assert any('최대 2분 담고, 20초까지 골라요.'==n['text'] for n in q.nodes())
    q.screenshot('full-capture-ready');q.tap_text('녹화 시작')
    begun=time.monotonic();print('Signed preview real two-minute video started.',flush=True)
    q.wait_ui('어느 순간을 들어볼까요?',seconds=140)
    result['actual_preview_capture_seconds']=round(time.monotonic()-begun,2)
    assert result['actual_preview_capture_seconds']>=118,result
    q.screenshot('full-two-minute-editor')
    q.tap_text('0.5초씩 맞추기',attempts=12);q.screenshot('fine-editor')
    result['scenarios'].append('actual signed preview: real 120-second automatic video stop, editor and fine controls')
    flow.launch();q.adb('push',str(source),owned)
    q.tap_text('아기말 번역하기');q.tap_text('저장된 영상 가져오기');delivery.choose_file(Path(owned).name);q.wait_ui('어느 순간을 들어볼까요?')
    q.tap_text('표정도 함께 보기');q.tap_text('이 구간 소리로 번역하기');q.wait_ui('아기 소리',seconds=65);q.screenshot('synthetic-no-baby')
    assert not any(n['text']=='장면과 함께 저장하기' for n in q.nodes()),'No baby evidence must not invent a result'
    result['scenarios'].append('actual model synthetic no-baby input abstains instead of producing a saved baby phrase')
    result['installs'].append(delivery.install(release))
    q.shell('wm','size','720x1280');q.shell('wm','density','320');q.shell('settings','put','system','font_scale','1.0')
    home_proof('release-small',False)
    flow.launch();q.tap_text('아기말 번역하기');q.wait_ui('녹화 시작',seconds=20)
    assert any('최대 1분 담고, 10초까지 골라요.'==n['text'] for n in q.nodes())
    assert not any(n['text']=='테스트 버전 · 전체 기능 열림' for n in q.nodes())
    q.screenshot('release-free-capture');result['scenarios'].append('final signed release: free allowance restored, no stored preview entitlement')
    result['status']='pass'
except Exception as error:
    result['status']='fail';result['error']=str(error);q.screenshot('signed-ui-failure');raise
finally:
    q.adb('shell','rm','-f',owned)
    q.shell('am','force-stop',q.PACKAGE)
    q.shell('settings','put','system','font_scale',font);q.restore_display(display)
    (q.RUN/'signed-ui-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False),flush=True)
