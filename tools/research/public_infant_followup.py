"""Frozen public preview evaluation; no training, private inputs or Android claims."""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys

from ast_embedding_interface import EMBEDDING, RUNTIME
from ast_infant_head_graph import HEAD_SHA, OUTPUT, frozen_head
from ast_infant_head_probe import score
from ast_device_benchmark import storage_snapshot
from extra_babble_evaluation import acquire, CANDIDATE_SHA
from infant_head_screen import ROOT, digest, read_report, write, offline
from infant_presence_probe import supported
from ondevice_ast_features import extract
from ondevice_audio_candidate import window_ranges
from ondevice_av_probe import validate_output_path, verify_model

LABELS = ROOT / 'outputs/MoaAndroid/app/src/astProbe/java/com/moa/babylanguage/AstAudioAnalyzer.kt'
MONITORED = ('Babbling', 'Baby cry, infant cry', 'Baby laughter', 'Child speech, kid speaking',
             'Speech', 'Hubbub, speech noise, speech babble', 'Music', 'Silence')
SOURCE_ROWS = [
    dict(id='388407', sourcegroup='FSD50K:eval:amccoy711',
         preview_url='https://cdn.freesound.org/previews/388/388407_7293523-lq.mp3',
         page_url='https://freesound.org/people/amccoy711/sounds/388407/',
         caption_category='15_month_son_giggling', mixed='unknown'),
    dict(id='135185', sourcegroup='FSD50K:eval:carameans',
         preview_url='https://cdn.freesound.org/previews/135/135185_2456349-lq.mp3',
         page_url='https://freesound.org/people/carameans/sounds/135185/',
         caption_category='12_month_twins_bathtime_laugh_tags', mixed='unknown_multiple_children_water'),
    dict(id='475589', sourcegroup='Freesound:bvaudio',
         preview_url='https://cdn.freesound.org/previews/475/475589_2883167-lq.mp3',
         page_url='https://freesound.org/people/bvaudio/sounds/475589/',
         caption_category='9_month_babble_trill_before_sleep_tail_little_cry', mixed='unknown'),
]
for row in SOURCE_ROWS:
    row.update(current_license_url='http://creativecommons.org/publicdomain/zero/1.0/',
               training_blocked=True, approved_training_label=None, role='evaluation_only',
               age_verified=False, naturalness='caption_only_unreviewed')
SOURCE_SHA = 'ba345dd1dab51e0f0cc0d90805e5ba54b5aab902962ed07bae3ceb6f6ffbff2d'
CODE_FILES = ('public_infant_followup.py', 'extra_babble_evaluation.py', 'ast_infant_head_graph.py',
              'ast_infant_head_probe.py', 'ondevice_ast_features.py', 'infant_head_screen.py',
              'ondevice_audio_candidate.py', 'infant_presence_probe.py', 'ast_device_benchmark.py')


def sources():
    rows = json.loads(json.dumps(SOURCE_ROWS))
    if hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest() != SOURCE_SHA:
        raise ValueError('Exact followup sources changed')
    return rows


