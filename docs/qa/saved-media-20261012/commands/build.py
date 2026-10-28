from pathlib import Path
from dataclasses import asdict
import json, os, sys
root=Path(__file__).resolve().parents[2];base=Path(__file__).parent
run=sys.argv[1] if len(sys.argv)>1 else 'build01'
sys.path.insert(0,str(root/'tools/research'))
from bounded_process import run_bounded
env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot',ANDROID_HOME='C:/MoaSdk',ANDROID_SDK_ROOT='C:/MoaSdk')
env.pop('OMX_ROOT',None);env.pop('OMX_STATE_ROOT',None)
init=base/'isolated-build.gradle';build=root/'work/interval-evidence-20261009/build'
init.write_text("allprojects { p -> if (p.path == ':app') { p.layout.buildDirectory.set(new File('"+build.as_posix()+"')) } }\n",encoding='utf8')
r=run_bounded(['cmd.exe','/c','gradlew.bat',':app:assembleDebug',':app:assembleDebugAndroidTest','-I',str(init),
    '-PagattokAstProbe=true','-PagattokAstCandidate=true','-PagattokInfantPresenceCandidate=true',
    '-PagattokInfantCaptionCandidate=true','-Pkotlin.compiler.execution.strategy=in-process',
    '--max-workers=1','--console=plain','--no-daemon'],cwd=root/'outputs/MoaAndroid',log_path=base/(run+'.log'),
    timeout_seconds=600,memory_limit_bytes=5*1024**3,env=env,max_output_bytes=2*1024**2)
(base/(run+'.json')).write_text(json.dumps(asdict(r),indent=2),'utf8')
print(json.dumps(asdict(r)))
assert r.exit_code==0 and r.termination in (None,'orphan_cleanup') and r.cleanup_complete and not r.remaining_job_processes
assert not r.output_truncated and not r.memory_limit_hit and 'BUILD SUCCESSFUL' in (base/(run+'.log')).read_text('utf8')
