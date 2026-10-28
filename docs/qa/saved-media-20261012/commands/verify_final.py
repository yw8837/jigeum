from pathlib import Path
import hashlib,json,os,re,subprocess,sys

base=Path(__file__).resolve().parent / "final";root=base.parents[2]
prior=root/'work/interval-evidence-20261009/final'
source=Path('C:/Users/최용우/Documents/Codex/2026-09-29/plugin-browser-openai-bundled-mention-tab')
java='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe'
bundletool=source/'work/closed-test-058/tools/bundletool-all-1.18.3.jar'
env=dict(os.environ,JAVA_HOME=str(Path(java).parents[1]),ANDROID_HOME='C:/MoaSdk',ANDROID_SDK_ROOT='C:/MoaSdk')
for k in ('OMX_ROOT','OMX_STATE_ROOT'):env.pop(k,None)
sys.path.insert(0,str(root/'tools/release'))
from verify_ast_payload import verify

result={'status':'running','variants':{}}
for variant in ('beta','release'):
    apk=base/f'Agattok-0.5.24-{variant}-code38.apk';aab=apk.with_suffix('.aab')
    evidence=base/(variant+'-package')
    args=[sys.executable,str(root/'outputs/QA/check_package.py'),'--distribution',variant,'--apk',str(apk),'--aab',str(aab),'--baseline',str(prior/f'Agattok-0.5.23-{variant}-code37.apk'),'--bundletool',str(bundletool)]
    if not (evidence/'package-verdict.json').exists():
        with (base/(variant+'-package.log')).open('wb') as log:
            subprocess.run(args,cwd=root,env=dict(env,JIGEUM_QA_EVIDENCE=str(evidence)),stdout=log,stderr=subprocess.STDOUT,timeout=360,check=True)
    package=json.loads((evidence/'package-verdict.json').read_text(encoding='utf-8'));assert package['status']=='pass'
    assert package['apk_sha256']==hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
    assert package['aab_sha256']==hashlib.file_digest(aab.open('rb'),'sha256').hexdigest()
    expected='present' if variant=='beta' else 'absent'
    payload=[verify(p,expected,'infant-caption' if variant=='beta' else 'base') for p in (apk,aab)]
    dex=subprocess.run(['C:/MoaSdk/cmdline-tools/latest/bin/apkanalyzer.bat','dex','code','--class','com.moa.babylanguage.BuildConfig',str(apk)],env=env,capture_output=True,timeout=90,check=True).stdout.decode('utf-8')
    (base/(variant+'-buildconfig-dex.txt')).write_text(dex,encoding='utf-8')
    flags={'AST_INFANT_CAPTION':variant=='beta','AST_INFANT_PRESENCE':variant=='beta','AST_BABBLE_SUPPORT':variant=='beta','AST_INTEGRATED_CANDIDATE':False,'FULL_ACCESS_CLOSED_TEST':variant=='beta','FULL_ACCESS_PREVIEW':False,'DEBUG':False}
    for key,value in flags.items():assert re.search(r'\.field public static final '+key+r':Z = '+str(value).lower()+r'\b',dex),key
    assert '.field public static final VERSION_CODE:I = 0x26' in dex
    cadence=subprocess.run(['C:/MoaSdk/cmdline-tools/latest/bin/apkanalyzer.bat','dex','code','--class','com.moa.babylanguage.VideoFrameCadence',str(apk)],env=env,capture_output=True,timeout=90,check=True).stdout.decode('utf-8')
    (base/(variant+'-cadence-dex.txt')).write_text(cadence,encoding='utf-8')
    assert re.search(r'\.field public static final INTERVAL_MS:J = 0xc8L\b',cadence), 'cadence field missing'
    assert '0x30d40' in cadence, '200000us cadence absent from compiled selection'
    renderer=subprocess.run(['C:/MoaSdk/cmdline-tools/latest/bin/apkanalyzer.bat','dex','code','--class','com.moa.babylanguage.TranslatedMedia',str(apk)],env=env,capture_output=True,timeout=90,check=True).stdout.decode('utf-8')
    (base/(variant+'-renderer-dex.txt')).write_text(renderer,encoding='utf-8')
    assert 'selectedVideo' in renderer and 'renderMedia' in renderer, 'New shared raw/caption renderer missing'
    old=root/'work/vocal-kind-20261010/final'/apk.name
    assert hashlib.file_digest(old.open('rb'),'sha256').hexdigest()!=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(), 'Old unsigned-fix candidate was reused'
    result['variants'][variant]={'package':package,'payload':payload,'flags':flags,'files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.file_digest(p.open('rb'),'sha256').hexdigest()} for p in (apk,aab)}}
result['status']='pass'
(base/'final-package-result.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({'status':result['status'],'files':{v:r['files'] for v,r in result['variants'].items()}},indent=2))
