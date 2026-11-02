from pathlib import Path
import subprocess,json,sys,hashlib,time,uuid
root=Path(__file__).resolve().parents[2];base=Path(__file__).parent
out=base/(sys.argv[2] if len(sys.argv)>2 else 'performance01');out.mkdir(exist_ok=False)
sys.path.insert(0,str(root/'outputs/QA'));sys.path.insert(0,str(root/'tools/research'))
from instrument import installed_provenance,parse_result
from public_infant_review_acquire import FFMPEG,verify_ffmpeg
serial=sys.argv[1];adb=['C:/MoaSdk/platform-tools/adb.exe','-s',serial];pkg='com.moa.babylanguage.astcandidate'
def call(args,timeout=45,input=None):
 r=subprocess.run(adb+args,capture_output=True,timeout=timeout,input=input);r.check_returncode();return r.stdout
assert call(['shell','getprop','ro.product.model']).decode().strip().startswith('SM-S938')
app=root/'work/interval-evidence-20261009/build/outputs/apk/debug/app-debug.apk'
test=root/'work/interval-evidence-20261009/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
app_sha=hashlib.file_digest(app.open('rb'),'sha256').hexdigest()
assert app_sha=='982735e9a6d6c2ad31dea24bd4da165ce66c5bbf49361afdb24f39048ae65421','Product APK unexpectedly changed'
proof={'app':installed_provenance(adb,pkg,app_sha)};assert proof['app']['verified']
data=(root/'work/ast-public-paired-01/corpus/mayra_context.f32').read_bytes()
assert hashlib.sha256(data).hexdigest()=='7d94b29116df67113375501ea8bf2ee03551e5cca24e458faf7bdfb54e9eb025'
movie=out/'public20.mp4';verify_ffmpeg(FFMPEG)
expected='9d57307e1fbd0ff3329e9ed5eba90d6713e619c9ac5d7b65cc6118a5876d8877'
attempts=[]
for attempt in range(3):
 subprocess.run([str(FFMPEG),'-y','-v','error','-nostdin','-f','f32le','-ar','16000','-ac','1','-i','pipe:0','-f','lavfi','-i','color=c=black:s=320x320:r=1','-map','1:v:0','-map','0:a:0','-c:v','libx264','-threads','1','-pix_fmt','yuv420p','-c:a','aac','-b:a','128k','-t','20',str(movie)],input=data,capture_output=True,timeout=30,check=True)
 attempts.append(hashlib.file_digest(movie.open('rb'),'sha256').hexdigest())
 if attempts[-1]==expected:break
assert attempts[-1]==expected,attempts
remote='files/qa-saved-media-performance'
absent=subprocess.run(adb+['shell','run-as',pkg,'test','-e',remote],capture_output=True,timeout=20)
assert absent.returncode==1 and not absent.stderr,'Existing folder must be preserved'
call(['shell','run-as',pkg,'mkdir',remote])
call(['exec-in','run-as',pkg,'dd',f'of={remote}/public20.mp4','bs=65536'],input=movie.read_bytes())
assert call(['shell','run-as',pkg,'sha256sum',remote+'/public20.mp4']).decode().split()[0]==expected
assert b'Success' in call(['install','-r',str(test)],120)
proof['test']=installed_provenance(adb,pkg+'.test',hashlib.file_digest(test.open('rb'),'sha256').hexdigest(),require_version=False)
assert proof['test']['verified']
(out/'provenance.json').write_text(json.dumps(proof,indent=2),'utf8')
report={'status':'fail','encoding_sha256_attempts':attempts,'retained_public_input':True,'user_media_read':False}
tone_run=uuid.uuid4().hex
try:
 call(['shell','am','force-stop',pkg])
 started=time.monotonic()
 r=subprocess.run(adb+['shell','am','instrument','-w','-r','-e','class','com.moa.babylanguage.SavedMediaPerformanceTest,com.moa.babylanguage.AstIntegratedPerformanceTest','-e','saved_media_performance','true','-e','ast_integrated_performance','true','-e','ast_integrated_performance_run_id',tone_run,pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],capture_output=True,timeout=220)
 raw=(r.stdout+r.stderr).decode('utf8','replace');(out/'instrumentation.txt').write_text(raw,'utf8')
 report['runner']=parse_result(raw,r.returncode,time.monotonic()-started)
 payload=call(['shell','run-as',pkg,'cat',remote+'/result.json'])
 (out/'device-result.json').write_bytes(payload)
 result=json.loads(payload)
 tone=json.loads(call(['exec-out','run-as',pkg,'cat',f'files/qa-ast-integrated-performance/{tone_run}.json']))
 (out/'tone-device-result.json').write_text(json.dumps(tone,indent=2),'utf8')
 assert report['runner']['status']=='pass' and result['status']=='pass'
 assert len(result['rows'])==2 and all(row['visual_frames']==20 and not row['visual_covers_selection'] and row['elapsed_ms']<45000 for row in result['rows'])
 assert tone['status']=='completed' and [row['visual_frame_count'] for row in tone['cases']]==[50,100]
 report['status']='pass'
finally:
 call(['shell','run-as',pkg,'rm','-f',remote+'/public20.mp4',remote+'/result.json'])
 call(['shell','run-as',pkg,'rmdir',remote])
 call(['shell','run-as',pkg,'rm','-f',f'files/qa-ast-integrated-performance/{tone_run}.json'])
 report['owned_device_input_and_report_removed']=True
 (out/'result.json').write_text(json.dumps(report,indent=2),'utf8')
 print(json.dumps(report))
