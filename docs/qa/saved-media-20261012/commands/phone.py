from pathlib import Path
import subprocess,json,sys,hashlib,time
root=Path(__file__).resolve().parents[2];base=Path(__file__).parent
sys.path.insert(0,str(root/'outputs/QA'))
from instrument import installed_provenance,parse_result
serial=sys.argv[1]; out=base/sys.argv[2];out.mkdir(exist_ok=False)
adb=['C:/MoaSdk/platform-tools/adb.exe','-s',serial]
pkg='com.moa.babylanguage.astcandidate'
def call(args,timeout=90):
 r=subprocess.run(adb+args,capture_output=True,timeout=timeout);r.check_returncode();return r.stdout
assert call(['get-state']).strip()==b'device'
model=call(['shell','getprop','ro.product.model']).decode().strip()
assert model.startswith('SM-S938'),model
def original():
 raw=call(['shell','dumpsys','package','com.moa.babylanguage']).decode('utf8','replace')
 return [line.strip() for line in raw.splitlines() if any(v in line for v in ['versionCode=','versionName=','lastUpdateTime=','firstInstallTime=','dataDir=','codePath='])]
before=original()
(out/'original-app-before.json').write_text(json.dumps(before,indent=2),'utf8')
for package in [pkg,pkg+'.test']:
 assert not call(['shell','pm','list','packages',package]).strip(),'Existing research package requires inspection before replacing'
provenance={'model':model,'device_serial_sha256':hashlib.sha256(serial.encode()).hexdigest()}
for name,path,package in [('app','apk/debug/app-debug.apk',pkg),('test','apk/androidTest/debug/app-debug-androidTest.apk',pkg+'.test')]:
 f=root/'work/interval-evidence-20261009/build/outputs'/path
 digest=hashlib.file_digest(f.open('rb'),'sha256').hexdigest()
 assert b'Success' in call(['install',str(f)],120)
 provenance[name]=installed_provenance(adb,package,digest,require_version=name=='app')
 assert provenance[name]['verified']
(out/'provenance.json').write_text(json.dumps(provenance,indent=2),'utf8')
classes=','.join('com.moa.babylanguage.'+c for c in ['SavedMediaFlowTest','CaptionHdrCompatibilityTest','TranslatedMediaTests'])
started=time.monotonic()
r=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',classes,pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=420)
raw=(r.stdout+r.stderr).decode('utf8','replace')
(out/'instrumentation.txt').write_text(raw,'utf8')
report=parse_result(raw,r.returncode,time.monotonic()-started)
report['original_app_metadata_unchanged']=before==original()
(out/'result.json').write_text(json.dumps(report,indent=2),'utf8')
print(json.dumps(report));sys.exit(0 if report['status']=='pass' and report['original_app_metadata_unchanged'] else 1)
