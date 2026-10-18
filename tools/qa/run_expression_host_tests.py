"""Run the real, platform-free expression/interpreter Kotlin on the host.

Uses installed JDK + cached Kotlin/JUnit only; no Android build, model download,
phone, or baby media. Android decoder/model entry points are excluded by named
source boundaries, never replaced with a second interpreter implementation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'outputs/MoaAndroid/app/src'
PACKAGE = 'com/moa/babylanguage'
SUITES = ['ExpressionVarietyTest', 'ExpressionExpansionTest', 'LongExpressionTest',
          'EvidenceInterpreterTest', 'IntervalEvidenceTest', 'VocalKindEvidenceTest',
          'VisualTemporalAlignmentTest']


def between(text, start, end):
    assert text.count(start) == 1 and text.count(end) == 1, (start, end)
    return text[text.index(start):text.index(end)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True)
    parser.add_argument('--cache', default=str(Path.home() / '.gradle/caches/modules-2/files-2.1'))
    parser.add_argument('--output', required=True)
    parser.add_argument('--suites', nargs='+', default=SUITES)
    args = parser.parse_args()
    out = Path(args.output).resolve()
    if not out.is_relative_to(ROOT / 'work'):
        raise ValueError('Output must be inside this repository work/ directory')
    out.mkdir(parents=True, exist_ok=False)
    src = out / 'src'
    src.mkdir()
    hashes = {}

    def read(name):
        path = APP / 'main/java' / PACKAGE / name
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        return path.read_text(encoding='utf-8')

    def write(name, text):
        (src / name).write_text(text, encoding='utf-8')

    pure = ['SoundTimeline', 'InfantVoiceSupportPolicy', 'SoundPhaseRecovery',
            'BabbleSupportPolicy', 'AstAudioTypes', 'AudioCleanup', 'VisualTimeline',
            'VisualSoundAlignment', 'AudioTimestampAudit', 'BabyExpression']
    if (APP / 'main/java' / PACKAGE / 'BabyExpressionCatalog.kt').exists():
        pure.append('BabyExpressionCatalog')
    for name in pure:
        write(name + '.kt', read(name + '.kt'))
    sound = read('SoundAnalyzer.kt')
    write('SoundTypes.kt', 'package com.moa.babylanguage\n' +
          between(sound, 'data class SoundCue', '/** YAMNet recognizes') +
          'object SoundAnalyzer {\n' +
          between(sound, '    internal fun cueLabel', '    suspend fun analyze(') + '}\n')
    clip = read('ClipEvidence.kt')
    clip = re.sub(r'^import (?:android|kotlinx)\..*\n', '', clip, flags=re.M)
    runtime = between(clip, '/** All decoders', '/** A display rule')
    write('ClipEvidence.kt', clip.replace(runtime, ''))
    # Copy type/summary declarations; landmark measurement belongs to Android.
    original = read('VideoImport.kt')
    visual = original[original.index('internal enum class VisualEvidenceStatus'):original.index('\n}', original.index('internal data class VideoFrameSummary')) + 2]
    measure = between(visual, '    companion object {\n        fun measure', '\ninternal data class VisualEvidence(')
    write('VisualTypes.kt', 'package com.moa.babylanguage\n' + visual.replace(measure, '}\n'))
    audio = read('VideoAudioDecoder.kt')
    write('VideoAudioTypes.kt', 'package com.moa.babylanguage\n' + between(audio, 'internal data class VideoAudioAnalysis', 'internal object VideoAudioDecoder'))
    cadence = read('VideoFrameDecoder.kt')
    write('VideoFrameCadence.kt', 'package com.moa.babylanguage\n' + between(cadence, 'internal class VideoFrameCadence', 'internal data class DecodedVideoFrame'))
    for suite in args.suites:
        path = APP / 'androidTest/java' / PACKAGE / (suite + '.kt')
        hashes[suite + '.kt'] = hashlib.sha256(path.read_bytes()).hexdigest()
        write(suite + '.kt', path.read_text(encoding='utf-8'))

    cache = Path(args.cache)
    def jar(group, artifact, version):
        matches = list((cache / group / artifact / version).glob('*/*.jar'))
        assert len(matches) == 1, (artifact, matches)
        return str(matches[0])
    kotlin = lambda artifact, version='2.2.10': jar('org.jetbrains.kotlin', artifact, version)
    runtime = [kotlin('kotlin-stdlib'), jar('org.jetbrains', 'annotations', '13.0'),
               jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
               jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3')]
    compiler = runtime + [kotlin('kotlin-compiler-embeddable'), kotlin('kotlin-script-runtime'),
                          kotlin('kotlin-daemon-embeddable'), kotlin('kotlin-reflect', '1.6.10')]
    import os
    cp = os.pathsep.join(runtime)
    encoding = ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8']
    command = [args.java, *encoding, '-Xmx768m', '-cp', os.pathsep.join(compiler),
               'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
               '-jvm-target', '17', '-classpath', cp, '-d', str(out / 'tests.jar')]
    result = subprocess.run(command + [str(p) for p in sorted(src.glob('*.kt'))],
                            capture_output=True, text=True, encoding='utf-8', timeout=120)
    (out / 'compile.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        print(result.stdout + result.stderr)
        raise SystemExit(result.returncode)
    result = subprocess.run([args.java, *encoding, '-Xmx512m', '-cp', str(out / 'tests.jar') + os.pathsep + cp,
                             'org.junit.runner.JUnitCore'] + ['com.moa.babylanguage.' + s for s in args.suites],
                            capture_output=True, text=True, encoding='utf-8', timeout=90)
    (out / 'tests.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    (out / 'sources.json').write_text(json.dumps(hashes, indent=2), encoding='utf-8')
    print(result.stdout + result.stderr)
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
