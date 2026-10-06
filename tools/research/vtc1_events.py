"""Replay recorded VTC1 scores through pinned upstream Binarize; no inference.

No media, model weights, network, or product decisions. Restricted Timeline
adapter supplies only add/iteration/support/gaps to original Binarize code.
"""
import argparse
from dataclasses import dataclass
import json
import math
import numbers
from pathlib import Path
import sys
from types import ModuleType
from typing import Iterable, Iterator, List, Optional, Tuple, Union
import warnings

from ondevice_av_probe import validate_output_path
from vtc1_probe import PIN, LABELS, ONSET, digest, extract_classes, native_coverage, verify_sources


class IntervalTimeline:
    """Finite intervals only; minimal package glue for native Binarize."""
    def __init__(self, segments=()):
        self.segments = list(segments)

    def __iter__(self):
        return iter(sorted(self.segments, key=lambda s: (s.start, s.end)))

    def add(self, segment):
        self.segments.append(segment)

    def support(self):
        result = []
        for segment in self:
            if not segment:
                continue
            if result and segment.start <= result[-1].end:
                result[-1] = type(segment)(result[-1].start, max(result[-1].end, segment.end))
            else:
                result.append(segment)
        return type(self)(result)

    def gaps(self):
        segments = list(self.support())
        return type(self)(type(a)(a.end, b.start) for a, b in zip(segments, segments[1:]))


def native_core(assets):
    import numpy as np
    module = ModuleType('agattok_vtc1_replay_core')
    sys.modules[module.__name__] = module
    ns = module.__dict__
    ns.update(np=np, math=math, numbers=numbers, warnings=warnings, Timeline=IntervalTimeline,
              dataclass=dataclass,
              SEGMENT_PRECISION=1e-6, Alignment=str, CropMode=str, Optional=Optional,
              Union=Union, Tuple=Tuple, List=List, Iterator=Iterator, Iterable=Iterable)
    evidence = []
    core = assets/'source/core/pyannote/core'
    for path, symbols in ((core/'segment.py', ('Segment', 'SlidingWindow')),
                          (core/'feature.py', ('SlidingWindowFeature',)),
                          (assets/'source/pyannote/pyannote/audio/utils/signal.py', ('Binarize',))):
        evidence.extend(extract_classes(path, symbols, ns))
    return ns, evidence


def supported_grid(samples, ns):
    import numpy as np
    features = ns['SlidingWindowFeature'](np.empty((samples, 1), dtype=np.float32),
        ns['SlidingWindow'](start=-.5/16000, duration=1/16000, step=1/16000))
    resolution = ns['SlidingWindow'](start=0, duration=325/16000, step=27/16000)
    coverage, chunks = native_coverage(features, resolution, ns['SlidingWindow'](duration=2., step=.5))
    starts = np.arange(len(coverage))*27
    return starts[(starts+325 <= samples) & (coverage > 0)], coverage, chunks, resolution


def case_events(case, ns):
    import numpy as np
    starts = np.asarray(case['frame_start_samples'])
    # Exact PCM samples come from frame contract/corpus, never duration_ms rounding.
    samples = case['pcm_samples']
    expected, coverage, chunks, resolution = supported_grid(samples, ns)
    if (case['labels'] != list(LABELS) or not np.array_equal(starts, np.arange(len(starts))*27)
            or not np.array_equal(np.asarray(case['frame_end_samples']), starts+325)):
        raise ValueError('Recorded coordinate/label contract differs')
    scores = np.asarray(case['scores'], dtype=np.float32)
    if (scores.shape != (len(starts), 5) or not np.isfinite(scores).all()
            or np.any((scores < 0) | (scores > 1))):
        raise ValueError('Recorded score contract differs')
    # Existing run01 used complete RFs only. Audit omitted-coverage edges without
    # re-inference and prove each excluded row had native zero-valued coverage.
    keep = np.isin(starts, expected)
    if not np.array_equal(starts[keep], expected) or np.any(scores[~keep] != 0):
        raise ValueError('Unsupported edge has nonzero score or grid has missing interior')
    if not np.all(np.diff(expected) == 27):
        raise ValueError('Noncontiguous supported grid cannot be collapsed')
    window = ns['SlidingWindow'](start=float(expected[0])/16000,
                                duration=325/16000, step=27/16000)
    feature = ns['SlidingWindowFeature'](scores[keep], window)
    events = {}
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', DeprecationWarning)
        for column, label in enumerate(LABELS):
            binarize = ns['Binarize'](onset=ONSET[column], offset=ONSET[column],
                min_duration_on=.1, min_duration_off=.1, pad_onset=0., pad_offset=0.)
            events[label] = [dict(start_ms=s.start*1000, end_ms=s.end*1000,
                                  duration_ms=s.duration*1000) for s in binarize.apply(feature, dimension=column)]
    return dict(id=case['id'], expected=case.get('expected'), role=case.get('role'),
        source_pcm_sha256=case['source_pcm_sha256'], pcm_samples=samples,
        supported_frames=len(expected), excluded_zero_coverage_rows=int((~keep).sum()),
        native_grid_rows=len(coverage), native_zero_coverage_rows=int((coverage == 0).sum()),
        chunks=chunks, events=events,
        event_durations_ms={label:sum(e['duration_ms'] for e in values) for label,values in events.items()})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--scores', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.assets, args.scores, args.corpus, args.output):
        validate_output_path(path.resolve())
    assets = args.assets.resolve(strict=True)
    pin = json.loads(PIN.read_text('utf-8'))
    verify_sources(assets, pin)
    report = json.loads(args.scores.read_text('utf-8'))
    manifest = json.loads((args.corpus/'manifest.json').read_text('utf-8'))
    if (report['status'] != 'completed' or report['model_sha256'] != pin['weight']['sha256']
            or report['corpus_manifest_sha256'] != [digest(args.corpus/'manifest.json')]):
        raise ValueError('Run/corpus pin mismatch')
    if len(report['cases']) != len(manifest):
        raise ValueError('Partial run')
    by_id = {entry['id']:entry for entry in manifest}
    ns, ast_evidence = native_core(assets)
    rows = []
    seen = set()
    for case in report['cases']:
        entry = by_id[case['id']]
        if case['id'] in seen or entry['pcm_sha256'] != case['source_pcm_sha256']:
            raise ValueError('Duplicate/mismatched input')
        seen.add(case['id'])
        rows.append(case_events(dict(case, pcm_samples=entry['pcm_samples']), ns))
    result = dict(status='completed', classifier_forwards=0, media_read=False, product_changed=False,
        quality_validated=False, commercial_permission_confirmed=False,
        replay_sha256=digest(Path(__file__)), input_scores_sha256=digest(args.scores),
        corpus_manifest_sha256=digest(args.corpus/'manifest.json'), extracted_classes=ast_evidence,
        threshold_policy='original onset=offset, min_on .1 before min_off .1, pads0', cases=rows)
    with args.output.open('x', encoding='utf-8') as file:
        json.dump(result, file, indent=2)
    print(json.dumps(dict(status='completed', cases=len(rows), classifier_forwards=0, product_changed=False)))


if __name__ == '__main__':
    main()
