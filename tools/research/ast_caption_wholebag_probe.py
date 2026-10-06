"""Exact two unreviewed caption bags, whole coverage; research only, not event labels."""
import argparse
from dataclasses import asdict
import hashlib
import os
from pathlib import Path
import platform
import subprocess
import sys
import uuid

from ast_mil_probe import (
    ROOT, RUNTIME, SOURCE_SHA, MODEL_SHA, MODEL, FEATURE_SHA, normalized,
    validate_feature_inputs, score_bag, offline, digest, read_report, write,
)
from ast_infant_head_probe import score, evaluate
from ast_embedding_interface import AstRepresentation
from infant_mil_probe import fit_mil, select
from infant_presence_probe import presence_weights
from public_infant_review_acquire import sources, SOURCE_SHA as EXTRA_SOURCE_SHA, FFMPEG, verify_ffmpeg
from ondevice_av_probe import validate_output_path

EXPERIMENT = 'ast-caption-wholebag-20261007'
EVIDENCE_ROOT = ROOT / 'work' / EXPERIMENT
AUDIO_ROOT = ROOT / 'work/new-infant-review-20261007/audio'
REVIEW_ROOT = ROOT / 'work/new-infant-review-20261007/run02'
ACQUISITION_SHA = '0894e5ff8d6446afda67b9ad1721f8901f7c473484bff2721a06abdbc2c55cf1'
LEVELS_SHA = '0c8e9718c6c42980034499a05f291c52fbe98350f0d9b16a4b9b7d5b027cf364'
COMPLETION_SHA = 'cee1c0ca602145a62d9b2f98c97e79340caa117b2f99255d6a907c4fb39b932c'
EXTRA = {'9437': (21456, '61e4354ad089ecd676b2a5d1e9dfde32a4695563087420a8f0e46bb67b1e86c0'),
         '244237': (1560295, '05aa0e243efcbb3e66265f2966f9dd279a7d254ccf5193cea7dbcf6dd7349ac9')}


def extra_sources(experiment):
    if experiment != EXPERIMENT:
        raise ValueError('Exact caption research experiment authorization required')
    paths = (AUDIO_ROOT / 'acquisition.json', REVIEW_ROOT / 'levels.json', REVIEW_ROOT / 'validated-completion.json')
    for path, expected in zip(paths, (ACQUISITION_SHA, LEVELS_SHA, COMPLETION_SHA)):
        if path.is_symlink() or digest(path) != expected:
            raise ValueError('Frozen public acquisition/review differs')
    acquired, levels, completion = [read_report(p) for p in paths]
    if (acquired['status'] != 'complete' or acquired['source_sha256'] != EXTRA_SOURCE_SHA
            or acquired['training'] is not False or acquired['approved_training_label'] is not None
            or completion['status'] != 'completed' or completion['levels_sha256'] != LEVELS_SHA):
        raise ValueError('Public review source/boundary differs')
    rows = sources()
    if [r['id'] for r in acquired['files']] != list(EXTRA) or [r['id'] for r in levels['cases']] != list(EXTRA):
        raise ValueError('Exact caption bag IDs differ')
    for source, item, case in zip(rows, acquired['files'], levels['cases']):
        samples, sha = EXTRA[source['id']]
        if (any(item.get(k) != v or case.get(k) != v for k, v in source.items())
                or case['actual_samples'] != samples or item['sha256'] != sha):
            raise ValueError('Caption review label/role/full coverage differs')
        audio = AUDIO_ROOT / (source['id'] + '.mp3')
        if audio.is_symlink() or audio.stat().st_size != item['bytes'] or digest(audio) != sha:
            raise ValueError('Frozen caption source audio differs')
    return rows


def whole_spans(pcm):
    import numpy as np
    pcm = np.asarray(pcm)
    if (pcm.ndim != 1 or not 1 <= len(pcm) <= 1920000 or not np.issubdtype(pcm.dtype, np.floating)
            or not np.isfinite(pcm).all() or np.abs(pcm).max() > 1):
        raise ValueError('Whole bag expects finite 1sample..120s PCM [-1,1]')
    for start in range(0, len(pcm), 160000):
        actual = pcm[start:start + 160000].astype(np.float32)
        block = np.pad(actual, (0, max(0, 16000 - len(actual))))
        yield block, dict(start_sample=start, real_samples=len(actual),
            padded_samples=len(block) - len(actual), rms=float(np.sqrt(np.mean(actual.astype(np.float64) ** 2))),
            input_pcm_sha256=hashlib.sha256(block.tobytes()).hexdigest())


