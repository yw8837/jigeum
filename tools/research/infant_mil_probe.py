"""Fixed weak multiple-instance presence head. Public training, local evaluation only."""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import sys

from infant_head_screen import (ROOT,RUNTIME,EXTENDED_IDS,YAMNET_SHA,FixedEncoder,read_sources,
    read_report,digest,normalize,offline,runtime,write)
from infant_head_source_cv import folds,PENALTIES
from infant_presence_probe import sigmoid,presence_weights,windows_for,temporal_support,supported
from ondevice_av_probe import validate_output_path

SOURCE_SHA='27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9'
FEATURE_SHA='5dabe7acace700d68ae7bcc3db83d40cbe6df4fbb0b26c7a65756cbe61c2b240'


def bag(features,frames):
    import numpy as np
    if features.shape!=(len(frames),1024) or not np.isfinite(features).all() or not 1<=len(frames)<=21:
        raise ValueError('Invalid MIL bag shape')
    previous_end=0
    kept=[]
    for i,frame in enumerate(frames):
        if (frame['start_sample']!=previous_end or not isinstance(frame['real_samples'],int)
                or not 1<=frame['real_samples']<=15600 or not np.isfinite(frame['rms']) or frame['rms']<0):
            raise ValueError('Invalid actual MIL window chronology')
        previous_end+=frame['real_samples']
        if frame['rms']>=.003:
            kept.append(i)
    if not kept:
        raise ValueError('Quiet-only MIL bag')
    values=np.stack([normalize(features[i]) for i in kept])
    durations=np.asarray([frames[i]['real_samples'] for i in kept],np.float64)
    return values,durations/durations.sum()


def bag_logit_and_attention(logits,durations):
    import numpy as np
    if (logits.ndim!=1 or not len(logits) or durations.shape!=logits.shape
            or not np.isfinite(logits).all() or not np.isfinite(durations).all()
            or np.any(durations<=0) or abs(float(durations.sum())-1)>1e-10):
        raise ValueError('Invalid MIL logits or time weights')
    maximum=float(logits.max())
    scaled=durations*np.exp(logits-maximum)
    total=float(scaled.sum())
    return maximum+float(np.log(total)),scaled/total


def loss_and_gradient(bags,labels,weights,w,b,penalty):
    import numpy as np
    loss=0.
    gw=penalty*w.copy()
    gb=0.
    for (x,durations),label,weight in zip(bags,labels,weights):
        logit,attention=bag_logit_and_attention(x@w+b,durations)
        loss+=weight*float(np.logaddexp(0,logit)-label*logit)
        residual=weight*(float(sigmoid(logit))-label)
        gw+=residual*(attention@x)
        gb+=residual
    return loss+.5*penalty*float(w@w),gw,gb


def fit_mil(bags,labels,weights,penalty):
    import numpy as np
    if (not bags or labels.shape!=(len(bags),) or weights.shape!=(len(bags),)
            or set(labels.tolist())!={0.,1.} or not np.isfinite(weights).all() or np.any(weights<=0)
            or abs(float(weights.sum())-1)>1e-10 or penalty not in PENALTIES):
        raise ValueError('Invalid MIL training domain')
    for x,durations in bags:
        if x.ndim!=2 or x.shape[1]!=1024 or not np.isfinite(x).all():
            raise ValueError('Invalid MIL training features')
        bag_logit_and_attention(np.zeros(len(x)),durations)
    w=np.zeros(1024,np.float64); b=0.
    mw=vw=np.zeros_like(w); mb=vb=0.
    for step in range(1,301):
        loss,gw,gb=loss_and_gradient(bags,labels,weights,w,b,penalty)
        if not np.isfinite(loss) or not np.isfinite(gw).all() or not np.isfinite(gb):
            raise ValueError('Nonfinite MIL fit')
        mw=.9*mw+.1*gw; vw=.999*vw+.001*gw**2
        mb=.9*mb+.1*gb; vb=.999*vb+.001*gb**2
        w-=.03*(mw/(1-.9**step))/(np.sqrt(vw/(1-.999**step))+1e-8)
        b-=.03*(mb/(1-.9**step))/(np.sqrt(vb/(1-.999**step))+1e-8)
    if not np.isfinite(w).all() or not np.isfinite(b):
        raise ValueError('Nonfinite MIL head')
    return w,b


def select(bags,cases):
    import numpy as np
    if len(bags)!=len(cases):
        raise ValueError('MIL source and bag counts differ')
    labels=np.asarray([float(c['weak_class']=='infant_vocal') for c in cases])
    rows=[]
    for penalty in PENALTIES:
        details=[]
        for source,train,held in folds(cases):
            w,b=fit_mil([bags[i] for i in train],labels[train],presence_weights([cases[i] for i in train]),penalty)
            losses=[]
            for i in held:
                x,durations=bags[i]
                logit,_=bag_logit_and_attention(x@w+b,durations)
                losses.append(float(np.logaddexp(0,logit)-labels[i]*logit))
            loss=float(np.mean(losses))
            if not np.isfinite(loss):
                raise ValueError('Nonfinite MIL CV loss')
            details.append(dict(sourcegroup=source,train_ids=[cases[i]['id'] for i in train],
                                held_ids=[cases[i]['id'] for i in held],negative_log_likelihood=loss))
        rows.append(dict(penalty=penalty,source_macro_nll=float(np.mean([r['negative_log_likelihood'] for r in details])),folds=details))
    chosen=min(rows,key=lambda row:(row['source_macro_nll'],-row['penalty']))['penalty']
    return chosen,rows


