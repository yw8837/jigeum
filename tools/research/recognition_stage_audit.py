"""Aggregate frozen Android probe stages locally, without re-running inference.

The probe reads PCM, not a video container, and evaluates AST unconditionally.
It cannot measure video decode, deployed conditional AST use, visual evidence,
event recall, or infant intent accuracy. Input IDs, phrases, PCM hashes and
per-window observations stay in the private source; only counts are exported.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re

from presence_context_replay import canonical_sha, load, validate_phase, validate_report


ISSUES = {'NO_AUDIO', 'TOO_SHORT', 'INVALID_AUDIO', 'SILENCE', 'QUIET',
          'BACKGROUND_DOMINANT', 'INFANT_UNCONFIRMED', 'MIXED_VOCAL', 'MODEL_FAILURE'}
ANALYSIS_ISSUES = {'NO_AUDIO', 'TOO_SHORT', 'INVALID_AUDIO', 'SILENCE', 'MODEL_FAILURE'}
TIMELINE_ISSUES = {'BACKGROUND_DOMINANT', 'INFANT_UNCONFIRMED', 'MIXED_VOCAL'}


def outcome(phase):
    """Classify the recorded stage, never infer a cause from scores or wording."""
    if not {'analysis_issue', 'decision_issue', 'issue', 'sound'} <= phase.keys():
        raise ValueError('Missing stage fields: old reports need explicit unknown handling')
    analysis, decision, final = (phase[k] for k in ('analysis_issue', 'decision_issue', 'issue'))
    if any(value is not None and value not in ISSUES for value in (analysis, decision, final)):
        raise ValueError('Unrecognized recorded issue')
    if phase['has_interpretation']:
        if (analysis is not None or final is not None or decision not in (None, 'MIXED_VOCAL')
                or not isinstance(phase['sound'], str) or not phase['sound']):
            raise ValueError('Contradictory output and stage issue')
        # The probe's partial flag belongs to the whole SoundTimeline decision.
        # It does NOT record EvidenceInterpreter.partialObservation's result.
        return 'output_after_whole_decision_hold' if decision else 'output'
    if phase['sound'] is not None:
        raise ValueError('Held observation contains an output sound')
    if analysis is not None:
        if analysis not in ANALYSIS_ISSUES or not (analysis == decision == final):
            raise ValueError('Contradictory analysis hold')
        return 'held_analysis_' + analysis.lower()
    if decision is None and final == 'QUIET':
        return 'held_final_audibility'
    if final in TIMELINE_ISSUES and decision == final:
        return 'held_' + final.lower()
    raise ValueError('Unmapped stage transition; do not guess a failure reason')


def union_ms(windows):
    end, total = 0, 0
    for window in windows:
        total += max(0, window['end_ms'] - max(end, window['start_ms']))
        end = max(end, window['end_ms'])
    return total


def summarize(report):
    before = canonical_sha(report)
    count = report.get('case_count')
    if type(count) is not int or not 1 <= count <= 100:
        raise ValueError('Expected 1..100 recorded conditions')
    cases = validate_report(report, count)
    final_counts, whole_issues, transitions = Counter(), Counter(), Counter()
    base_counts, prior_counts = Counter(), Counter()
    pcm_hashes, phrase_sources = set(), defaultdict(set)
    coverage, window_ms = 0, 0
    for case in cases:
        identity = case.get('received_sha256')
        if not isinstance(identity, str) or not re.fullmatch('[a-f0-9]{64}', identity):
            raise ValueError('Missing exact PCM identity')
        # The legacy validator covers prior/final phases. Baseline gets the same
        # structural validation here so a missing baseline cannot be an abstention.
        validate_phase(case.get('base'), case['duration_ms'])
        base_counts[outcome(case['base'])] += 1
        prior_counts[outcome(case['prior_phase'])] += 1
        phase = case['phase']
        final_counts[outcome(phase)] += 1
        whole_issues[phase['decision_issue'] or 'NONE'] += 1
        earlier = 'output' if case['prior_phase']['has_interpretation'] else 'held'
        current = 'output' if phase['has_interpretation'] else 'held'
        transitions[earlier + '_to_' + current] += 1
        pcm_hashes.add(identity)
        if phase['has_interpretation']:
            phrase_sources[phase['phrase']].add(identity)
        windows = case['ast']['windows']
        coverage += union_ms(windows)
        window_ms += sum(w['end_ms'] - w['start_ms'] for w in windows)
    if canonical_sha(report) != before:
        raise ValueError('Audit changed recorded observations')
    return dict(
        schema_version=1, status='completed', scope='frozen_pcm_probe_stage_counts',
        conditions=count, unique_pcm_hashes=len(pcm_hashes), independent_families=None,
        input_scope={'measured_pcm': count}, container_decode_measured=False,
        visual_measured=False, production_ast_execution_measured=False,
        accuracy_measured=False, new_model_forwards=0, product_changed=False,
        base_outcomes=dict(sorted(base_counts.items())),
        prior_outcomes=dict(sorted(prior_counts.items())),
        final_outcomes=dict(sorted(final_counts.items())),
        whole_decision_issues=dict(sorted(whole_issues.items())),
        transitions=dict(sorted(transitions.items())),
        transition_scope='recorded prior_phase to phase; not deployed version comparison',
        recorded_ast_union_ms=coverage, recorded_ast_window_ms=window_ms,
        precise_event_boundaries_measured=False,
        phrase_groups_across_distinct_pcm=sum(len(v) > 1 for v in phrase_sources.values()),
        phrase_collision_proves_wrong_result=False,
        current_product_inference_measured=False,
        private_rows_exported=False)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--label', required=True, help='Public name for this historical measurement')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args(argv)
    data = load(args.input)  # Existing bounded reader rejects duplicate keys/NaN.
    actual_hash = hashlib.sha256(args.input.read_bytes()).hexdigest()
    if actual_hash != args.sha256:
        raise ValueError('Frozen input hash mismatch')
    result = summarize(data)
    if (hashlib.sha256(args.input.read_bytes()).hexdigest() != actual_hash
            or canonical_sha(load(args.input)) != canonical_sha(data)):
        raise ValueError('Frozen input changed during audit')
    result.update(source_label=args.label, source_report_sha256=actual_hash,
                  source_model_sha256=data['model_sha256'],
                  audit_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    # Exclusive creation protects previous evidence and prevents input overwrite.
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write('\n')
    print(json.dumps({'conditions': result['conditions'], 'final_outcomes': result['final_outcomes']}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
