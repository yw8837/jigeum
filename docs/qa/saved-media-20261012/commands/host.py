from pathlib import Path
import json, subprocess, sys, hashlib
root=Path(__file__).resolve().parents[2];base=Path(__file__).resolve().parent
src=root/'outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage'
tests=root/'outputs/MoaAndroid/app/src/androidTest/java/com/moa/babylanguage'
out=base/sys.argv[1];out.mkdir(exist_ok=False)
cache=Path('C:/Users/최용우/.gradle/caches/modules-2/files-2.1')
def jar(g,n,v):
 f=list((cache/g/n/v).glob('*/*.jar'));assert len(f)==1;return str(f[0])
libs=[jar('org.jetbrains.kotlin',n,'2.2.10') for n in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']]
libs += [jar('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0'),jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core','1.3')]
cp=';'.join(libs);java='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe'
original=(src/'SoundAnalyzer.kt').read_text('utf8');types=original[original.index('data class SoundCue'):original.index('/** YAMNet')]
types += '\ninternal object SoundAnalyzer {\n' + original[original.index('    internal fun cueLabel'):original.index('    suspend fun analyze')] + '\n}\n'
v=(src/'VideoImport.kt').read_text('utf8')
vt=v[v.index('internal enum class VisualEvidenceStatus'):v.index('internal data class VideoFrameSummary')]
start=vt.index('    companion object {');end=vt.index('internal data class VisualEvidence')
vt=vt[:start]+'}\n\n'+vt[end:]
(out/'Types.kt').write_text('package com.moa.babylanguage\n'+types+'\n'+vt,encoding='utf8')
e=(src/'ClipEvidence.kt').read_text('utf8')
if '--baseline' in sys.argv:
 e=subprocess.run(['git','show','6499256:outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt'],cwd=root,capture_output=True,check=True).stdout.decode('utf8')
e=e[:e.index('/** All decoders')]+e[e.index('/** A display rule'):]
e='\n'.join(x for x in e.splitlines() if not x.startswith('import android') and not x.startswith('import kotlinx'))
(out/'ClipEvidence.kt').write_text(e,encoding='utf8')
files=[src/n for n in ['SoundTimeline.kt','InfantVoiceSupportPolicy.kt','SoundPhaseRecovery.kt','BabbleSupportPolicy.kt','AstAudioTypes.kt','AudioCleanup.kt','VisualTimeline.kt','VisualSoundAlignment.kt','AudioTimestampAudit.kt']]
if '--baseline' in sys.argv:
 baseline=out/'BabyExpression.kt'
 baseline.write_bytes(subprocess.run(['git','show','6499256:outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BabyExpression.kt'],cwd=root,capture_output=True,check=True).stdout)
 files.append(baseline)
elif (src/'BabyExpression.kt').exists():files.append(src/'BabyExpression.kt')
names=sys.argv[2].split(',') if len(sys.argv)>2 else ['ExpressionVarietyTest']
cadence=(src/'VideoFrameDecoder.kt').read_text('utf8')
cadence=cadence[cadence.index('/** Select the first'):cadence.index('internal data class DecodedVideoFrame')]
(out/'Cadence.kt').write_text('package com.moa.babylanguage\n'+cadence,encoding='utf8')
files.append(out/'Cadence.kt')
files += [tests/(n+'.kt') for n in names]
args=[java,'-Xmx768m','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(out/'classes'),str(out/'Types.kt'),str(out/'ClipEvidence.kt'),*[str(f) for f in files]]
r=subprocess.run(args,capture_output=True,timeout=90);(out/'compile.log').write_bytes(r.stdout+r.stderr)
report={'compile_exit':r.returncode,'android_inference':False,'source_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [*files,src/'ClipEvidence.kt']}}
if r.returncode==0:
 r=subprocess.run([java,'-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8','-cp',str(out/'classes')+';'+cp,'org.junit.runner.JUnitCore',*['com.moa.babylanguage.'+n for n in names]],capture_output=True,timeout=30)
 (out/'tests.log').write_bytes(r.stdout+r.stderr);report['test_exit']=r.returncode
print((r.stdout+r.stderr).decode('utf8','replace'))
(out/'result.json').write_text(json.dumps(report,indent=2),encoding='utf8');sys.exit(r.returncode)
