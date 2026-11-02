from pathlib import Path
import ctypes,hashlib,json,os,subprocess,sys,threading,time
root=Path(__file__).resolve().parents[2];base=Path(__file__).parent
out=base/(sys.argv[1] if len(sys.argv)>1 else 'delivery-diagnosis');out.mkdir(exist_ok=True)
os.environ.update(ANDROID_SERIAL='emulator-5556',JIGEUM_QA_SERIAL='emulator-5556',JIGEUM_QA_EVIDENCE=str(out),ANDROID_HOME='C:/MoaSdk',JIGEUM_QA_ADB='C:/MoaSdk/platform-tools/adb.exe')
for k in ('OMX_ROOT','OMX_STATE_ROOT'):os.environ.pop(k,None)
sys.path.insert(0,str(root/'outputs/QA'))
import qa_harness as q
import capture_ui as flow
import preview_ui as delivery
class Memory(ctypes.Structure):
    _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong)]+[(n,ctypes.c_ulonglong) for n in ('total','avail','page','availpage','virtual','availvirtual','extended')]
def memory():
    m=Memory();m.length=ctypes.sizeof(m);assert ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m));return m.avail
def dump(value):
    (out/'diagnosis.json').write_text(json.dumps(value,ensure_ascii=False,indent=2),'utf8')
def selection():
    q.tap_text('0.5초씩 맞추기',attempts=12)
    for _ in range(12):
        n=next((n for n in q.nodes() if n['desc']=='끝 0.5초 뒤로'),None)
        if n:break
        q.swipe_up()
    assert n and n['enabled']
    # Same stable fine-control button; assert the final real UI range afterward.
    for _ in range(20):q.tap(*n['center'])
    q.wait_ui('0:00.0 – 0:20.0 · 20초 선택')
deadline=time.monotonic()+150
while time.monotonic()<deadline:
    try:
        if q.shell('getprop','sys.boot_completed',timeout=10).stdout.strip()==b'1':break
    except Exception:pass
    time.sleep(2)
else:raise AssertionError('QA emulator did not boot')
assert q.adb('emu','avd','name').stdout.decode().splitlines()[0]=='MoaQA35'
result={'status':'running','app_changed':False,'timeout_changed':False,'rows':[]}
beta=base/'final/Agattok-0.5.24-beta-code38.apk'
result['install']=delivery.install(beta)
assert result['install']['verified'] and result['install']['version_code']==38
fixture=base/'performance04/public20.mp4'
result['input_sha256']=hashlib.file_digest(fixture.open('rb'),'sha256').hexdigest()
assert result['input_sha256']=='9d57307e1fbd0ff3329e9ed5eba90d6713e619c9ac5d7b65cc6118a5876d8877'
remote='/sdcard/Download/agattok-delivery-diagnosis.mp4'
assert q.shell('test','-e',remote,check=False).returncode!=0
q.adb('push',str(fixture),remote)
result['remote_fixture']=remote;dump(result)
try:
    for index,mode in enumerate(sys.argv[2:] or ('quiet','polling','quiet')):
        print('SETUP',index,mode,flush=True)
        delivery.import_video(Path(remote).name);selection()
        samples=[];stop=threading.Event()
        def sample():
            while not stop.is_set():samples.append([time.monotonic(),memory()]);stop.wait(1)
        monitor=threading.Thread(target=sample,daemon=True);monitor.start()
        row={'mode':mode,'host_available_before':memory()};result['rows'].append(row)
        q.tap_text('영상 + 소리로 번역하기')
        begun=time.monotonic();print('ANALYSIS',index,mode,flush=True)
        try:
            if mode=='quiet':time.sleep(46)
            nodes=q.wait_ui('장면과 함께 저장하기',seconds=12 if mode=='quiet' else 60)
            row['verdict']='pass'
        except Exception as e:
            row.update(verdict='fail',error=str(e))
        finally:
            stop.set();monitor.join(2)
            row.update(observed_seconds=round(time.monotonic()-begun,3),host_available_min=min(x[1] for x in samples),host_samples=samples)
            q.trace('analysis-'+str(index));q.screenshot('analysis-'+str(index));dump(result)
            print('RESULT',index,row['verdict'],row['host_available_min'],flush=True)
    result['status']='completed'
finally:
    q.shell('am','force-stop',q.PACKAGE)
    q.shell('rm','-f',remote)
    result['fixture_removed']=q.shell('test','-e',remote,check=False).returncode!=0
    dump(result)
print(json.dumps({'status':result['status'],'verdicts':[r['verdict'] for r in result['rows']]}),flush=True)