def validate_whole(features, frames, samples):
    import numpy as np
    if (type(samples) is not int or not 1 <= samples <= 1920000
            or features.shape != (len(frames), 768) or len(frames) != (samples + 159999) // 160000
            or not np.isfinite(features).all()):
        raise ValueError('Whole bag shape/full sample coverage differs')
    for i, frame in enumerate(frames):
        expected = min(160000, samples - i * 160000)
        if (type(frame.get('start_sample')) is not int or frame['start_sample'] != i * 160000
                or type(frame.get('real_samples')) is not int or frame['real_samples'] != expected
                or frame.get('padded_samples') != max(0, 16000 - expected)
                or not np.isfinite(frame['rms']) or not 0 <= frame['rms'] <= 1):
            raise ValueError('Whole bag chronology/tail/quiet metadata differs')


def whole_bag(features, frames, samples):
    import numpy as np
    validate_whole(features, frames, samples)
    kept = [i for i, f in enumerate(frames) if f['rms'] >= .003]
    if not kept:
        raise ValueError('Quiet-only whole bag')
    values = np.stack([normalized(features[i]) for i in kept])
    times = np.asarray([frames[i]['real_samples'] for i in kept], np.float64)
    return values, times / times.sum()


def span_scores(features, case, w, b):
    validate_whole(features, case['frames'], case['actual_pcm_samples'])
    return [dict(frame, quiet=frame['rms'] < .003,
        **(score(vector, w, b) if frame['rms'] >= .003 else dict(infant_score=None, infant_support=False)))
        for vector, frame in zip(features, case['frames'])]


def extract_features(args):
    import numpy as np
    rows = extra_sources(args.experiment_id)
    verify_ffmpeg(FFMPEG)
    encoder = AstRepresentation(); arrays = {}; cases = []
    for row in rows:
        path = AUDIO_ROOT / (row['id'] + '.mp3')
        decoded = subprocess.run([str(FFMPEG), '-v', 'error', '-i', str(path), '-map', '0:a:0',
            '-vn', '-t', '120', '-ac', '1', '-ar', '16000', '-f', 's16le', 'pipe:1'],
            capture_output=True, timeout=20)
        expected, sha = EXTRA[row['id']]
        if decoded.returncode or len(decoded.stdout) != expected * 2:
            raise ValueError('Full caption bag decode sample count differs')
        pcm = np.frombuffer(decoded.stdout, dtype='<i2').astype(np.float32) / 32768
        vectors = []; frames = []
        for block, frame in whole_spans(pcm):
            vector, error = encoder.infer(block)
            vectors.append(vector); frames.append(dict(frame, exact_527_logits_equal=True, max_absolute_logit_error=error))
        values = np.stack(vectors); validate_whole(values, frames, expected)
        if digest(path) != sha:
            raise ValueError('Public bag audio changed during extraction')
        arrays[row['id']] = values
        cases.append(dict(row, frames=frames, actual_pcm_samples=expected,
            research_use='whole_recording_caption_weak_bag', weak_caption_target=1.,
            experimental_use_authorized=True, canonical_training_authorized=False,
            audio_sha256=sha, feature_input_scope='whole_recording'))
    archive = args.output / 'features-extra.npz'
    np.savez_compressed(archive, **arrays)
    write(args.output / 'features.json', dict(experiment_id=EXPERIMENT, cases=cases,
        features_sha256=digest(archive), acquisition_sha256=ACQUISITION_SHA, levels_sha256=LEVELS_SHA,
        source_snapshot_sha256=EXTRA_SOURCE_SHA, model_sha256=MODEL_SHA, runtime=RUNTIME,
        source_code_sha256=provenance(), raw_files_created=0, training=False,
        event_ground_truth=False, product_changed=False))