def selected_ranges(sample_count, *, three_locations=False):
    if type(sample_count) is not int or not 16000 <= sample_count <= 300 * 16000:
        raise ValueError('Require actual 1..300 seconds of public decoded PCM')
    length = min(sample_count, 320000)
    starts = {0}
    if three_locations and sample_count > length:
        starts.update(((sample_count - length) // 2, sample_count - length))
    return [(start, start + length) for start in sorted(starts)]


def labels(path=LABELS):
    text = path.read_text(encoding='utf-8').split('private object AstLabels {', 1)[1]
    names = [json.loads('"' + value + '"') for value in re.findall(r'"((?:\\.|[^"\\])*)"', text)]
    if len(names) != 527 or len(set(names)) != 527 or any(name not in names for name in MONITORED):
        raise ValueError('Pinned Android AST label order differs')
    return names


def probabilities(logits, names):
    import numpy as np
    if logits.shape != (1, 527) or logits.dtype != np.float32 or not np.isfinite(logits).all():
        raise ValueError('Original AST output shape/dtype/finite mismatch')
    x = logits[0].astype(np.float64)
    p = np.exp(-np.logaddexp(0, -x))
    return {name: float(p[names.index(name)]) for name in MONITORED}


def validate_ffmpeg(path):
    if not path.is_file() or path.is_symlink():
        raise ValueError('Use the explicit existing local FFmpeg binary')
    result = subprocess.run([str(path), '-hide_banner', '-muxers'], capture_output=True, timeout=10)
    output = (result.stdout + result.stderr).decode('utf-8', 'replace')
    if result.returncode or len(output) > 256*1024 or not re.search(r'^\s*\S*E\s+s16le\s', output, re.MULTILINE):
        raise ValueError('FFmpeg must expose the s16le PCM output muxer before acquisition')


def verify_pins(args, before):
    actual = dict(model=digest(args.model), head=digest(args.head / 'head-ast-presence-public.npz'),
                  fit=digest(args.head / 'fit.json'), labels=digest(LABELS),
                  sources=hashlib.sha256(json.dumps(SOURCE_ROWS, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                  ffmpeg=digest(args.ffmpeg))
    if actual != before or actual['model'] != CANDIDATE_SHA or actual['head'] != HEAD_SHA or actual['sources'] != SOURCE_SHA:
        raise ValueError('Frozen followup input provenance changed')
    return actual


def observe_window(session, block, names, w, b):
    import numpy as np
    rms = float(np.sqrt(np.mean(block.astype(np.float64) ** 2)))
    result = dict(raw_rms=rms, quiet=rms < .003)
    if rms < .003:
        return dict(result, infant_score=None, infant_support=False, original_ast_scores=None)
    features = extract(block)[None]
    logits, embedding, presence = session.run(None, {'input_values': features})
    try:
        if (embedding.shape != (1, 768) or presence.shape != (1, 1) or
                any(v.dtype != np.float32 or not np.isfinite(v).all() for v in (embedding, presence))):
            raise ValueError('Followup head tensors invalid')
        value = float(presence[0, 0])
        reference = score(embedding[0], w, b)
        error = abs(value - reference['infant_score'])
        if not 0 <= value <= 1 or error > 5e-5 or supported(value) != reference['infant_support']:
            raise ValueError('Followup graph/reference head parity failed')
        return dict(result, infant_score=value, infant_support=supported(value), head_max_absolute_error=error,
                    original_ast_scores=probabilities(logits, names))
    finally:
        for value in (features, logits, embedding, presence):
            value.fill(0)


def validate_completion(done, args, pins):
    if (done.get('status') != 'completed' or done.get('requested_cases') != 3 or done.get('decoded_cases') != 3 or
            done.get('runtime') != RUNTIME or done.get('pins') != verify_pins(args, pins) or
            done.get('source_code_sha256') != {name: digest(ROOT / 'tools/research' / name) for name in CODE_FILES} or
            any(done.get(key) is not False for key in ('training', 'calibration', 'private_media_read',
                'strong_ground_truth', 'quality_validated', 'product_changed', 'android_validated'))):
        raise ValueError('Incomplete or changed followup output')
    cases = done.get('cases', [])
    if [row.get('id') for row in cases] != [row['id'] for row in sources()]:
        raise ValueError('Followup source cases missing or reordered')
    for case, source in zip(cases, sources()):
        if any(case.get(key) != value for key, value in source.items()):
            raise ValueError('Followup source provenance changed')
        ranges = selected_ranges(case['decoded_total_samples'], three_locations=case['id'] == '475589')
        if case.get('selected_ranges') != [list(item) for item in ranges]:
            raise ValueError('Followup predefined selection changed')
        expected = [(i, start + a, start + b) for i, (start, end) in enumerate(ranges)
                    for a, b in window_ranges(end - start)]
        actual = [(row['selection_index'], row['start_sample'], row['end_sample']) for row in case['windows']]
        if (actual != expected or case.get('event_ground_truth') is not False or
                case.get('android_pipeline_executed') is not False):
            raise ValueError('Followup windows or evidence scope differs')


def worker(args):
    rows = sources()
    w, b = frozen_head(args.head)
    pins = dict(model=digest(args.model), head=digest(args.head / 'head-ast-presence-public.npz'),
                fit=digest(args.head / 'fit.json'), labels=digest(LABELS), sources=SOURCE_SHA, ffmpeg=digest(args.ffmpeg))
    verify_pins(args, pins)
    records = acquire(args, rows, source_sha=SOURCE_SHA, max_bytes=3*1024**2)
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual = dict(python=platform.python_version(), numpy=np.__version__, onnx=onnx.__version__, ort=ort.__version__)
    if actual != RUNTIME:
        raise ValueError('Followup fixed runtime differs')
    names = labels()
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.enable_mem_pattern = False
    session = ort.InferenceSession(str(args.model), sess_options=options, providers=['CPUExecutionProvider'])
    if [o.name for o in session.get_outputs()] != ['logits', EMBEDDING, OUTPUT]:
        raise ValueError('Unexpected candidate outputs')
    cases = []
    for row in records:
        path = args.output / (row['id'] + '.mp3')
        decoded = subprocess.run([str(args.ffmpeg), '-v', 'error', '-protocol_whitelist', 'file,pipe',
            '-format_whitelist', 'mp3', '-i', str(path), '-map', '0:a:0', '-vn', '-t', '300',
            '-ac', '1', '-ar', '16000', '-f', 's16le', 'pipe:1'], capture_output=True, timeout=20)
        if decoded.returncode or not 32000 <= len(decoded.stdout) <= 300*16000*2 or len(decoded.stdout) % 2 or digest(path) != row['sha256']:
            raise ValueError('Followup public PCM decode failed/changed')
        pcm = np.frombuffer(decoded.stdout, dtype='<i2').astype(np.float32) / 32768
        windows = []
        selected = selected_ranges(len(pcm), three_locations=row['id'] == '475589')
        for selection_index, (selected_start, selected_end) in enumerate(selected):
            for offset_start, offset_end in window_ranges(selected_end - selected_start):
                start, end = selected_start + offset_start, selected_start + offset_end
                result = observe_window(session, pcm[start:end], names, w, b)
                windows.append(dict(result, selection_index=selection_index, start_sample=start,
                                    end_sample=end, real_samples=end - start))
        cases.append(dict(row, decoded_total_samples=len(pcm), selected_ranges=selected, windows=windows,
                          any_supported_window=any(v['infant_support'] for v in windows),
                          event_ground_truth=False, android_pipeline_executed=False))
        pcm.fill(0)
    final = verify_pins(args, pins)
    write(args.output / 'evaluation.json', dict(status='completed', cases=cases, requested_cases=3, decoded_cases=len(cases),
        pins=final, source_code_sha256={name: digest(ROOT / 'tools/research' / name) for name in CODE_FILES}, runtime=actual,
        training=False, calibration=False, private_media_read=False, strong_ground_truth=False,
        quality_validated=False, product_changed=False, android_validated=False))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'head', 'ffmpeg', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--reuse', type=Path)
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args(argv)
    for key in ('model', 'head', 'ffmpeg', 'output', 'reuse'):
        if getattr(args, key) is not None:
            setattr(args, key, getattr(args, key).resolve())
    validate_output_path(args.output)
    verify_model(args.model, dict(size_bytes=91138597, sha256=CANDIDATE_SHA))
    frozen_head(args.head)
    labels()
    validate_ffmpeg(args.ffmpeg)
    if args.worker:
        worker(args); return 0
    pins = dict(model=digest(args.model), head=digest(args.head / 'head-ast-presence-public.npz'),
                fit=digest(args.head / 'fit.json'), labels=digest(LABELS), sources=SOURCE_SHA, ffmpeg=digest(args.ffmpeg))
    verify_pins(args, pins)
    args.output.mkdir(parents=True, exist_ok=False)
    write(args.output / 'storage-start.json', storage_snapshot(args.output))
    from bounded_process import run_bounded
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT', None); env.pop('OMX_STATE_ROOT', None)
    child = [sys.executable, '-B', str(Path(__file__).resolve()), '--worker']
    for key in ('model', 'head', 'ffmpeg', 'output', 'reuse'):
        if getattr(args, key) is not None:
            child += ['--' + key, str(getattr(args, key))]
    result = run_bounded(child, cwd=ROOT, env=env, log_path=args.output / 'run.log',
                         timeout_seconds=180, memory_limit_bytes=3 * 1024 ** 3, max_output_bytes=1024 ** 2)
    write(args.output / 'process.json', asdict(result))
    write(args.output / 'storage-end.json', storage_snapshot(args.output))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes or result.output_truncated:
        raise RuntimeError('Followup failed; preserve partial acquisition without claiming completion')
    done = read_report(args.output / 'evaluation.json')
    validate_completion(done, args, pins)
    print(json.dumps(dict(status='completed', cases=3, source_sha256=SOURCE_SHA, training=False, product_changed=False)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
