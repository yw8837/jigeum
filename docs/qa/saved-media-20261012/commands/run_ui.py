from pathlib import Path
import json,os,subprocess,sys,time
root=Path.cwd();base=root/'work/saved-media-20261012';mode=sys.argv[1]
adb=['C:/MoaSdk/platform-tools/adb.exe','-s','emulator-5556']
name=subprocess.run(adb+['emu','avd','name'],capture_output=True,timeout=15,check=True).stdout.decode().splitlines()[0]
assert name=='MoaQA35', name
assert mode in ('upgrade','closed')
source=Path('C:/Users/최용우/Documents/Codex/2026-09-29/plugin-browser-openai-bundled-mention-tab')
beta=base/'final'/'Agattok-0.5.24-beta-code38.apk';release=base/'final'/'Agattok-0.5.24-release-code38.apk'
fixture=source/'work/test-access-052/agattok-qa-cry.mp4'
if mode=='upgrade':
    args=['outputs/QA/signed_media_upgrade.py','--old-apk',str(root/'work/interval-evidence-20261009/final/Agattok-0.5.23-beta-code37.apk'),'--new-apk',str(beta),'--cry-video',str(fixture),'--old-version-code','37','--old-version-name','0.5.23-beta','--new-version-code','38','--new-version-name','0.5.24-beta'];limit=700
else:
    args=['outputs/QA/closed_test_ui.py','--beta-apk',str(beta),'--release-apk',str(release),'--cry-video',str(fixture)];limit=1400
label=sys.argv[2] if len(sys.argv)>2 else 'final'
assert label in ('final','retry02','remaining03','remaining04')
if label in ('remaining03','remaining04'):
    assert mode == 'closed'
    args += ['--access-proof',str(base/'signed-closed-retry02'/'closed-test-ui-result.json')]
out=base/('signed-'+mode+'-'+label);out.mkdir(exist_ok=False)
env=dict(os.environ,ANDROID_SERIAL='emulator-5556',JIGEUM_QA_SERIAL='emulator-5556',ANDROID_HOME='C:/MoaSdk',JIGEUM_QA_ADB='C:/MoaSdk/platform-tools/adb.exe',JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot',JIGEUM_QA_EVIDENCE=str(out))
for key in ('OMX_ROOT','OMX_STATE_ROOT'):env.pop(key,None)
start=time.monotonic();verdict={'mode':mode,'status':'running','timeout_seconds':limit,'synthetic_or_public_fixture_only':True,'avd':name}
with (out/'process.log').open('wb') as log:
    try:
        r=subprocess.run([sys.executable,*args],env=env,stdout=log,stderr=subprocess.STDOUT,timeout=limit)
        verdict.update(status='pass' if r.returncode==0 else 'failed',exit=r.returncode)
    except subprocess.TimeoutExpired:
        verdict.update(status='timeout')
        subprocess.run(adb+['shell','am','force-stop','com.moa.babylanguage'],timeout=15,capture_output=True)
verdict['seconds']=round(time.monotonic()-start,2)
(out/'process-result.json').write_text(json.dumps(verdict,indent=2),encoding='utf-8');print(json.dumps(verdict))
sys.exit(0 if verdict['status']=='pass' else 1)
