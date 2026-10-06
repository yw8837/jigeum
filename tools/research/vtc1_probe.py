"""Pinned VTC1 local research only; never downloads, uploads or changes the app.

Execute the original PyanNet/SincNet/Task and Model.slide calculation bodies.
Package/CLI/training imports are omitted. Timeline is restricted to a single
Segment and pescador batching to one CPU item. No commercial grant is inferred.
"""
import argparse
import ast
from dataclasses import asdict, dataclass
from enum import Enum
import json
import math
import numbers
import os
from pathlib import Path
import platform
import sys
from types import ModuleType, SimpleNamespace
from typing import Callable, Iterable, Iterator, List, NamedTuple, Optional, Tuple, Union
import warnings

from efficientat_probe import digest, load_corpus
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('vtc1-model.json')
LABELS = ('KCHI', 'CHI', 'MAL', 'FEM', 'SPEECH')
ONSET = (.3739, .1570, .1363, .3583, .4014)
SOURCE_CONFIG = dict(sincnet=dict(out_channels=[256, 60, 60], kernel_size=[251, 5, 5]),
                     rnn=dict(hidden_size=128, dropout=0, num_layers=3, unit='LSTM', bidirectional=True),
                     ff=dict(hidden_size=[128, 128]))


class SingleSegmentTimeline:
    """Only package glue for native SlidingWindow.__call__; not a general timeline."""
    def __init__(self, segments):
        if len(segments) != 1:
            raise ValueError('Only one contiguous support is allowed')
        self.segments = tuple(segments)

    def __iter__(self):
        return iter(self.segments)


def batch_one(stream, batch_size, partial):
    if batch_size != 1 or partial is not True:
        raise ValueError('Only sequential CPU batch1 is supported')
    import numpy as np
    for item in stream:
        yield {'X': np.stack([item['X']])}


class RemoveTimelineImports(ast.NodeTransformer):
    def visit_ImportFrom(self, node):
        if any(alias.name == 'Timeline' for alias in node.names):
            return None
        return node


def extract_classes(path, names, namespace, selected_methods=None):
    tree = ast.parse(path.read_text('utf-8'))
    classes = [node for node in tree.body if isinstance(node, ast.ClassDef) and node.name in names]
    if {node.name for node in classes} != set(names):
        raise ValueError('Missing/duplicate upstream class')
    evidence = []
    for node in classes:
        original = ast.dump(node, include_attributes=False)
        if selected_methods is not None:
            node.body = [method for method in node.body if isinstance(method, (ast.FunctionDef, ast.AsyncFunctionDef))
                         and method.name in selected_methods]
            if {method.name for method in node.body} != set(selected_methods):
                raise ValueError('Missing/duplicate selected upstream method')
        node = RemoveTimelineImports().visit(node)
        evidence.append(dict(symbol=node.name, source_ast_sha256=__import__('hashlib').sha256(original.encode()).hexdigest(),
                             kept_ast_sha256=__import__('hashlib').sha256(ast.dump(node, include_attributes=False).encode()).hexdigest()))
    module = ast.fix_missing_locations(ast.Module(body=classes, type_ignores=[]))
    exec(compile(module, str(path), 'exec'), namespace)
    return evidence


