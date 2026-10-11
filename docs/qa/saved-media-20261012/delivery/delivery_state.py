from pathlib import Path
import json,os,shutil,subprocess,sys
base=Path(__file__).parent/'delivery-diagnosis';base.mkdir(exist_ok=True)
phase=sys.argv[1]
payload={'mode':'ultraqa','active':phase not in ('complete','stopped'),'current_phase':phase,'iteration':int(sys.argv[2]) if len(sys.argv)>2 else 1,'scenario_matrix':str(base/'matrix.md')}
env=dict(os.environ)
for k in ('OMX_ROOT','OMX_STATE_ROOT'):env.pop(k,None)
args=['state',phase,'--mode','ultraqa','--json'] if phase in ('read','clear') else ['state','write','--input',json.dumps(payload),'--json']
r=subprocess.run([shutil.which('node'),'C:/Users/최용우/AppData/Roaming/npm/node_modules/oh-my-codex/dist/cli/omx.js',*args],cwd=base,env=env,capture_output=True,timeout=20)
r.check_returncode();result=json.loads(r.stdout)
assert result['cleared'] if phase=='clear' else not result['exists'] if phase=='read' else result['success']
(base/('state-'+phase+'.json')).write_bytes(r.stdout)
print(phase+' verified')
