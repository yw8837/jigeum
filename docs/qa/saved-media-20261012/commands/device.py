from pathlib import Path
import subprocess,json,sys,hashlib,time
root=Path(__file__).resolve().parents[2];base=Path(__file__).parent
sys.path.insert(0,str(root/'outputs/QA'))
from instrument import installed_provenance,parse_result
adb=['C:/MoaSdk/platform-tools/adb.exe','-s','emulator-5556']
pkg='com.moa.babylanguage.astcandidate'
out=base/sys.argv[1];out.mkdir(exist_ok=False)
def call(args,timeout=90):
 r=subprocess.run(adb+args,capture_output=True,timeout=timeout);r.check_returncode();return r.stdout
assert call(['emu','avd','name']).decode().splitlines()[0]=='MoaQA35'
assert call(['shell','getprop','sys.boot_completed']).strip()==b'1'
provenance={}
for name,path,package in [('app','apk/debug/app-debug.apk',pkg),('test','apk/androidTest/debug/app-debug-androidTest.apk',pkg+'.test')]:
 f=root/'work/interval-evidence-20261009/build/outputs'/path
 digest=hashlib.file_digest(f.open('rb'),'sha256').hexdigest()
 assert b'Success' in call(['install','-r',str(f)],120)
 provenance[name]=installed_provenance(adb,package,digest,require_version=name=='app')
 assert provenance[name]['verified']
(out/'provenance.json').write_text(json.dumps(provenance,indent=2),'utf8')
classes=sys.argv[2] if len(sys.argv)>2 else 'SavedMediaFlowTest'
classes=','.join('com.moa.babylanguage.'+c for c in classes.split(','))
started=time.monotonic()
r=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class',classes,pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=480)
raw=(r.stdout+r.stderr).decode('utf8','replace')
(out/'instrumentation.txt').write_text(raw,'utf8')
report=parse_result(raw,r.returncode,time.monotonic()-started)
(out/'result.json').write_text(json.dumps(report,indent=2),'utf8')
print(json.dumps(report));sys.exit(0 if report['status']=='pass' else 1)
