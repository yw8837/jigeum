"""Frozen public-source AST768 presence head, not infant intent or event timing."""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys

from ast_embedding_interface import AstRepresentation,MODEL,MODEL_SHA,SOURCE_SHA,RUNTIME
from infant_head_screen import ROOT,EXTENDED_IDS,read_sources,read_report,digest,offline,write
from infant_presence_probe import sigmoid,presence_weights,fit_binary,select,supported
from ondevice_av_probe import validate_output_path


def normalized(vector):
    import numpy as np
    vector=np.asarray(vector,dtype=np.float64)
    if vector.shape!=(768,) or not np.isfinite(vector).all():
        raise ValueError('Expected finite AST768 vector')
    norm=float(np.linalg.norm(vector))
    if norm<1e-12:
        raise ValueError('Zero AST representation')
    return vector/norm


def spans(pcm):
    import numpy as np
    pcm=np.asarray(pcm)
    if (pcm.ndim!=1 or not 1<=len(pcm)<=320000 or not np.issubdtype(pcm.dtype,np.floating)
            or not np.isfinite(pcm).all() or np.abs(pcm).max()>1):
        raise ValueError('Expected finite 1 sample..20s float PCM in [-1,1]')
    rows=[]
    for start in range(0,len(pcm),160000):
        actual=pcm[start:start+160000].astype(np.float32)
        padded=np.pad(actual,(0,max(0,16000-len(actual))))
        rows.append((padded,dict(start_sample=start,real_samples=len(actual),
            padded_samples=len(padded)-len(actual),rms=float(np.sqrt(np.mean(actual.astype(np.float64)**2))),
            input_pcm_sha256=hashlib.sha256(padded.tobytes()).hexdigest())))
    return rows


def validate_representations(features,frames):
    import numpy as np
    if (features.shape!=(len(frames),768) or not 1<=len(frames)<=2 or not np.isfinite(features).all()
            or any(type(f.get('real_samples')) is not int or not 1<=f['real_samples']<=160000
                   or type(f.get('start_sample')) is not int or f['start_sample']!=i*160000
                   or f.get('padded_samples')!=max(0,16000-f['real_samples'])
                   or not np.isfinite(f['rms']) or not 0<=f['rms']<=1 for i,f in enumerate(frames))
            or (len(frames)==2 and frames[0]['real_samples']!=160000)):
        raise ValueError('Invalid AST span/representation')


def pooled(features,frames):
    import numpy as np
    validate_representations(features,frames)
    eligible=[i for i,f in enumerate(frames) if f['rms']>=.003]
    if not eligible:
        raise ValueError('Quiet-only AST training/pooling clip')
    return normalized(np.average(features[eligible],axis=0,weights=[frames[i]['real_samples'] for i in eligible]))


def score(vector,w,b):
    import numpy as np
    if w.shape!=(768,) or not np.isfinite(w).all() or not np.isfinite(b):
        raise ValueError('Invalid AST scoring head')
    x=normalized(vector)
    probability=float(sigmoid(x@w+b))
    stored=float(sigmoid(x.astype(np.float32)@w.astype(np.float32)+np.float32(b)))
    error=abs(probability-stored)
    if error>5e-5:
        raise ValueError('AST head FP32 parity failed')
    return dict(infant_score=probability,infant_support=supported(probability),float32_absolute_error=error)


def score_spans(features,frames,w,b):
    # Full-context support is never converted to actual infant vocalization duration.
    validate_representations(features,frames)
    return [dict(frame,quiet=frame['rms']<.003,
        **(score(feature,w,b) if frame['rms']>=.003 else dict(infant_score=None,infant_support=False)))
        for feature,frame in zip(features,frames)]


def extract_spans(encoder,pcm):
    import numpy as np
    vectors=[]; frames=[]
    for block,frame in spans(pcm):
        vector,error=encoder.infer(block)
        vectors.append(vector)
        frames.append(dict(frame,exact_527_logits_equal=True,max_absolute_logit_error=error))
    return np.stack(vectors),frames


def validate_sources(args):
    if args.sources_sha!=SOURCE_SHA:
        raise ValueError('Fixed AST head source SHA differs')
    sources=read_sources(args.sources,args.sources_sha)
    if [r['id'] for r in sources]!=list(EXTENDED_IDS):
        raise ValueError('AST head requires exact public35')
    return sources


