from pathlib import Path
from dataclasses import asdict
import json, os, sys, shutil

root = Path(__file__).resolve().parents[2]
base = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'tools/research'))
from bounded_process import run_bounded

mode = sys.argv[1]
assert mode in ('beta', 'release')
final = base / 'final' / ('Agattok-0.5.24-' + mode + '-code38.aab')
assert not final.exists() and not final.with_suffix('.apk').exists()
env = dict(os.environ, JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot',
           ANDROID_HOME='C:/MoaSdk', ANDROID_SDK_ROOT='C:/MoaSdk')
for key in ('OMX_ROOT', 'OMX_STATE_ROOT'):
    env.pop(key, None)
argv = [shutil.which('pwsh'), '-NoProfile', '-File', str(root / 'tools/release/build-play-bundle.ps1'),
        '-Purpose', 'closed-test' if mode == 'beta' else 'internal', '-JavaHome', env['JAVA_HOME'],
        '-AndroidSdk', env['ANDROID_HOME'], '-Destination', str(final)]
if mode == 'beta':
    argv += ['-AstBabbleSupport', '-InfantPresenceSupport', '-InfantCaptionSupport', '-InfantArmProof',
             str(root / 'work/caption-arm-20261007-run01/result.json'), '-ProofPython', sys.executable]
r = run_bounded(argv, cwd=root, log_path=base / (mode + '-build01.log'), timeout_seconds=900,
                memory_limit_bytes=5 * 1024**3, env=env, max_output_bytes=2 * 1024**2)
(base / (mode + '-build01.json')).write_text(json.dumps(asdict(r), indent=2), 'utf8')
assert r.exit_code == 0 and r.termination in (None, 'orphan_cleanup') and r.cleanup_complete
assert not r.remaining_job_processes and not r.memory_limit_hit and not r.output_truncated
assert final.is_file() and b'BUILD SUCCESSFUL' in (base / (mode + '-build01.log')).read_bytes()
apk = root / 'outputs/MoaAndroid/app/build/outputs/apk' / mode / ('app-' + mode + '.apk')
shutil.copyfile(apk, final.with_suffix('.apk'))
print(json.dumps(asdict(r)))