def fit(args):
    import numpy as np
    sources=read_sources(args.sources,args.sources_sha)
    metadata=read_report(args.features/'features.json'); archive=args.features/'features-public.npz'
    if (args.sources_sha!=SOURCE_SHA or [r['id'] for r in sources]!=list(EXTENDED_IDS)
            or archive.is_symlink() or digest(archive)!=FEATURE_SHA or metadata['features_sha256']!=FEATURE_SHA
            or metadata['model_sha256']!=YAMNET_SHA or metadata['source_snapshot_sha256']!=SOURCE_SHA):
        raise ValueError('Fixed MIL source/features provenance differs')
    cases=metadata['cases']
    if [c['id'] for c in cases]!=list(EXTENDED_IDS):
        raise ValueError('Fixed MIL feature cases differ')
    for case,row in zip(cases,sources):
        if (case['role'],case['weak_class'],case['sourcegroup'])!=(row['role'],row['provisionalclass'],row['sourcegroup']):
            raise ValueError('MIL source role differs')
    with np.load(archive,allow_pickle=False) as data:
        train=[c for c in cases if c['role']=='train']
        bags=[bag(data[c['id']],c['frames']) for c in train]
        chosen,cv=select(bags,train)
        labels=np.asarray([float(c['weak_class']=='infant_vocal') for c in train])
        w,b=fit_mil(bags,labels,presence_weights(train),chosen)
        path=args.output/'head-presence-public.npz'
        np.savez_compressed(path,weights=w.astype(np.float32),bias=np.asarray(b,np.float32))
        write(args.output/'selection.json',dict(selected_penalty=chosen,grid=PENALTIES,source_cv=cv,
            train_ids=[c['id'] for c in train],validation_features_read=False,private_features_read=False,
            source_snapshot_sha256=SOURCE_SHA,features_sha256=FEATURE_SHA,head_sha256=digest(path)))
        rows=[]
        for case in cases:
            x,durations=bag(data[case['id']],case['frames'])
            logit,_=bag_logit_and_attention(x@w+b,durations)
            probability=float(sigmoid(logit))
            fp32,_=bag_logit_and_attention(x.astype(np.float32)@w.astype(np.float32)+np.float32(b),durations)
            error=abs(probability-float(sigmoid(fp32)))
            if error>5e-5:
                raise ValueError('MIL FP32 parity differs')
            windows=windows_for(data[case['id']],case['frames'],w,b)
            rows.append(dict(id=case['id'],role=case['role'],weak_class=case['weak_class'],sourcegroup=case['sourcegroup'],
                infant_score=probability,infant_support=supported(probability),windows=windows,
                float32_max_absolute_error=error,**temporal_support(windows)))
    write(args.output/'fit.json',dict(cases=rows,train_cases=len(train),validation_cases=len(cases)-len(train),
        parameters=1025,fp32_parameter_bytes=4100,head_sha256=digest(path),
        source_snapshot_sha256=SOURCE_SHA,features_sha256=FEATURE_SHA,
        configuration=dict(steps=300,lr=.03,l2=chosen,threshold=.8,margin=.4,
            pooling='actual_sample_weighted_log_mean_exp',temperature=1.,normalization='window_L2_only'),
        weak_labels_only=True,private_training=False,quality_validated=False,android_validated=False,product_changed=False))


def worker(args):
    offline(); actual=runtime()
    if args.mode=='fit':
        fit(args)
    else:
        from infant_presence_probe import evaluate
        evaluate(args)
    write(args.output/'completion.json',dict(status='completed',mode=args.mode,runtime=actual,
        runner_sha256=digest(Path(__file__)),feature_runner_sha256=digest(ROOT/'tools/research/infant_head_screen.py'),
        presence_runner_sha256=digest(ROOT/'tools/research/infant_presence_probe.py'),product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('fit','evaluate'))
    parser.add_argument('--output',type=Path,required=True)
    for name in ('sources','features','head','corpus'):
        parser.add_argument('--'+name,type=Path)
    parser.add_argument('--sources-sha'); parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('output','sources','features','head','corpus'):
        value=getattr(args,name)
        if value is not None: setattr(args,name,value.resolve())
    validate_output_path(args.output)
    required=('sources','sources_sha','features') if args.mode=='fit' else ('head','corpus')
    if any(getattr(args,name) is None for name in required):
        raise SystemExit('Missing required presence mode arguments')
    if args.sources: read_sources(args.sources,args.sources_sha)
    if args.worker: worker(args); return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),args.mode,'--worker','--output',str(args.output)]
    for name in ('sources','sources_sha','features','head','corpus'):
        value=getattr(args,name)
        if value is not None: argv+=['--'+name.replace('_','-'),str(value)]
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,memory_limit_bytes=2*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Infant MIL probe failed')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['mode']!=args.mode or done['runtime']!=RUNTIME
            or done['runner_sha256']!=digest(Path(__file__))
            or done['feature_runner_sha256']!=digest(ROOT/'tools/research/infant_head_screen.py')
            or done['presence_runner_sha256']!=digest(ROOT/'tools/research/infant_presence_probe.py')):
        raise SystemExit('MIL completion provenance differs')
    print(json.dumps(dict(status='completed',mode=args.mode,product_changed=False)))


if __name__=='__main__': main()