def native_model(assets, pin):
    import numpy as np
    import torch
    from torch import nn
    source = assets / 'source'
    package = source/'pyannote/pyannote/audio'
    core = source/'core/pyannote/core'
    module = ModuleType('agattok_vtc1_extracted')
    sys.modules[module.__name__] = module
    namespace = module.__dict__
    namespace.update(np=np, torch=torch, nn=nn, Module=nn.Module, F=torch.nn.functional, math=math,
        numbers=numbers, warnings=warnings, dataclass=dataclass, Enum=Enum, NamedTuple=NamedTuple,
        Optional=Optional, Union=Union, Tuple=Tuple, List=List, Iterator=Iterator, Iterable=Iterable,
        Callable=Callable, Alignment=str, CropMode=str, SEGMENT_PRECISION=1e-6,
        Timeline=SingleSegmentTimeline, Resolution=object, RESOLUTION_FRAME='frame', RESOLUTION_CHUNK='chunk',
        pescador=SimpleNamespace(maps=SimpleNamespace(buffer_stream=batch_one)))
    evidence = []
    for path, names, methods in (
        (core/'segment.py', ('Segment', 'SlidingWindow'), None),
        (core/'feature.py', ('SlidingWindowFeature',), None),
        (package/'train/task.py', ('TaskType', 'TaskOutput', 'Task'), None),
        (package/'train/model.py', ('Model',), ('__init__', 'init', 'task', 'n_features', 'classes', 'dimension', 'resolution', 'alignment', 'slide')),
        (package/'models/sincnet.py', ('SincConv1d', 'SincNet'), None),
        (package/'models/models.py', ('RNN', 'FF', 'PyanNet'), None)):
        evidence.extend(extract_classes(path, names, namespace, methods))
    # Read the actual train specification used by Pretrained, not the obsolete
    # task name in weights/specs.yml. No YAML dependency or untrusted loader.
    specs = (source/'vtc/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/specs.yml').read_text('utf-8')
    expected_tail = 'y:\n  classes:\n' + ''.join('  - '+label+'\n' for label in LABELS)
    if (specs.count('\ntask: frame-wise multi-label classification\n') != 1
            or not specs.endswith(expected_tail)):
        raise ValueError('Pinned train task/classes differ')
    model = namespace['PyanNet'](dict(X=dict(dimension=1), y=dict(classes=list(LABELS)),
        task=namespace['Task'].from_str('frame-wise multi-label classification')), **SOURCE_CONFIG).float().eval()
    state = torch.load(assets/pin['weight']['name'], weights_only=True, map_location='cpu')
    expected = model.state_dict()
    if not isinstance(state, dict) or set(state) != set(expected):
        raise ValueError('Entire state key set mismatch')
    for name, value in state.items():
        if (not isinstance(value, torch.Tensor) or value.dtype != torch.float32
                or value.shape != expected[name].shape or not bool(torch.isfinite(value).all())):
            raise ValueError('State tensor mismatch: '+name)
    if state['linear_.weight'].shape != (5, 128):
        raise ValueError('Unexpected voice-type head')
    model.load_state_dict(state, strict=True)
    if model.resolution.duration != 325/16000 or model.resolution.step != 27/16000:
        raise ValueError('Unexpected frame coordinate contract')
    return model, namespace, dict(state_keys=len(state), parameters=sum(p.numel() for p in model.parameters()),
        resolution=dict(duration_samples=325, step_samples=27, start_samples=0), extracted_classes=evidence)


def verify_sources(assets, pin):
    for relative, sha in pin['source_files'].items():
        path = assets/relative
        if path.is_symlink() or not path.resolve(strict=True).is_relative_to(assets) or digest(path) != sha:
            raise ValueError('Pinned source mismatch: '+relative)


def verify_assets(assets, pin):
    verify_sources(assets, pin)
    weight = assets/pin['weight']['name']
    if (weight.is_symlink() or weight.stat().st_size != pin['weight']['size']
            or digest(weight) != pin['weight']['sha256']):
        raise ValueError('Pinned weight mismatch')


def native_coverage(features, resolution, sliding_window, alignment='strict'):
    """Replay native slide's chunk/crop/count arithmetic without a forward.

    Returns the real coverage of every grid row, including zero-coverage edges.
    Uses exactly the native resolution and pyannote.core crop calls.
    """
    import numpy as np
    support = features.extent
    if support.duration < sliding_window.duration:
        chunks, fixed = [support], support.duration
    else:
        chunks = list(sliding_window(support, align_last=True))
        fixed = sliding_window.duration
    count = np.zeros(resolution.samples(chunks[-1].end, mode='center'), dtype=np.int8)
    rows = []
    for chunk in chunks:
        indices = resolution.crop(chunk, mode=alignment, fixed=fixed)
        if not len(indices) or min(indices) < 0 or max(indices) >= len(count):
            raise ValueError('Native crop outside output grid')
        count[indices] += 1
        rows.append(dict(start=chunk.start, end=chunk.end, indices=len(indices)))
    return count, rows


