from pathlib import Path
import json,os,sys,time
root=Path(__file__).resolve().parents[2];base=Path(__file__).resolve().parent;out=base/'signed-smoke-gpu'
out.mkdir(exist_ok=False)
os.environ.update(ANDROID_SERIAL='emulator-5556',JIGEUM_QA_SERIAL='emulator-5556',ANDROID_HOME='C:/MoaSdk',
 JIGEUM_QA_ADB='C:/MoaSdk/platform-tools/adb.exe',JIGEUM_QA_EVIDENCE=str(out))
for key in ('OMX_ROOT','OMX_STATE_ROOT'):os.environ.pop(key,None)
sys.path.insert(0,str(root/'outputs/QA'))
import qa_harness as q
import capture_ui as flow
import preview_ui as delivery
from closed_test_ui import download_media
def select_twenty_seconds():
    q.tap_text('0.5초씩 맞추기', attempts=12)
    target=None
    for _ in range(12):
        target=next((n for n in q.nodes() if n['desc']=='끝 0.5초 뒤로'),None)
        if target:break
        q.swipe_up()
    assert target and target['enabled']
    for _ in range(20):q.tap(*target['center'])
    q.wait_ui('0:00.0 – 0:20.0 · 20초 선택')

assert q.adb('emu','avd','name').stdout.decode().splitlines()[0]=='MoaQA35'
fixture=base/'performance04/public20.mp4'
remote='/sdcard/Download/agattok-cadence-smoke-'+str(time.time_ns())+'.mp4';owned=[remote]
assert q.shell('test','-e',remote,check=False).returncode!=0
result=dict(status='running',actual_play_install_tested=False,physical_device=False,scenarios=[])
try:
    beta=base/'final/Agattok-0.5.24-beta-code38.apk'
    result['beta_install']=delivery.install(beta)
    assert result['beta_install']['verified'] and result['beta_install']['version_code']==38
    q.adb('push',str(fixture),remote)
    flow.launch();q.wait_ui('전체 기능 테스트 · 2분 촬영 · 20초 분석')
    delivery.import_video(Path(remote).name);select_twenty_seconds();q.tap_text('영상 + 소리로 번역하기')
    begun=time.monotonic();time.sleep(46);q.wait_ui('장면과 함께 저장하기',seconds=12)
    result['analysis_confirmation_seconds_including_46_second_quiet_wait']=round(time.monotonic()-begun,3)
    q.screenshot('beta38-actual-av');q.trace('beta38-actual-av')
    q.tap_text('장면과 함께 저장하기');q.wait_ui('우리가 나눈 아기말',seconds=45)
    q.tap_text('장면 보기 · 내려받기');q.wait_ui('그때의 아기말')
    result['beta_media']=download_media(owned,'beta38')
    result['scenarios'].append('New signed beta38 actual 20-second AV analysis, scene save, and MP4 export without purchase')
    release=base/'final/Agattok-0.5.24-release-code38.apk'
    result['release_install']=delivery.install(release)
    assert result['release_install']['verified'] and result['release_install']['version_code']==38
    flow.launch();q.wait_ui('무료 · 1분 촬영 · 10초 분석')
    q.tap_text('기록');q.tap_text('장면 보기 · 내려받기');q.wait_ui('앨범에 영상 저장')
    result['release_media']=download_media(owned,'release38',result['beta_media']['name'])
    assert result['beta_media']['sha256']==result['release_media']['sha256']
    flow.launch();q.tap_text('아기말북');q.tap_text('전체 이용권 보기',attempts=12)
    q.wait_ui('쌓인 아기말을 한 권으로');q.screenshot('release38-pdf-relocked')
    delivery.import_video(Path(remote).name);flow.visual_switch()
    q.wait_ui('지금의 표정도 함께 살펴봐요');q.screenshot('release38-visual-relocked')
    for permission in ('android.permission.CAMERA','android.permission.RECORD_AUDIO'):
        q.shell('pm','grant',q.PACKAGE,permission)
    flow.launch();q.tap_text('아기말 번역하기');q.wait_ui('녹화 시작',seconds=20)
    q.wait_ui('최대 1분 담고, 10초까지 골라요.')
    result['scenarios'].append('Same-key release38 relocks visual/PDF and 60/10 capture while saved MP4 hash remains identical')
    result['status']='pass'
except Exception as e:
    result.update(status='failed',error=str(e));raise
finally:
    q.shell('am','force-stop',q.PACKAGE);q.shell('rm','-f',*owned)
    result['owned_external_files_removed']=all(q.shell('test','-e',p,check=False).returncode!=0 for p in owned)
    if not result['owned_external_files_removed']:result['status']='failed'
    (out/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf8')
    print(json.dumps({k:result[k] for k in ('status','scenarios','owned_external_files_removed')}))
assert result['status']=='pass'