def load_extra_features(args):
    rows = extra_sources(args.experiment_id)
    metadata = read_report(args.extra_features / 'features.json')
    completed = read_report(args.extra_features / 'completion.json')
    process = read_report(args.extra_features / 'process.json')
    archive = args.extra_features / 'features-extra.npz'
    if (completed['status'] != 'completed' or completed['mode'] != 'features'
            or completed['experiment_id'] != EXPERIMENT or completed['runtime'] != RUNTIME
            or completed['source_code_sha256'] != provenance()
            or completed['artifact_metadata_sha256'] != digest(args.extra_features / 'features.json')
            or process['exit_code'] != 0 or process['termination'] is not None
            or process['cleanup_complete'] is not True or process['remaining_job_processes'] != 0
            or archive.is_symlink() or not 1 <= archive.stat().st_size <= 2 * 1024**2
            or digest(archive) != metadata['features_sha256'] or metadata['experiment_id'] != EXPERIMENT
            or metadata['model_sha256'] != MODEL_SHA or metadata['runtime'] != RUNTIME
            or metadata['acquisition_sha256'] != ACQUISITION_SHA or metadata['levels_sha256'] != LEVELS_SHA
            or metadata['source_snapshot_sha256'] != EXTRA_SOURCE_SHA
            or metadata['source_code_sha256'] != provenance() or metadata['training'] is not False
            or metadata['event_ground_truth'] is not False or metadata['product_changed'] is not False
            or len(metadata['cases']) != 2):
        raise ValueError('Caption feature provenance differs')
    for row, case in zip(rows, metadata['cases']):
        if (any(case.get(k) != v for k, v in row.items())
                or case['actual_pcm_samples'] != EXTRA[row['id']][0]
                or case['audio_sha256'] != EXTRA[row['id']][1]
                or case['feature_input_scope'] != 'whole_recording'
                or case['weak_caption_target'] != 1. or case['experimental_use_authorized'] is not True
                or case['canonical_training_authorized'] is not False):
            raise ValueError('Caption feature experimental-use boundary differs')
    return archive, metadata['cases']


def fit(args):
    import numpy as np
    archive, cases, train = validate_feature_inputs(args)
    extra_archive, extras = load_extra_features(args)
    aliases = {c['sourcegroup'].split(':')[-1].casefold() for c in cases}
    if aliases & {c['uploader'].casefold() for c in extras}:
        raise ValueError('Known source aliases overlap')
    extra_train = [dict(c, role='train', weak_class='infant_vocal') for c in extras]
    training = train + extra_train
    if len(training) != 23 or len({c['sourcegroup'] for c in training}) != 20:
        raise ValueError('Caption training partition differs')
    with np.load(archive, allow_pickle=False) as old, np.load(extra_archive, allow_pickle=False) as new:
        values = lambda c: new[c['id']] if c['id'] in EXTRA else old[c['id']]
        bags = [whole_bag(values(c), c['frames'], c['actual_pcm_samples']) for c in training]
        chosen, cv = select(bags, training, 768)
        for grid in cv:
            for fold in grid['folds']:
                held = [c for c in training if c['sourcegroup'] == fold['sourcegroup']]
                fold['held_weak_classes'] = sorted({c['weak_class'] for c in held})
            grid['class_macro_nll'] = {label: float(np.mean([f['negative_log_likelihood'] for f in grid['folds']
                if label in f['held_weak_classes']])) for label in ('infant_vocal', 'adult_vocal', 'other_or_uncertain')}
        labels = np.asarray([float(c['weak_class'] == 'infant_vocal') for c in training])
        w, b = fit_mil(bags, labels, presence_weights(training), chosen, 768)
        head = args.output / 'head-ast-presence-public.npz'
        np.savez_compressed(head, weights=w.astype(np.float32), bias=np.asarray(b, np.float32))
        selection = dict(selected_penalty=chosen, source_cv=cv, train_ids=[c['id'] for c in training],
            head_sha256=digest(head), validation_features_read=False, private_features_read=False,
            experiment_id=EXPERIMENT, extra_features_sha256=digest(extra_archive))
        write(args.output / 'selection.json', selection)
        # Access validation arrays only after head and selection are frozen on disk.
        all_cases = cases + extra_train
        result_rows = [dict(id=c['id'], role=c['role'], weak_class=c['weak_class'], sourcegroup=c['sourcegroup'],
            **score_bag(whole_bag(values(c), c['frames'], c['actual_pcm_samples']), w, b),
            spans=span_scores(values(c), c, w, b)) for c in all_cases]
    validate_feature_inputs(args); load_extra_features(args)
    write(args.output / 'fit.json', dict(cases=result_rows, train_cases=23, validation_cases=14,
        training_sources=20, parameters=769, fp32_parameter_bytes=3076, head_sha256=digest(head),
        model_sha256=MODEL_SHA, source_snapshot_sha256=SOURCE_SHA, extra_source_sha256=EXTRA_SOURCE_SHA,
        experiment_id=EXPERIMENT, selection_sha256=digest(args.output / 'selection.json'),
        configuration=dict(dimension=768, steps=300, lr=.03, l2=chosen, threshold=.8, margin=.4,
            pooling='whole_bag_actual_sample_weighted_log_mean_exp', temperature=1., normalization='span_L2_only'),
        caption_weak_research_training_used=True, canonical_training_authorized=False,
        extra_review_states=[{k:c[k] for k in ('id','training_blocked','approved_training_label','audio_review_completed','event_ground_truth')} for c in extras],
        private_training=False, calibrated_probability=False, quality_validated=False,
        android_validated=False, product_changed=False))


