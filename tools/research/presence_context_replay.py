"""Read frozen Android scores without changing decisions or running a model."""
import argparse
from collections import Counter
from dataclasses import asdict
import hashlib
import json
import math
from pathlib import Path
import re
import sys

from ast_device_benchmark import storage_snapshot
from infant_head_screen import ROOT, digest, offline, write
from ondevice_av_probe import validate_output_path

MODEL_SHA = '4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a'
INPUTS = (
    ('work/infant-presence-android-20261007/pcm-04/paired-results-private.json',
     '640f2eea851852ab9c12fb038ebbc6af558309c0e0e0fe7944f3f9e54a2e4960', 36),
    ('work/infant-presence-android-20261007/pcm-05/paired-results-private.json',
     '35bbb62dfd77ec001abddc939d23e71bfdcb1a801f20e96805b43d8f6755b8c5', 7),
    ('work/public-infant-followup-20261007/android01/paired-results.json',
     '9106d91ab1a99cee0e1608e5120220536d8c090c944350604f09bbfb88f274b2', 8),
)
EMOTIONS = ('Baby cry, infant cry', 'Baby laughter', 'Crying, sobbing', 'Laughter',
            'Screaming', 'Giggle', 'Snicker', 'Belly laugh', 'Chuckle, chortle', 'Whimper', 'Wail, moan')
# Eleven emotional class names, plus Television: the same twelve contrary labels
# as InfantVoiceSupportPolicy. Babbling and Child speech are also retained.
REQUIRED = set(EMOTIONS) | {'Television', 'Babbling', 'Child speech, kid speaking'}
YAM_FLOORS = {'울음': .35, '아기 웃음 후보': .35, '울음(화자 미확인)': .65,
              '웃음(화자 미확인)': .65, '비명(화자 미확인)': .65}
MAX_BYTES = 8 * 1024**2
EVIDENCE_ROOT = ROOT / 'work/presence-context-replay-20261007'
CODE_FILES = ('tools/research/presence_context_replay.py', 'tools/research/infant_head_screen.py',
              'tools/research/ast_device_benchmark.py', 'tools/research/ondevice_av_probe.py',
              'outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/InfantVoiceSupportPolicy.kt',
              'outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BabbleSupportPolicy.kt')


def code_pins():
    return {name: digest(ROOT / name) for name in CODE_FILES}


def validate_replay_output(path):
    validate_output_path(path)
    if not path.resolve().is_relative_to(EVIDENCE_ROOT.resolve()) or path.resolve() == EVIDENCE_ROOT.resolve():
        raise ValueError('Replay output must be a new directory below its dedicated evidence root')