def features(args):
    import numpy as np
    sources=validate_sources(args)
    acquired=read_report(args.media/'acquisition.json')
    if (acquired['source_snapshot_sha256']!=SOURCE_SHA
            or [r['id'] for r in acquired['files']]!=list(EXTENDED_IDS)):
        raise ValueError('AST acquisition provenance differs')
    encoder=AstRepresentation(); arrays={}; cases=[]
    for row,item in zip(sources,acquired['files']):
        path=args.media/(row['id']+'.mp3')
        if (path.is_symlink() or path.stat().st_size!=item['bytes'] or digest(path)!=item['sha256']
                or item['preview_url']!=row['preview_url']):
            raise ValueError('Fixed public preview differs')
        decoded=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(path),'-map','0:a:0','-vn',
            '-t','20','-ac','1','-ar','16000','-f','s16le','pipe:1'],capture_output=True,timeout=20)
        if decoded.returncode or not 2<=len(decoded.stdout)<=640000 or len(decoded.stdout)%2:
            raise ValueError('AST public decode failed')
        pcm=np.frombuffer(decoded.stdout,dtype='<i2').astype(np.float32)/32768
        arrays[row['id']],frames=extract_spans(encoder,pcm)
        if digest(path)!=item['sha256']:
            raise ValueError('Public preview changed during decode')
        cases.append(dict(id=row['id'],role=row['role'],weak_class=row['provisionalclass'],
            sourcegroup=row['sourcegroup'],frames=frames,actual_pcm_samples=len(pcm),
            preview_sha256=item['sha256'],audio_review_completed=False))
    np.savez_compressed(args.output/'features-public.npz',**arrays)
    write(args.output/'features.json',dict(cases=cases,features_sha256=digest(args.output/'features-public.npz'),
        source_snapshot_sha256=SOURCE_SHA,model_sha256=MODEL_SHA,runtime=RUNTIME,
        memory_graph_sha256=encoder.memory_sha256,memory_graph_bytes=encoder.memory_bytes,
        frontend_sha256=digest(ROOT/'tools/research/ondevice_ast_features.py'),
        source_acquisition_sha256=digest(args.media/'acquisition.json'),private_media_read=False,
        training=False,product_changed=False))


def fit(args):
    import numpy as np
    sources=validate_sources(args)
    metadata=read_report(args.features/'features.json')
    archive=args.features/'features-public.npz'
    if (archive.is_symlink() or not 1<=archive.stat().st_size<=2*1024**2
            or digest(archive)!=metadata['features_sha256'] or metadata['model_sha256']!=MODEL_SHA
            or metadata['source_snapshot_sha256']!=SOURCE_SHA or metadata['runtime']!=RUNTIME
            or metadata['frontend_sha256']!=digest(ROOT/'tools/research/ondevice_ast_features.py')):
        raise ValueError('AST feature provenance differs')
    cases=metadata['cases']
    if [c['id'] for c in cases]!=list(EXTENDED_IDS):
        raise ValueError('AST feature cases differ')
    for c,row in zip(cases,sources):
        if (c['role'],c['weak_class'],c['sourcegroup'])!=(row['role'],row['provisionalclass'],row['sourcegroup']):
            raise ValueError('AST source role differs')
    with np.load(archive,allow_pickle=False) as data:
        train=[c for c in cases if c['role']=='train']
        if len(train)!=21 or len({c['sourcegroup'] for c in train})!=18:
            raise ValueError('AST train partition differs')
        x=np.stack([pooled(data[c['id']],c['frames']) for c in train])
        labels=np.asarray([float(c['weak_class']=='infant_vocal') for c in train])
        chosen,cv=select(x,train,768)
        w,b=fit_binary(x,labels,presence_weights(train),chosen,768)
        head=args.output/'head-ast-presence-public.npz'
        np.savez_compressed(head,weights=w.astype(np.float32),bias=np.asarray(b,np.float32))
        write(args.output/'selection.json',dict(selected_penalty=chosen,source_cv=cv,
            train_ids=[c['id'] for c in train],validation_features_read=False,private_features_read=False,
            head_sha256=digest(head),features_sha256=digest(archive),source_snapshot_sha256=SOURCE_SHA))
        rows=[]
        for c in cases:
            vector=pooled(data[c['id']],c['frames'])
            rows.append(dict(id=c['id'],role=c['role'],weak_class=c['weak_class'],sourcegroup=c['sourcegroup'],
                **score(vector,w,b),spans=score_spans(data[c['id']],c['frames'],w,b)))
    write(args.output/'fit.json',dict(cases=rows,train_cases=21,validation_cases=14,
        parameters=769,fp32_parameter_bytes=3076,head_sha256=digest(head),model_sha256=MODEL_SHA,
        source_snapshot_sha256=SOURCE_SHA,features_sha256=digest(archive),
        configuration=dict(dimension=768,steps=300,lr=.03,l2=chosen,threshold=.8,margin=.4,
            normalization='actual_sample_weighted_span_mean_then_clip_L2',selection='train_source_macro_nll'),
        weak_labels_only=True,private_training=False,calibrated_probability=False,
        quality_validated=False,android_validated=False,product_changed=False))


