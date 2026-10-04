"""Audit recorded AST candidate deltas. Never opens media or runs a model/policy.

Actual recorded Kotlin decisions are retained. AST-only scores without a YAMNet
baseline remain unknown; policy versions, source hashes and replay modes stay
separate. Per-source rows are private and may be written only below work/.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path

MODEL_SHA256 = '3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
POLICY_V2_SHA256 = 'a17ca8200627f5d4bb069ca537ba6aead430f0b86c36d6ccc82c0de4d89a9e92'
POLICY_FILES = ('BabbleSupportPolicy.kt', 'SoundTimeline.kt', 'AudioCleanup.kt', 'SoundAnalyzer.kt')


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path: Path):
    if path.suffix != '.json' or path.stat().st_size > 16 * 1024 * 1024:
        raise ValueError('Expected bounded JSON metadata, never media/model bytes')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique)


def verify_model_hash(observed: str) -> None:
    if observed != MODEL_SHA256:
        raise ValueError('Model hash differs from the fixed AST INT8 evidence')


def verify_source_hashes(recorded: dict, current: dict) -> None:
    if recorded != current:
        raise ValueError('Recorded policy/source hashes differ; do not aggregate as current v2')


def classify_change(baseline: dict | None, candidate: dict | None,
                    label_evidence: str, expected_events: list | None) -> dict:
    value = dict(transition='unknown', label_assessment='exact_label_unknown', independent_positive=False,
                 confirmed_new_false_acceptance=False, preservation_violation=False)
    if baseline is None or candidate is None:
        return value
    for decision in (baseline, candidate):
        if decision.get('state') not in ('accepted', 'abstained', 'blocked') or not isinstance(decision.get('events'), list):
            raise ValueError('Malformed recorded decision')
        if decision['state'] == 'accepted' and not decision['events']:
            raise ValueError('Accepted decision has no recorded event')
        if decision['state'] != 'accepted' and decision['events']:
            raise ValueError('Unaccepted decision has recorded accepted events')
    before, after = baseline['state'], candidate['state']
    if before == 'accepted':
        if after != 'accepted':
            transition = 'lost_acceptance'
        elif set(baseline['events']) == set(candidate['events']):
            transition = 'preserved_acceptance'
        else:
            transition = 'changed_acceptance'
    elif after == 'accepted':
        transition = 'added_acceptance' if before == 'abstained' else 'accepted_blocked_input'
    elif before == after == 'abstained':
        transition = 'retained_abstention'
    elif before == after == 'blocked':
        transition = 'retained_blocked_input'
    else:
        transition = 'changed_unaccepted_state'
    value['transition'] = transition
    value['preservation_violation'] = transition in ('lost_acceptance', 'changed_acceptance', 'accepted_blocked_input')
    if expected_events is None:
        return value
    matches = set(candidate['events']) == set(expected_events) and after == 'accepted'
    if label_evidence == 'independent_exact':
        if transition == 'added_acceptance':
            value.update(label_assessment='matches_independent_label' if matches else 'new_false_acceptance',
                         independent_positive=matches, confirmed_new_false_acceptance=not matches)
        elif transition == 'preserved_acceptance' and not matches:
            value['label_assessment'] = 'preserved_baseline_label_mismatch'
        else:
            value['label_assessment'] = 'matches_independent_label' if matches else 'independent_label_not_recovered'
    elif label_evidence == 'user_asserted_development':
        value['label_assessment'] = 'matches_user_development_label' if matches else 'user_development_label_not_recovered'
    elif label_evidence == 'weak_source_title':
        value['label_assessment'] = 'agrees_with_weak_label' if matches else 'weak_label_disagreement'
    return value


def summarize(rows: list[dict]) -> dict:
    segments = {(r['source_sha256'], r['start_ms'], r['end_ms']) for r in rows}
    source_intervals = defaultdict(list)
    for source, start, end in segments:
        source_intervals[source].append((start, end))
    overlaps = sum(max(a[0], b[0]) < min(a[1], b[1])
                   for intervals in source_intervals.values()
                   for i, a in enumerate(intervals) for b in intervals[i+1:])
    buckets = defaultdict(list)
    for row in rows:
        buckets[(row['mode'], row['policy_provenance'], row['role'])].append(row)
    strata = []
    for (mode, policy, role), items in sorted(buckets.items()):
        strata.append(dict(mode=mode, policy_provenance=policy, role=role, records=len(items),
            sources=len({r['source_sha256'] for r in items}), groups=len({r['group'] for r in items}),
            transitions=dict(Counter(r['transition'] for r in items)),
            label_evidence=dict(Counter(r['label_evidence'] for r in items))))
    exact = [r for r in rows if r['label_evidence']=='independent_exact' and r['transition']!='unknown']
    return dict(records=len(rows), unique_sources=len(source_intervals), unique_groups=len({r['group'] for r in rows}),
        unique_source_segments=len(segments), overlapping_segment_pairs=overlaps, strata=strata,
        known_delta_records=sum(r['transition']!='unknown' for r in rows),
        unknown_delta_records=sum(r['transition']=='unknown' for r in rows),
        independently_labeled_delta_records=len(exact),
        independent_positive_records=sum(r['independent_positive'] for r in exact),
        confirmed_new_false_acceptance_records=sum(r['confirmed_new_false_acceptance'] for r in exact) if exact else None,
        preservation_violation_records=sum(r['preservation_violation'] for r in rows),
        accuracy_validated=False, model_inference_calls=0, media_files_read=0,
        replay_modes_are_not_independent_samples=True)


def audio_decision(audio: dict) -> dict:
    events = audio['events']
    if not isinstance(events, list) or any(not isinstance(event,str) for event in events):
        raise ValueError('Invalid recorded event set')
    if events:
        if audio.get('decision_issue') is not None:
            raise ValueError('Contradictory accepted event/issue')
        state = 'accepted'
    else:
        state = 'blocked' if audio.get('problem') is not None else 'abstained'
    return dict(state=state, events=events, issue=audio.get('decision_issue'))


def validate_identity(source: str, start: int, end: int) -> None:
    if len(source)!=64 or any(c not in '0123456789abcdef' for c in source):
        raise ValueError('Missing actual source SHA256; no invented source grouping')
    if type(start) is not int or type(end) is not int or start<0 or end<=start:
        raise ValueError('Invalid recorded source interval')


def with_delta(row: dict, baseline: dict | None, candidate: dict | None,
               label_evidence='none', expected_events=None) -> dict:
    validate_identity(row['source_sha256'],row['start_ms'],row['end_ms'])
    return dict(row, baseline=baseline, candidate=candidate, label_evidence=label_evidence,
                **classify_change(baseline,candidate,label_evidence,expected_events))


def validate_android_completion(case: dict, before: dict, after: dict) -> None:
    if case['operational_status'] != 'completed':
        raise ValueError('Incomplete Android case')
    no_audio = (case['decoded_samples'] == 0 and all(
        d['state'] == 'blocked' and d['issue'] == 'NO_AUDIO' for d in (before, after)))
    if not case['decoded_matches_selected_length'] and not no_audio:
        raise ValueError('Incomplete Android decode')


def android_rows(root: Path) -> list[dict]:
    area = root/'work/ondevice-av/android-owner-probe-02-offline'
    report, execution = read_json(area/'results-private.json'), read_json(area/'execution.json')
    verify_model_hash(report['model_sha256'])
    if (report['status']!='completed' or execution['execution']['status']!='pass'
            or report['production_babble_policy_enabled'] is not False
            or report['evaluation_opt_in'] is not True
            or report['candidate_attached_explicitly_for_evaluation'] is not True):
        raise ValueError('Android evidence is not a completed opt-in run')
    rows=[]
    for case in report['cases']:
        before,after=audio_decision(case['base_audio']),audio_decision(case['candidate_audio'])
        validate_android_completion(case,before,after)
        for decision,key in ((before,'base_interpretation'),(after,'candidate_interpretation')):
            if (decision['state']=='accepted') != case[key]['has_interpretation']:
                raise ValueError('Audio/interpretation acceptance differs; inspect separately')
        policy_hash=report.get('policy_source_sha256')
        if policy_hash is not None and policy_hash!=POLICY_V2_SHA256:
            raise ValueError('Android policy source hash mismatch')
        row=dict(id=case['id'],source_sha256=case['source_sha256'],group='source:'+case['source_sha256'],
            start_ms=case['start_ms'],end_ms=case['end_ms'],role='known_development',mode='android_full_pipeline',
            policy_provenance='v2_source_verified' if policy_hash else 'documented_v2_source_hash_unrecorded')
        pharmacy=case['id'].startswith('owner2_')
        rows.append(with_delta(row,before,after,'user_asserted_development' if pharmacy else 'none', ['BABBLE'] if pharmacy else None))
    return rows


def retained_rows(root: Path, current_hashes: dict) -> tuple[list[dict],dict]:
    area=root/'work/retained-pcm-ast-check'
    report,audit=read_json(area/'results-private.json'),read_json(area/'input-audit-private.json')
    verify_model_hash(report['model_sha256']); verify_source_hashes(report['source_sha256'],current_hashes)
    records={x['id']:x for x in audit}
    if len(records)!=len(audit):
        raise ValueError('Duplicate retained input-audit source ID')
    before={(x['id'],x['mode']):x for x in report['before']}
    after={(x['id'],x['mode']):x for x in report['after']}
    if len(before)!=len(report['before']) or len(after)!=len(report['after']) or before.keys()!=after.keys():
        raise ValueError('Missing/duplicate recorded before-after replay')
    expected={(x['id'],mode) for x in audit if x['duplicate_owner'] is None
              for mode in ('raw_timeline','filtered_aggregate')}
    if set(before)!=expected:
        raise ValueError('Retained replay does not cover every audited source and both modes')
    rows=[]
    for key,value in after.items():
        baseline_record=before[key]; metadata=records[value['id']]
        if metadata['duplicate_owner'] is not None:
            raise ValueError('Duplicate owner source unexpectedly included as a new replay')
        if any(baseline_record[field]!=value[field] for field in ('before_issue','before_events')):
            raise ValueError('Recorded replay baseline identity changed')
        def decision(prefix):
            events=[event for event in value[prefix+'_events'].split(',') if event]
            issue=value[prefix+'_issue']
            return dict(state='accepted' if issue=='ACCEPTED' else 'abstained',events=events,issue=issue)
        mode={'raw_timeline':'raw_timeline_replay','filtered_aggregate':'filtered_aggregate_fallback'}[value['mode']]
        row=dict(id=value['id'],source_sha256=metadata['source_sha256'],group='source:'+metadata['source_sha256'],
            start_ms=metadata['start_ms'],end_ms=metadata['end_ms'],role='new_retained_source',mode=mode,
            policy_provenance='v2_source_verified',supported_window_count=int(value['supported_windows']))
        rows.append(with_delta(row,decision('before'),decision('after'),'user_reported_vocalization',None))
    excluded=[x for x in audit if x['duplicate_owner'] is not None]
    return rows,dict(duplicate_owner_segments_excluded=len(excluded),
                     duplicate_owner_sources_excluded=len({x['source_sha256'] for x in excluded}))


def public_rows(root: Path) -> list[dict]:
    area=root/'work/public-perception'
    metadata={}
    for file in ('attribution.json','expanded-input/manifest.json','target-age-input/manifest.json'):
        for entry in read_json(area/file):
            if entry['id'] in metadata: raise ValueError('Repeated public source metadata')
            metadata[entry['id']]=entry
    rows=[]
    for score_file,case_file in (('int8-frozen-01/scores.json','expanded-input/cases.json'),
                                 ('int8-target-age-01/scores.json','target-age-input/cases.json')):
        report=read_json(area/score_file); verify_model_hash(report['model_sha256'])
        if report['cases_sha256']!=digest(area/case_file): raise ValueError('Recorded cases metadata hash mismatch')
        windows=defaultdict(list)
        for window in report['windows']:
            scores=window['scores']
            if len(scores)!=527 or any(not isinstance(v,(float,int)) or not math.isfinite(v) or not 0<=v<=1 for v in scores.values()):
                raise ValueError('Invalid recorded AST score mapping')
            windows[window['id']].append(window)
        if set(windows)!=set(read_json(area/case_file)): raise ValueError('Incomplete public score source set')
        for ident,items in windows.items():
            entry=metadata[ident]
            if any(type(w['start_ms']) is not int or type(w['end_ms']) is not int or not 0<=w['start_ms']<w['end_ms']<=entry['duration_ms'] for w in items):
                raise ValueError('Recorded AST windows exceed frozen source selection')
            row=dict(id=ident,source_sha256=entry['source_sha256'],group=entry['group'],start_ms=0,
                end_ms=entry['duration_ms'],role='known_development' if ident=='public_babble_eleven_month' else 'new_public_source',
                mode='ast_only_no_yamnet_baseline',policy_provenance='documented_v1_sweep_no_fused_policy_result',
                ast_window_count=len(items),baseline_available=False,fused_candidate_available=False)
            rows.append(with_delta(row,None,None,'weak_source_title',None))
    return rows


def audit(root: Path) -> tuple[list[dict],dict]:
    source=root/'outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage'
    current={name:digest(source/name) for name in POLICY_FILES}
    if current['BabbleSupportPolicy.kt']!=POLICY_V2_SHA256:
        raise ValueError('Current policy differs from the fixed v2 source')
    rows=android_rows(root)
    retained,excluded=retained_rows(root,current); rows.extend(retained); rows.extend(public_rows(root))
    summary=summarize(rows)
    summary.update(excluded,ast_only_sources=sum(r['mode']=='ast_only_no_yamnet_baseline' for r in rows),
                   ast_only_windows=sum(r.get('ast_window_count',0) for r in rows),
                   current_policy_sha256=POLICY_V2_SHA256,policy_reexecuted=False,
                   private_scores_or_phrases_exported=False,product_policy_enabled=False)
    return rows,summary


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); root=args.root.resolve(); output=args.output.resolve()
    if not output.is_relative_to(root/'work') or output==root/'work':
        raise ValueError('Private delta rows must stay under the ignored work directory')
    rows,summary=audit(root)
    output.mkdir(parents=True,exist_ok=False)
    (output/'source-deltas-private.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    (output/'aggregate.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
    return int(summary['preservation_violation_records']>0)


if __name__=='__main__':
    raise SystemExit(main())