def canonical_sha(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=True, sort_keys=True,
        separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def reject_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key')
        result[key] = value
    return result


def load(path):
    if path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= MAX_BYTES:
        raise ValueError('Report file/size invalid')
    return json.loads(path.read_text('utf-8'), object_pairs_hook=reject_duplicates,
        parse_constant=lambda value: (_ for _ in ()).throw(ValueError('Nonfinite JSON constant')))


def integer(value, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('Integer range invalid')
    return value


def probability(value):
    if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 1:
        raise ValueError('Probability or RMS range invalid')
    return value


def expected_windows(duration):
    if duration <= 10000:
        return [(0, duration)]
    starts = list(range(0, duration - 10000 + 1, 5000))
    if starts[-1] != duration - 10000:
        starts.append(duration - 10000)
    return [(start, start + 10000) for start in starts]


def validate_phase(phase, duration):
    if not isinstance(phase, dict):
        raise ValueError('Missing recorded phase')
    if (type(phase.get('has_interpretation')) is not bool or not isinstance(phase.get('events'), list)
            or not isinstance(phase.get('frames'), list) or not isinstance(phase.get('cues'), list)):
        raise ValueError('Incomplete recorded phase')
    if phase['has_interpretation']:
        if phase.get('issue') is not None or not isinstance(phase.get('phrase'), str) or not phase['phrase']:
            raise ValueError('Inconsistent approved phase')
    elif phase.get('phrase') is not None or not isinstance(phase.get('issue'), str):
        raise ValueError('Inconsistent held phase')
    starts = []
    for frame in phase['frames']:
        starts.append(integer(frame.get('start_ms'), 0, duration - 1))
        validate_cues(frame.get('cues'))
    if starts != sorted(set(starts)) or phase.get('frame_starts_ms') != starts:
        raise ValueError('Invalid recorded frame ordering')
    validate_cues(phase['cues'])
    canonical_sha(phase)  # Reject nonfinite values elsewhere before preservation.


def validate_cues(cues):
    if not isinstance(cues, list):
        raise ValueError('Missing cue list')
    names = []
    for cue in cues:
        if not isinstance(cue, dict) or not isinstance(cue.get('label'), str) or not 1 <= len(cue['label']) <= 120:
            raise ValueError('Invalid cue label')
        names.append(cue['label'])
        probability(cue.get('score'))
    if len(names) != len(set(names)):
        raise ValueError('Duplicate cue label')


def validate_report(report, count):
    if not isinstance(report, dict) or report.get('status') != 'completed' or report.get('completed') is not True:
        raise ValueError('Report not completed')
    if report.get('model_sha256') != MODEL_SHA or report.get('quality_validated') is not False:
        raise ValueError('Model or quality contract invalid')
    if (report.get('schema_version') != 1 or report.get('evaluation_opt_in') is not True or
            report.get('infant_presence_evaluated') is not True):
        raise ValueError('Missing explicit research evaluation contract')
    if report.get('ast_evaluated_even_when_yamnet_not_eligible') is not True:
        raise ValueError('Missing unconditional research AST evidence')
    run = report.get('run_id')
    if not isinstance(run, str) or not re.fullmatch('[0-9a-f]{32}', run):
        raise ValueError('Invalid run ID')
    cases = report.get('cases')
    if (not isinstance(cases, list) or len(cases) != count or
            type(report.get('case_count')) is not int or report['case_count'] != count or
            type(report.get('measured_count')) is not int or report['measured_count'] != count):
        raise ValueError('Incomplete case count')
    ids = set()
    for case in cases:
        name = case.get('id')
        if not isinstance(name, str) or not re.fullmatch('[A-Za-z0-9_-]{1,100}', name) or name in ids:
            raise ValueError('Duplicate or invalid case ID')
        ids.add(name)
        if case.get('run_id') != run or case.get('status') != 'measured' or case.get('quality_validated') is not False:
            raise ValueError('Stale or incomplete case')
        samples = integer(case.get('samples'), 16000, 320000)
        duration = integer(case.get('duration_ms'), 1000, 20000)
        # Recorded selection length rounds up to milliseconds; the actual AST
        # endpoints round down from samples. Preserve both clocks explicitly.
        if duration != (samples + 15) // 16 or case.get('received_bytes') != samples * 4:
            raise ValueError('Recorded PCM length mismatch')
        if type(case.get('eligible')) is not bool:
            raise ValueError('Missing recorded eligibility')
        validate_phase(case.get('prior_phase'), duration)
        validate_phase(case.get('phase'), duration)
        ast = case.get('ast')
        if not isinstance(ast, dict) or ast.get('model_sha256') != MODEL_SHA or ast.get('problem') is not None:
            raise ValueError('Incomplete AST evidence')
        windows = ast.get('windows')
        if (not isinstance(windows, list) or type(ast.get('returned_windows')) is not int or
                ast['returned_windows'] != len(windows)):
            raise ValueError('Missing AST windows')
        ranges = []
        for window in windows:
            ranges.append((integer(window.get('start_ms'), 0, duration - 1),
                           integer(window.get('end_ms'), 1, duration)))
            probability(window.get('infant_presence'))
            probability(window.get('strongest_second_rms'))
            scores = window.get('scores')
            if not isinstance(scores, dict) or not REQUIRED <= scores.keys():
                raise ValueError('Missing contrary scores')
            for value in scores.values():
                probability(value)
        if ranges != expected_windows(samples // 16):
            raise ValueError('Incomplete or shifted AST coverage')
    return cases


def diagnose(case):
    """Sidecar only: preserve phase and never authorize a phrase or event label."""
    prior = case['prior_phase']
    windows = case['ast']['windows']
    clues = [dict(start_ms=w['start_ms'], end_ms=w['end_ms'], score=w['infant_presence'],
                  strongest_second_rms=w['strongest_second_rms']) for w in windows
             if w['infant_presence'] >= .80 and w['strongest_second_rms'] >= .003]
    yam = sorted({cue['label'] for cue in prior['cues'] +
        [c for frame in prior['frames'] for c in frame['cues']]
        if cue['label'] in YAM_FLOORS and cue['score'] >= YAM_FLOORS[cue['label']]})
    ast_emotions = sorted({label for window in windows for label in EMOTIONS if window['scores'][label] >= .30})
    tv = any(window['scores']['Television'] >= .30 for window in windows)
    if case['phase']['has_interpretation']:
        category = 'APPROVED_OUTPUT_PRESERVED'
    elif not clues:
        category = 'HELD_NO_BROAD_PRESENCE'
    elif tv:
        category = 'HELD_PRESENCE_TV_CONTEXT'
    elif yam or ast_emotions or prior['issue'] == 'MIXED_VOCAL':
        category = 'HELD_PRESENCE_EMOTIONAL_CONTEXT'
    else:
        category = 'HELD_PRESENCE_OTHER_GUARD'
    return dict(id=case['id'], category=category, recorded_eligible=case['eligible'],
        prior_phase_sha256=canonical_sha(prior), phase_sha256=canonical_sha(case['phase']),
        prior_issue=prior['issue'], current_issue=case['phase']['issue'],
        sidecar=dict(scope='BROAD_CONTEXT', candidate_only=True, windows=clues,
                     yam_emotional_flags=yam, ast_emotional_flags=ast_emotions, television_flag=tv,
                     speaker='UNVERIFIED', emotion='HELD', subtype='HELD', onset=None, phrase=None,
                     product_authorization=False, event_truth_verified=False))


def replay():
    pins = code_pins()
    inputs, rows = [], []
    for name, sha, count in INPUTS:
        path = ROOT / name
        report = load(path)
        if digest(path) != sha:
            raise ValueError('Frozen input hash mismatch')
        for case in validate_report(report, count):
            before = canonical_sha(case)
            row = diagnose(case)
            if canonical_sha(case) != before:
                raise ValueError('Recorded input mutated')
            rows.append(dict(input_index=len(inputs), **row))
        inputs.append(dict(path=name, sha256=sha, cases=count))
    for item in inputs:
        if digest(ROOT / item['path']) != item['sha256']:
            raise ValueError('Frozen input changed during replay')
    if code_pins() != pins:
        raise ValueError('Replay or product policy source changed during replay')
    return dict(status='completed', research_only=True, model_sha256=MODEL_SHA,
        case_count=len(rows), inputs=inputs, code_sha256=pins,
        categories=dict(Counter(r['category'] for r in rows)),
        product_changed=False, model_forwards=0, media_read=False, training=False,
        quality_validated=False, new_phrase_approvals=0, rows=rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--worker', action='store_true', help=argparse.SUPPRESS)
    args = parser.parse_args()
    output = args.output.resolve()
    validate_replay_output(output)
    offline()
    if args.worker:
        if (not output.is_dir() or not (output / 'storage-start.json').is_file() or
                (output / 'results.json').exists()):
            raise ValueError('Invalid or stale worker directory')
        result = replay()
        write(output / 'results.json', result)
        print(json.dumps(dict(status=result['status'], cases=result['case_count'], categories=result['categories'])))
        return
    output.mkdir(parents=True, exist_ok=False)
    write(output / 'storage-start.json', storage_snapshot(output))
    from bounded_process import run_bounded
    process = run_bounded([sys.executable, '-B', str(Path(__file__).resolve()), '--output', str(output), '--worker'],
        cwd=ROOT, log_path=output / 'run.log', timeout_seconds=60, memory_limit_bytes=256*1024**2)
    write(output / 'process.json', asdict(process))
    write(output / 'storage-end.json', storage_snapshot(output))
    if process.exit_code != 0 or not process.cleanup_complete or process.remaining_job_processes != 0:
        raise RuntimeError('Replay worker failed; inspect bounded log')
    result = load(output / 'results.json')
    if result != replay():
        raise ValueError('Partial or inconsistent replay completion')
    print(json.dumps(dict(status=result['status'], cases=result['case_count'], categories=result['categories'])))


if __name__ == '__main__':
    main()