def evaluate(args):
    import numpy as np
    from efficientat_probe import load_corpus
    metadata=read_report(args.head/'fit.json')
    path=args.head/'head-ast-presence-public.npz'
    if (path.is_symlink() or not 1<=path.stat().st_size<=64*1024 or digest(path)!=metadata['head_sha256']
            or metadata['model_sha256']!=MODEL_SHA or metadata['source_snapshot_sha256']!=SOURCE_SHA
            or metadata['configuration']['dimension']!=768 or metadata['configuration']['threshold']!=.8
            or metadata['configuration']['margin']!=.4 or metadata['private_training']):
        raise ValueError('Frozen AST head differs')
    with np.load(path,allow_pickle=False) as data:
        w,b=data['weights'],data['bias']
    if w.shape!=(768,) or b.shape!=() or not np.isfinite(w).all() or not np.isfinite(b):
        raise ValueError('Stored AST head invalid')
    entries=load_corpus(args.corpus); encoder=AstRepresentation(); cases=[]
    for entry in entries:
        path=args.corpus/(entry['id']+'.f32')
        values,frames=extract_spans(encoder,np.fromfile(path,dtype='<f4'))
        rows=score_spans(values,frames,w,b)
        if digest(path)!=entry['pcm_sha256']:
            raise ValueError('Evaluation-only PCM changed')
        cases.append(dict(id=entry['id'],expected=entry.get('expected'),duration_ms=entry['duration_ms'],
            pcm_sha256=entry['pcm_sha256'],spans=rows,any_supported_span=any(r['infant_support'] for r in rows)))
    write(args.output/'evaluation-local.json',dict(cases=cases,head_sha256=metadata['head_sha256'],
        corpus_manifest_sha256=digest(args.corpus/'manifest.json'),training=False,private_data_evaluation_only=True,
        actual_infant_event_timing_validated=False,quality_validated=False,android_validated=False,product_changed=False))


def provenance():
    files=('ast_infant_head_probe.py','ast_embedding_interface.py','ondevice_ast_features.py',
           'infant_presence_probe.py','infant_head_source_cv.py','infant_head_screen.py')
    return {f:digest(ROOT/'tools/research'/f) for f in files}


def worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual=dict(python=platform.python_version(),numpy=np.__version__,onnx=onnx.__version__,ort=ort.__version__)
    if actual!=RUNTIME:
        raise ValueError('Fixed AST head runtime differs')
    {'features':features,'fit':fit,'evaluate':evaluate}[args.mode](args)
    if digest(MODEL)!=MODEL_SHA:
        raise ValueError('Product AST model changed')
    write(args.output/'completion.json',dict(status='completed',mode=args.mode,runtime=actual,
        source_code_sha256=provenance(),product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('features','fit','evaluate'))
    parser.add_argument('--output',type=Path,required=True)
    for name in ('sources','media','ffmpeg','features','head','corpus'):
        parser.add_argument('--'+name,type=Path)
    parser.add_argument('--sources-sha'); parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('output','sources','media','ffmpeg','features','head','corpus'):
        if getattr(args,name) is not None: setattr(args,name,getattr(args,name).resolve())
    validate_output_path(args.output)
    required={'features':('sources','sources_sha','media','ffmpeg'),
        'fit':('sources','sources_sha','features'),'evaluate':('head','corpus')}[args.mode]
    if any(getattr(args,name) is None for name in required):
        raise SystemExit('Missing required AST head mode arguments')
    if args.sources: validate_sources(args)
    if args.worker: worker(args); return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'); env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),args.mode,'--worker','--output',str(args.output)]
    for name in ('sources','sources_sha','media','ffmpeg','features','head','corpus'):
        if getattr(args,name) is not None: argv+=['--'+name.replace('_','-'),str(getattr(args,name))]
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,
        memory_limit_bytes=(2 if args.mode=='fit' else 3)*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('AST infant head failed; inspect bounded evidence')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['mode']!=args.mode or done['runtime']!=RUNTIME
            or done['source_code_sha256']!=provenance()):
        raise SystemExit('AST head completion provenance differs')
    print(json.dumps(dict(status='completed',mode=args.mode,product_changed=False)))


if __name__=='__main__': main()
