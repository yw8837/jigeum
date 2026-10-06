"""Public-only weak infant-presence head; local evaluation, never a need translator."""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import sys

from infant_head_screen import (ROOT,RUNTIME,CLASSES,EXTENDED_IDS,YAMNET_SHA,FixedEncoder,read_sources,
    read_report,digest,normalize,pooled,offline,runtime,write)
from infant_head_source_cv import folds,PENALTIES
from ondevice_av_probe import validate_output_path


def sigmoid(logits):
    import numpy as np
    if not np.isfinite(logits).all():
        raise ValueError('Nonfinite infant-presence logits')
    return np.exp(-np.logaddexp(0,-logits))


def presence_weights(cases):
    import numpy as np
    if not cases or any(c['role']!='train' or c['weak_class'] not in CLASSES for c in cases):
        raise ValueError('Only training sources may supply presence fit weights')
    labels=np.asarray([c['weak_class']=='infant_vocal' for c in cases])
    if set(labels.tolist())!={False,True}:
        raise ValueError('Presence fit needs positive and negative sources')
    values=[]
    for i,case in enumerate(cases):
        source_count=len({c['sourcegroup'] for j,c in enumerate(cases) if labels[j]==labels[i]})
        clip_count=sum(c['sourcegroup']==case['sourcegroup'] and labels[j]==labels[i] for j,c in enumerate(cases))
        values.append(1/(2*source_count*clip_count))
    result=np.asarray(values,np.float64)
    if abs(float(result.sum())-1)>1e-10:
        raise ValueError('Presence weights must sum to one')
    return result


def fit_binary(x,labels,weights,penalty):
    import numpy as np
    if (x.shape!=(len(labels),1024) or labels.shape!=(len(x),) or weights.shape!=(len(x),)
            or not np.isfinite(x).all() or not np.isfinite(weights).all()
            or np.any(weights<=0) or abs(float(weights.sum())-1)>1e-10
            or set(labels.tolist())!={0.,1.} or penalty not in PENALTIES):
        raise ValueError('Invalid binary training domain')
    w=np.zeros(1024,np.float64)
    b=0.
    mw=vw=np.zeros_like(w)
    mb=vb=0.
    for step in range(1,301):
        grad=(sigmoid(x@w+b)-labels)*weights
        gw=x.T@grad+penalty*w
        gb=float(grad.sum())
        mw=.9*mw+.1*gw
        vw=.999*vw+.001*gw**2
        mb=.9*mb+.1*gb
        vb=.999*vb+.001*gb**2
        w-=.03*(mw/(1-.9**step))/(np.sqrt(vw/(1-.999**step))+1e-8)
        b-=.03*(mb/(1-.9**step))/(np.sqrt(vb/(1-.999**step))+1e-8)
    if not np.isfinite(w).all() or not np.isfinite(b):
        raise ValueError('Nonfinite fitted presence head')
    return w,b


def select(x,cases):
    import numpy as np
    if x.shape!=(len(cases),1024) or not np.isfinite(x).all():
        raise ValueError('Invalid training-only presence feature matrix')
    labels=np.asarray([float(c['weak_class']=='infant_vocal') for c in cases])
    rows=[]
    for penalty in PENALTIES:
        details=[]
        for source,train,held in folds(cases):
            w,b=fit_binary(x[train],labels[train],presence_weights([cases[i] for i in train]),penalty)
            logits=x[held]@w+b
            loss=float(np.mean(np.logaddexp(0,logits)-labels[held]*logits))
            if not np.isfinite(loss):
                raise ValueError('Nonfinite presence CV loss')
            details.append(dict(sourcegroup=source,train_ids=[cases[i]['id'] for i in train],
                held_ids=[cases[i]['id'] for i in held],negative_log_likelihood=loss))
        rows.append(dict(penalty=penalty,source_macro_nll=float(np.mean([r['negative_log_likelihood'] for r in details])),folds=details))
    chosen=min(rows,key=lambda row:(row['source_macro_nll'],-row['penalty']))['penalty']
    return chosen,rows