def worker(args):
    import socket
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network prohibited during local inference')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    pin = json.loads(PIN.read_text('utf-8'))
    verify_assets(args.assets, pin)
    entries = [(root, entry) for root in args.corpus for entry in load_corpus(root)]
    if len({e['id'] for _, e in entries}) != len(entries):
        raise ValueError('Duplicate case IDs across corpora')
    import numpy as np
    import torch
    if dict(python=platform.python_version(), torch=torch.__version__, numpy=np.__version__) != pin['runtime']:
        raise ValueError('Runtime does not match research pin')
    torch.set_num_threads(4)
    torch.manual_seed(17)
    model, native, details = native_model(args.assets, pin)
    resolution = model.resolution
    chunks = native['SlidingWindow'](duration=2., step=.5)
    raw_audio_window = native['SlidingWindow'](start=-.5/16000, duration=1/16000, step=1/16000)
    report = dict(status='validated' if args.validate_only else 'running', model_sha256=pin['weight']['sha256'],
        runner_sha256=digest(Path(__file__)), runtime=pin['runtime'], architecture=details,
        quality_validated=False, android_validated=False, commercial_permission_confirmed=False,
        python_network_blocked=True, private_classifier_executed=not args.validate_only,
        corpus_manifest_sha256=[digest(root/'manifest.json') for root in args.corpus], cases=[])
    if args.validate_only:
        # Tensor contracts only: a non-private synthetic signal, not a quality sample.
        with torch.inference_mode():
            probe = model(torch.zeros(1, 32000, 1))
        if probe.shape != (1, 1174, 5) or not bool(torch.isfinite(probe).all()):
            raise ValueError('Native synthetic forward contract mismatch')
        report.update(validated_cases=len(entries), private_classifier_forwards=0,
                      synthetic_tensor_forwards=1, synthetic_shape=list(probe.shape))
        (args.output/'validation-private.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        return
    import time
    for root, entry in entries:
        path = root/(entry['id']+'.f32')
        pcm = np.fromfile(path, dtype='<f4').copy()
        features = native['SlidingWindowFeature'](pcm[:, None], raw_audio_window)
        begin = time.monotonic()
        with torch.inference_mode():
            output = model.slide(features, chunks, batch_size=1, device='cpu')
        scores = output.data
        if scores.ndim != 2 or scores.shape[1] != 5 or not np.isfinite(scores).all() or np.any((scores < 0) | (scores > 1)):
            raise ValueError('Invalid native slide output')
        # Native grid may extend beyond true audio. Keep only complete RFs in the
        # recording; edges/zero-coverage bins are not fabricated detections.
        indices = np.arange(len(scores))
        starts = indices*27
        coverage, chunk_rows = native_coverage(features, resolution, chunks, model.alignment)
        if len(coverage) != len(scores):
            raise ValueError('Native coverage/grid length mismatch')
        valid = (starts+325 <= len(pcm)) & (coverage > 0)
        indices, scores, starts = indices[valid], scores[valid], starts[valid]
        if not len(scores):
            raise ValueError('No complete receptive-field support')
        if digest(path) != entry['pcm_sha256']:
            raise ValueError('Source PCM changed during inference')
        report['cases'].append(dict(id=entry['id'], source_pcm_sha256=entry['pcm_sha256'], duration_ms=entry['duration_ms'],
            expected=entry.get('expected'), role=entry.get('role'), label_strength=entry.get('label_strength'),
            inference_seconds=time.monotonic()-begin, frames=len(scores),
            frame_start_samples=starts.tolist(), frame_end_samples=(starts+325).tolist(),
            coverage=coverage[valid].tolist(), native_chunks=chunk_rows,
            labels=list(LABELS), scores=scores.tolist(),
            monitored={label:dict(max=float(scores[:,i].max()), mean=float(scores[:,i].mean()),
                bins_ge_original_onset=int((scores[:,i]>=ONSET[i]).sum()), original_onset=ONSET[i]) for i,label in enumerate(LABELS)}))
        (args.output/'scores-private.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    report['status'] = 'completed'
    (args.output/'scores-private.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--corpus', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--validate-only', action='store_true')
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    args.assets = args.assets.resolve(strict=True)
    args.corpus = [root.resolve(strict=True) for root in args.corpus]
    args.output = args.output.resolve()
    for path in (args.assets, *args.corpus, args.output):
        validate_output_path(path)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True, exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT', None)
    env.pop('OMX_STATE_ROOT', None)
    command = [sys.executable, str(Path(__file__).resolve()), '--worker', '--assets', str(args.assets),
               '--corpus', *map(str, args.corpus), '--output', str(args.output)]
    if args.validate_only:
        command.append('--validate-only')
    result = run_bounded(command, cwd=Path(__file__).resolve().parents[2], env=env,
        log_path=args.output/'inference-private.log', timeout_seconds=180, memory_limit_bytes=2*1024**3)
    (args.output/'process.json').write_text(json.dumps(asdict(result), indent=2), encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('VTC1 research failed; inspect local process/log')
    filename = 'validation-private.json' if args.validate_only else 'scores-private.json'
    report = json.loads((args.output/filename).read_text('utf-8'))
    if report['status'] != ('validated' if args.validate_only else 'completed'):
        raise SystemExit('Incomplete research artifact')
    print(json.dumps(dict(status=report['status'], private_cases=len(report['cases']),
                         quality_validated=False, product_changed=False)))


if __name__ == '__main__':
    main()
