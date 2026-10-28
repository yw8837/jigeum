"""Replay recorded VTC1 scores through pinned upstream Binarize; no inference.

No media, model weights, network, or product decisions. Restricted Timeline
adapter supplies only add/iteration/support/gaps to original Binarize code.
"""
import argparse
from dataclasses import asdict, dataclass
import json
import math
import numbers
import os
from pathlib import Path
import platform
import re
import sys
from types import ModuleType
from typing import Iterable, Iterator, List, Optional, Tuple, Union
import warnings

from ondevice_av_probe import validate_output_path
from vtc1_probe import PIN, LABELS, ONSET, digest, extract_classes, native_coverage, verify_sources

SCORES_LIMIT_BYTES = 128*1024**2


def read_json(path, max_bytes):
    if not 1 <= path.stat().st_size <= max_bytes:
        raise ValueError('JSON size outside research limit')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(path.read_text('utf-8'), object_pairs_hook=unique)


def validate_manifest(entries):
    if not isinstance(entries, list) or not 1 <= len(entries) <= 100:
        raise ValueError('Expected 1..100 manifest cases')
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError('Invalid manifest case')
        identity, samples = entry.get('id'), entry.get('pcm_samples')
        if (not isinstance(identity, str) or not re.fullmatch('[a-z0-9_-]{1,80}',identity)
                or identity in seen):
            raise ValueError('Manifest ID invalid/duplicated')
        seen.add(identity)
        if (type(samples) is not int or not 16000 <= samples <= 320000
                or entry.get('pcm_bytes') != samples*4 or entry.get('duration_ms') != (samples+15)//16
                or entry.get('sample_rate_hz') != 16000 or entry.get('channels') != 1
                or entry.get('encoding') != 'float32le'
                or not isinstance(entry.get('pcm_sha256'),str)
                or not re.fullmatch('[0-9a-f]{64}',entry['pcm_sha256'])):
            raise ValueError('Manifest PCM contract mismatch')


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
    if type(samples) is not int or not 16000 <= samples <= 320000:
        raise ValueError('PCM samples outside 1..20 second contract')
    features = ns['SlidingWindowFeature'](np.empty((samples, 1), dtype=np.float32),
        ns['SlidingWindow'](start=-.5/16000, duration=1/16000, step=1/16000))
    resolution = ns['SlidingWindow'](start=0, duration=325/16000, step=27/16000)
    coverage, chunks = native_coverage(features, resolution, ns['SlidingWindow'](duration=2., step=.5))
    starts = np.arange(len(coverage))*27
    return starts[(starts+325 <= samples) & (coverage > 0)], coverage, chunks, resolution


def case_events(case, ns):
    import numpy as np
    if (not isinstance(case,dict) or type(case.get('pcm_samples')) is not int
            or not 16000 <= case['pcm_samples'] <= 320000):
        raise ValueError('Recorded PCM samples outside 1..20 second contract')
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


def replay(args):
    import numpy as np
    assets = args.assets.resolve(strict=True)
    pin = json.loads(PIN.read_text('utf-8'))
    if platform.python_version() != pin['runtime']['python'] or np.__version__ != pin['runtime']['numpy']:
        raise ValueError('Replay runtime does not match research pin')
    verify_sources(assets, pin)
    manifest = read_json(args.corpus/'manifest.json', 131072)
    validate_manifest(manifest)
    report = read_json(args.scores, SCORES_LIMIT_BYTES)
    recorded_runtime = report.get('runtime',{})
    if ({key:recorded_runtime.get(key) for key in ('torch','numpy')} !=
            {key:pin['runtime'][key] for key in ('torch','numpy')}
            or recorded_runtime.get('python',pin['runtime']['python']) != pin['runtime']['python']):
        raise ValueError('Recorded backend runtime mismatch')
    if (report['status'] != 'completed' or report['model_sha256'] != pin['weight']['sha256']
            or report['corpus_manifest_sha256'] != [digest(args.corpus/'manifest.json')]):
        raise ValueError('Run/corpus pin mismatch')
    if not isinstance(report.get('cases'),list) or len(report['cases']) != len(manifest):
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
        replay_runtime=dict(python=platform.python_version(),numpy=np.__version__),
        recorded_model_runtime=recorded_runtime,
        historical_python_recorded='python' in recorded_runtime,
        replay_sha256=digest(Path(__file__)), input_scores_sha256=digest(args.scores),
        corpus_manifest_sha256=digest(args.corpus/'manifest.json'), extracted_classes=ast_evidence,
        threshold_policy='original onset=offset, min_on .1 before min_off .1, pads0', cases=rows)
    with args.output.open('x', encoding='utf-8') as file:
        json.dump(result, file, indent=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--scores', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    for path in (args.assets, args.scores, args.corpus, args.output):
        validate_output_path(path.resolve())
    if args.worker:
        replay(args)
        return
    for path in (args.output,Path(str(args.output)+'.process.json'),Path(str(args.output)+'.log')):
        if path.exists():
            raise FileExistsError('Refuse existing replay output/evidence')
    from bounded_process import run_bounded
    env = dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    result = run_bounded([sys.executable,str(Path(__file__).resolve()),'--worker','--assets',str(args.assets.resolve()),
        '--scores',str(args.scores.resolve()),'--corpus',str(args.corpus.resolve()),'--output',str(args.output.resolve())],
        cwd=Path(__file__).resolve().parents[2],env=env,log_path=Path(str(args.output)+'.log'),
        timeout_seconds=60,memory_limit_bytes=1024**3)
    with Path(str(args.output)+'.process.json').open('x',encoding='utf-8') as file:
        json.dump(asdict(result),file,indent=2)
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('VTC1 replay failed; inspect local process/log')
    report = read_json(args.output, 16*1024**2)
    if report.get('status') != 'completed':
        raise SystemExit('Incomplete replay output')
    print(json.dumps(dict(status='completed',cases=len(report['cases']),classifier_forwards=0,product_changed=False)))


if __name__ == '__main__':
    main()