def supported(value):
    import math
    if not math.isfinite(value) or not 0<=value<=1:
        raise ValueError('Invalid infant-presence score')
    return bool(value>=.8 and value-(1-value)>=.4)


def windows_for(features,frames,w,b):
    import numpy as np
    if features.shape!=(len(frames),1024) or not np.isfinite(features).all() or not frames:
        raise ValueError('Invalid presence windows')
    windows=[]
    for feature,frame in zip(features,frames):
        value=float(sigmoid(normalize(feature)@w+b)) if frame['rms']>=.003 else None
        windows.append(dict(frame,quiet=value is None,infant_score=value,
                            infant_support=False if value is None else supported(value)))
    return windows


def temporal_support(windows):
    return dict(support_seconds=sum(f['real_samples'] for f in windows if f['infant_support'])/16000,
                consecutive_support=any(a['infant_support'] and b['infant_support'] for a,b in zip(windows,windows[1:])))


def fit(args):
    import numpy as np
    sources=read_sources(args.sources,args.sources_sha)
    if [r['id'] for r in sources]!=list(EXTENDED_IDS):
        raise ValueError('Presence contract requires exact extended35 sources')
    metadata=read_report(args.features/'features.json')
    archive=args.features/'features-public.npz'
    if (archive.is_symlink() or not 1<=archive.stat().st_size<=2*1024**2
            or digest(archive)!=metadata['features_sha256'] or metadata['model_sha256']!=YAMNET_SHA
            or metadata['source_snapshot_sha256']!=args.sources_sha):
        raise ValueError('Fixed extended features/provenance differs')
    cases=metadata['cases']
    if [c['id'] for c in cases]!=list(EXTENDED_IDS):
        raise ValueError('Expected extended35 feature cases')
    for case,row in zip(cases,sources):
        if (case['role'],case['weak_class'],case['sourcegroup'])!=(row['role'],row['provisionalclass'],row['sourcegroup']):
            raise ValueError('Source/role differs from pinned snapshot')
    with np.load(archive,allow_pickle=False) as data:
        train=[c for c in cases if c['role']=='train']
        if len(train)!=21 or len({c['sourcegroup'] for c in train})!=18:
            raise ValueError('Expected 21 train clips and 18 source groups')
        x=np.stack([pooled(data[c['id']],c['frames']) for c in train])
        labels=np.asarray([float(c['weak_class']=='infant_vocal') for c in train])
        chosen,cv=select(x,train)
        w,b=fit_binary(x,labels,presence_weights(train),chosen)
        np.savez_compressed(args.output/'head-presence-public.npz',weights=w.astype(np.float32),bias=np.asarray(b,np.float32))
        write(args.output/'selection.json',dict(selected_penalty=chosen,grid=PENALTIES,source_cv=cv,
            train_ids=[c['id'] for c in train],validation_features_read=False,private_features_read=False,
            source_snapshot_sha256=args.sources_sha,features_sha256=digest(archive),
            head_sha256=digest(args.output/'head-presence-public.npz')))
        rows=[]
        for case in cases:
            vector=pooled(data[case['id']],case['frames'])
            probability=float(sigmoid(vector@w+b))
            fp32=float(sigmoid(vector.astype(np.float32)@w.astype(np.float32)+np.float32(b)))
            if abs(probability-fp32)>5e-5:
                raise ValueError('Presence FP32 parity differs')
            windows=windows_for(data[case['id']],case['frames'],w,b)
            rows.append(dict(id=case['id'],role=case['role'],weak_class=case['weak_class'],sourcegroup=case['sourcegroup'],
                infant_score=probability,caption_infant_presence=case['weak_class']=='infant_vocal',
                infant_support=supported(probability),float32_max_absolute_error=abs(probability-fp32),
                windows=windows,**temporal_support(windows)))
    write(args.output/'fit.json',dict(cases=rows,train_ids=[c['id'] for c in train],train_cases=21,validation_cases=14,
        parameters=1025,fp32_parameter_bytes=4100,head_sha256=digest(args.output/'head-presence-public.npz'),
        source_snapshot_sha256=args.sources_sha,features_report_sha256=digest(args.features/'features.json'),
        configuration=dict(steps=300,lr=.03,l2=chosen,normalization='clip_L2_only',threshold=.8,margin=.4,
            class_source_clip_balanced=True,selection='train_source_cv_macro_binary_nll'),
        weak_labels_only=True,private_training=False,calibrated_probability=False,
        overlap_separation_validated=False,quality_validated=False,android_validated=False,product_changed=False))