def verify_head(args):
    import numpy as np
    meta = read_report(args.head / 'fit.json')
    selection = read_report(args.head / 'selection.json')
    path = args.head / 'head-ast-presence-public.npz'
    if (meta['experiment_id'] != EXPERIMENT or meta['head_sha256'] != args.head_sha
            or path.is_symlink() or digest(path) != args.head_sha
            or selection['head_sha256'] != args.head_sha
            or digest(args.head / 'selection.json') != meta['selection_sha256']
            or meta['train_cases'] != 23 or meta['training_sources'] != 20
            or meta['private_training'] is not False or meta['product_changed'] is not False):
        raise ValueError('Frozen caption research head differs')
    with np.load(path,allow_pickle=False) as archive:
        w,b=archive['weights'],archive['bias']
        if (w.shape!=(768,) or b.shape!=() or w.dtype!=np.float32 or b.dtype!=np.float32
                or not np.isfinite(w).all() or not np.isfinite(b)):
            raise ValueError('Frozen caption head tensor invalid')


def provenance():
    from ast_mil_probe import provenance as previous
    return dict(previous(), **{name: digest(ROOT / 'tools/research' / name) for name in
        ('ast_caption_wholebag_probe.py', 'ast_caption_completion.py', 'public_infant_review_acquire.py',
         'bounded_process.py', 'ondevice_av_probe.py', 'efficientat_probe.py')})