def evaluate(args):
    import numpy as np
    from efficientat_probe import load_corpus
    metadata=read_report(args.head/'fit.json')
    path=args.head/'head-presence-public.npz'
    if (path.is_symlink() or not 1<=path.stat().st_size<=64*1024 or digest(path)!=metadata['head_sha256']
            or metadata['configuration']['threshold']!=.8 or metadata['configuration']['margin']!=.4):
        raise ValueError('Fixed presence head differs')
    with np.load(path,allow_pickle=False) as data:
        w,b=data['weights'],data['bias']
    if w.shape!=(1024,) or b.shape!=() or not np.isfinite(w).all() or not np.isfinite(b):
        raise ValueError('Invalid stored presence head')
    entries=load_corpus(args.corpus)
    encoder=FixedEncoder()
    cases=[]
    for entry in entries:
        pcm_path=args.corpus/(entry['id']+'.f32')
        features,frames=encoder.extract(np.fromfile(pcm_path,dtype='<f4'))
        windows=windows_for(features,frames,w,b)
        if digest(pcm_path)!=entry['pcm_sha256']:
            raise ValueError('Evaluation-only PCM changed')
        cases.append(dict(id=entry['id'],expected=entry.get('expected'),duration_ms=entry['duration_ms'],
            pcm_sha256=entry['pcm_sha256'],windows=windows,
            **temporal_support(windows)))
    write(args.output/'evaluation-local.json',dict(cases=cases,head_sha256=digest(path),
        corpus_manifest_sha256=digest(args.corpus/'manifest.json'),private_data_evaluation_only=True,
        training=False,overlap_separation_validated=False,quality_validated=False,android_validated=False,product_changed=False))


def worker(args):
    offline()
    actual=runtime()
    {'fit':fit,'evaluate':evaluate}[args.mode](args)
    write(args.output/'completion.json',dict(status='completed',mode=args.mode,runtime=actual,
        runner_sha256=digest(Path(__file__)),feature_runner_sha256=digest(ROOT/'tools/research/infant_head_screen.py'),
        folds_runner_sha256=digest(ROOT/'tools/research/infant_head_source_cv.py'),product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('fit','evaluate'))
    parser.add_argument('--output',type=Path,required=True)
    for name in ('sources','features','head','corpus'):
        parser.add_argument('--'+name,type=Path)
    parser.add_argument('--sources-sha')
    parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('output','sources','features','head','corpus'):
        value=getattr(args,name)
        if value is not None:
            setattr(args,name,value.resolve())
    validate_output_path(args.output)
    required=('sources','sources_sha','features') if args.mode=='fit' else ('head','corpus')
    if any(getattr(args,name) is None for name in required):
        raise SystemExit('Missing required presence mode arguments')
    if args.sources:
        read_sources(args.sources,args.sources_sha)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),args.mode,'--worker','--output',str(args.output)]
    for name in ('sources','sources_sha','features','head','corpus'):
        value=getattr(args,name)
        if value is not None:
            argv+=['--'+name.replace('_','-'),str(value)]
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,memory_limit_bytes=2*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Infant-presence probe failed')
    completion=read_report(args.output/'completion.json')
    if (completion['status']!='completed' or completion['mode']!=args.mode or completion['runtime']!=RUNTIME
            or completion['runner_sha256']!=digest(Path(__file__))
            or completion['feature_runner_sha256']!=digest(ROOT/'tools/research/infant_head_screen.py')
            or completion['folds_runner_sha256']!=digest(ROOT/'tools/research/infant_head_source_cv.py')):
        raise SystemExit('Presence completion provenance differs')
    print(json.dumps(dict(status='completed',mode=args.mode,product_changed=False)))


if __name__=='__main__':
    main()