def validate_artifact(args):
    from ast_caption_completion import case_ids, flags, frames, scalar
    metadata = {'features':'features.json', 'fit':'fit.json', 'evaluate':'evaluation-local.json'}[args.mode]
    report = read_report(args.output / metadata)
    flags(report,dict(product_changed=False))
    if args.mode == 'features':
        source_rows=extra_sources(EXPERIMENT)
        rows=case_ids(report,EXTRA)
        flags(report,dict(experiment_id=EXPERIMENT,training=False,event_ground_truth=False,raw_files_created=0,
            acquisition_sha256=ACQUISITION_SHA,levels_sha256=LEVELS_SHA,source_snapshot_sha256=EXTRA_SOURCE_SHA,
            model_sha256=MODEL_SHA,runtime=RUNTIME))
        if digest(args.output/'features-extra.npz') != report.get('features_sha256'):
            raise ValueError('Feature archive does not match report')
        for source,row in zip(source_rows,rows):
            if any(row.get(k)!=v or type(row.get(k)) is not type(v) for k,v in source.items()):
                raise ValueError('Feature source/review state differs')
            flags(row,dict(actual_pcm_samples=EXTRA[row['id']][0],audio_sha256=EXTRA[row['id']][1],
                experimental_use_authorized=True,canonical_training_authorized=False,
                feature_input_scope='whole_recording',research_use='whole_recording_caption_weak_bag',weak_caption_target=1.))
            frames(row['frames'],row['actual_pcm_samples'],scored=False)
    elif args.mode == 'fit':
        _,old,_=validate_feature_inputs(args)
        extra=read_report(args.extra_features/'features.json')['cases']
        expected=old+extra;rows=case_ids(report,[r['id'] for r in expected])
        flags(report,dict(train_cases=23,validation_cases=14,training_sources=20,parameters=769,fp32_parameter_bytes=3076,
            caption_weak_research_training_used=True,canonical_training_authorized=False,private_training=False,
            calibrated_probability=False,quality_validated=False,android_validated=False,experiment_id=EXPERIMENT,
            model_sha256=MODEL_SHA,source_snapshot_sha256=SOURCE_SHA,extra_source_sha256=EXTRA_SOURCE_SHA))
        verify_head(type('HeadArgs',(),dict(head=args.output,head_sha=digest(args.output/'head-ast-presence-public.npz')))())
        selection=read_report(args.output/'selection.json')
        flags(report['configuration'],dict(dimension=768,steps=300,lr=.03,l2=selection['selected_penalty'],
            threshold=.8,margin=.4,pooling='whole_bag_actual_sample_weighted_log_mean_exp',temperature=1.,normalization='span_L2_only'))
        if selection['selected_penalty'] not in (.0001,.001,.01):raise ValueError('Unprescribed head selection')
        if report.get('extra_review_states') != [{k:row[k] for k in ('id','training_blocked','approved_training_label',
                'audio_review_completed','event_ground_truth')} for row in extra]:raise ValueError('Extra review states differ')
        for row,source in zip(rows,expected):
            flags(row,dict(role=source.get('role') if row['id'] not in EXTRA else 'train',
                weak_class=source.get('weak_class') if row['id'] not in EXTRA else 'infant_vocal',sourcegroup=source['sourcegroup']))
            scalar(row.get('infant_score'));scalar(row.get('float32_absolute_error'),0.,5e-5)
            from infant_presence_probe import supported
            if row.get('infant_support') is not supported(row['infant_score']):raise ValueError('Bag support differs')
            frames(row.get('spans'),source['actual_pcm_samples'])
            attention=row.get('attention')
            if not isinstance(attention,list) or len(attention)!=sum(f['rms']>=.003 for f in row['spans']):
                raise ValueError('Bag attention/eligible spans incomplete')
            if abs(sum(scalar(v) for v in attention)-1)>1e-6:raise ValueError('Bag attention sum differs')
    else:
        from efficientat_probe import load_corpus
        expected=load_corpus(args.corpus);rows=case_ids(report,[r['id'] for r in expected])
        flags(report,dict(head_sha256=args.head_sha,corpus_manifest_sha256=digest(args.corpus/'manifest.json'),training=False,
            private_data_evaluation_only=True,actual_infant_event_timing_validated=False,quality_validated=False,android_validated=False))
        for source,row in zip(expected,rows):
            flags(row,dict(duration_ms=source['duration_ms'],pcm_sha256=source['pcm_sha256']))
            frames(row.get('spans'),(args.corpus/(row['id']+'.f32')).stat().st_size//4)
            if row.get('any_supported_span') is not any(f['infant_support'] for f in row['spans']):
                raise ValueError('Evaluation summary contradicts spans')


def verify_completion(args, pins):
    done = read_report(args.output / 'completion.json')
    metadata = {'features':'features.json', 'fit':'fit.json', 'evaluate':'evaluation-local.json'}[args.mode]
    if (done.get('status') != 'completed' or done.get('mode') != args.mode or done.get('runtime') != RUNTIME
            or done.get('experiment_id') != EXPERIMENT or done.get('source_code_sha256') != pins
            or provenance() != pins or done.get('product_changed') is not False
            or done.get('artifact_metadata_sha256') != digest(args.output / metadata)):
        raise ValueError('Caption completion provenance or artifact differs')
    validate_artifact(args)


def run_worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual = dict(python=platform.python_version(), numpy=np.__version__, onnx=onnx.__version__, ort=ort.__version__)
    if actual != RUNTIME:
        raise ValueError('Caption experiment runtime differs')
    if args.mode == 'evaluate': verify_head(args)
    {'features': extract_features, 'fit': fit, 'evaluate': evaluate}[args.mode](args)
    if digest(MODEL) != MODEL_SHA:
        raise ValueError('Original AST changed')
    metadata_name = {'features':'features.json', 'fit':'fit.json', 'evaluate':'evaluation-local.json'}[args.mode]
    write(args.output / 'completion.json', dict(status='completed', mode=args.mode,
        runtime=actual, experiment_id=EXPERIMENT, source_code_sha256=provenance(), product_changed=False))
    completed = read_report(args.output / 'completion.json')
    completed['artifact_metadata_sha256'] = digest(args.output / metadata_name)
    write(args.output / 'completion.json', completed)


def output_path(path):
    validate_output_path(path)
    if path == EVIDENCE_ROOT.resolve() or not path.is_relative_to(EVIDENCE_ROOT.resolve()):
        raise ValueError('Caption output must stay inside dedicated evidence root')


def claim_worker(args):
    token = os.environ.get('AGATTOK_CAPTION_WORKER_TOKEN')
    if not token or not (args.output / 'launch.json').is_file():
        raise ValueError('Direct caption worker invocation prohibited')
    launch = read_report(args.output / 'launch.json')
    if (launch['token'] != token or launch['output'] != str(args.output) or launch['mode'] != args.mode
            or launch['source_code_sha256'] != provenance() or launch['experiment_id'] != EXPERIMENT):
        raise ValueError('Caption worker launch differs')
    if any((args.output / name).exists() for name in ('features.json','features-extra.npz','fit.json',
            'head-ast-presence-public.npz','selection.json','evaluation-local.json','completion.json','process.json')):
        raise ValueError('Existing caption terminal output must be preserved')
    with (args.output / 'worker-claim.json').open('x', encoding='utf-8') as handle:
        handle.write(str(os.getpid()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('features','fit','evaluate'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--experiment-id', required=True)
    for name in ('sources','features','extra_features','head','corpus'):
        parser.add_argument('--' + name.replace('_','-'), type=Path)
    parser.add_argument('--sources-sha'); parser.add_argument('--head-sha')
    parser.add_argument('--worker', action='store_true')
    args = parser.parse_args()
    for name in ('output','sources','features','extra_features','head','corpus'):
        value = getattr(args,name)
        if value is not None: setattr(args,name,value.resolve())
    output_path(args.output)
    if args.experiment_id != EXPERIMENT:
        raise ValueError('Exact caption research experiment authorization required')
    required = {'features':(), 'fit':('sources','sources_sha','features','extra_features'),
                'evaluate':('head','head_sha','corpus')}[args.mode]
    if any(getattr(args,name) is None for name in required):
        raise SystemExit('Missing caption mode arguments')
    if args.worker:
        claim_worker(args)
        run_worker(args); return
    # Fail malformed/stale inputs before creating an output run.
    extra_sources(args.experiment_id)
    if args.mode == 'fit': validate_feature_inputs(args); load_extra_features(args)
    if args.mode == 'evaluate': verify_head(args)
    pins = provenance(); args.output.mkdir(parents=True, exist_ok=False)
    launch = dict(token=uuid.uuid4().hex,mode=args.mode,output=str(args.output),
        experiment_id=EXPERIMENT,source_code_sha256=pins)
    write(args.output / 'launch.json',launch)
    env = dict(os.environ,PYTHONDONTWRITEBYTECODE='1',AGATTOK_CAPTION_WORKER_TOKEN=launch['token'])
    env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv = [sys.executable,'-B',str(Path(__file__).resolve()),args.mode,'--worker',
            '--output',str(args.output),'--experiment-id',EXPERIMENT]
    for name in ('sources','sources_sha','features','extra_features','head','head_sha','corpus'):
        value = getattr(args,name)
        if value is not None: argv += ['--'+name.replace('_','-'),str(value)]
    from bounded_process import run_bounded
    result = run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,
        memory_limit_bytes=(2 if args.mode=='fit' else 3)*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes or result.output_truncated:
        raise SystemExit('Caption experiment failed; inspect bounded evidence')
    verify_completion(args, pins)
    print('Caption experiment completed; product_changed=false')


if __name__ == '__main__': main()
