"""Fixed training-source-only regularization selection; not a final accuracy test."""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import sys

from infant_head_screen import (ROOT,IDS,CLASSES,RUNTIME,read_sources,read_report,digest,
    normalize,pooled,source_weights,softmax,supports,train_linear,offline,runtime,write,YAMNET_SHA)
from ondevice_av_probe import validate_output_path

PENALTIES=(.0001,.001,.01)


def folds(cases):
    if not cases or any(c['role']!='train' for c in cases):
        raise ValueError('Only training source cases permitted for CV')
    if len({c['id'] for c in cases})!=len(cases):
        raise ValueError('Duplicate training clip')
    groups=sorted({c['sourcegroup'] for c in cases})
    result=[]
    for group in groups:
        train=[i for i,c in enumerate(cases) if c['sourcegroup']!=group]
        held=[i for i,c in enumerate(cases) if c['sourcegroup']==group]
        if not held or {cases[i]['weak_class'] for i in train}!=set(CLASSES):
            raise ValueError('Every fold must retain all three training classes')
        result.append((group,train,held))
    return result


def select_from_training(x,cases):
    import numpy as np
    if x.shape!=(len(cases),1024) or not np.isfinite(x).all():
        raise ValueError('Invalid training-only feature matrix')
    labels=np.asarray([CLASSES.index(c['weak_class']) for c in cases])
    split=folds(cases)
    scores=[]
    for penalty in PENALTIES:
        rows=[]
        for group,train,held in split:
            train_cases=[cases[i] for i in train]
            w,b=train_linear(x[train],labels[train],source_weights(train_cases),penalty=penalty)
            probs=softmax(x[held]@w+b)
            correct=probs[np.arange(len(held)),labels[held]]
            loss=float(np.mean(-np.log(np.maximum(correct,1e-12))))
            if not np.isfinite(loss):
                raise ValueError('Nonfinite source-CV loss')
            rows.append(dict(sourcegroup=group,train_ids=[cases[i]['id'] for i in train],
                held_ids=[cases[i]['id'] for i in held],negative_log_likelihood=loss,
                caption_argmax_matches=int(np.count_nonzero(np.argmax(probs,axis=1)==labels[held]))))
        scores.append(dict(penalty=penalty,source_macro_nll=float(np.mean([r['negative_log_likelihood'] for r in rows])),folds=rows))
    chosen=min(scores,key=lambda row:(row['source_macro_nll'],-row['penalty']))['penalty']
    return chosen,scores


def worker(args):
    import numpy as np
    offline()
    actual=runtime()
    sources=read_sources(args.sources,args.sources_sha)
    metadata=read_report(args.features/'features.json')
    archive=args.features/'features-public.npz'
    if (archive.is_symlink() or not 1<=archive.stat().st_size<=2*1024**2
            or digest(archive)!=metadata['features_sha256'] or metadata['model_sha256']!=YAMNET_SHA
            or metadata['source_snapshot_sha256']!=args.sources_sha):
        raise ValueError('Fixed feature archive/provenance differs')
    cases=metadata['cases']
    if [c['id'] for c in cases]!=[r['id'] for r in sources]:
        raise ValueError('Fixed cases differ')
    for c,s in zip(cases,sources):
        if (c['role'],c['weak_class'],c['sourcegroup'])!=(s['role'],s['provisionalclass'],s['sourcegroup']):
            raise ValueError('Source/role differs')
    with np.load(archive,allow_pickle=False) as data:
        train=[c for c in cases if c['role']=='train']
        x=np.stack([pooled(data[c['id']],c['frames']) for c in train])
        chosen,cv=select_from_training(x,train)
        labels=np.asarray([CLASSES.index(c['weak_class']) for c in train])
        w,b=train_linear(x,labels,source_weights(train),penalty=chosen)
        # Freeze selection/weights before accessing validation arrays.
        np.savez_compressed(args.output/'head-weak-public.npz',weights=w.astype(np.float32),bias=b.astype(np.float32))
        write(args.output/'selection.json',dict(selected_penalty=chosen,grid=PENALTIES,source_cv=cv,
            train_ids=[c['id'] for c in train],training_sources=len(cv[0]['folds']),
            validation_features_read=False,private_features_read=False,source_snapshot_sha256=args.sources_sha,
            features_sha256=digest(archive),head_sha256=digest(args.output/'head-weak-public.npz'),
            runner_sha256=digest(Path(__file__)),linear_runner_sha256=digest(ROOT/'tools/research/infant_head_screen.py')))
        rows=[]
        for c in cases:
            vector=pooled(data[c['id']],c['frames'])
            probs=softmax(vector@w+b)
            fp32=softmax(vector.astype(np.float32)@w.astype(np.float32)+b.astype(np.float32))
            if np.max(np.abs(probs-fp32))>5e-5:
                raise ValueError('FP32 head parity differs')
            rows.append(dict(id=c['id'],role=c['role'],weak_class=c['weak_class'],sourcegroup=c['sourcegroup'],
                predicted=CLASSES[int(np.argmax(probs))],probabilities=probs.tolist(),
                infant_support=supports(probs),float32_max_absolute_error=float(np.max(np.abs(probs-fp32)))))
    confusion={}
    for role in ('train','validation'):
        matrix=np.zeros((3,3),int)
        for row in rows:
            if row['role']==role:
                matrix[CLASSES.index(row['weak_class']),CLASSES.index(row['predicted'])]+=1
        confusion[role]=matrix.tolist()
    write(args.output/'fit.json',dict(cases=rows,confusion_weak_caption_only=confusion,classes=CLASSES,
        train_ids=[c['id'] for c in train],train_cases=len(train),validation_cases=len(cases)-len(train),parameters=3075,
        head_sha256=digest(args.output/'head-weak-public.npz'),source_snapshot_sha256=args.sources_sha,
        configuration=dict(steps=300,lr=.03,l2=chosen,initialization='zero',normalization='clip_L2_only',
            class_source_clip_balanced=True,infant_threshold=.8,margin=.4,selection='train_source_cv_macro_nll'),
        strong_labels_approved=False,private_training=False,calibrated_probability=False,
        quality_validated=False,android_validated=False,product_changed=False))
    write(args.output/'completion.json',dict(status='completed',runtime=actual,mode='source_cv',
        runner_sha256=digest(Path(__file__)),linear_runner_sha256=digest(ROOT/'tools/research/infant_head_screen.py')))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('sources','features','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--sources-sha',required=True)
    parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('sources','features','output'):
        setattr(args,name,getattr(args,name).resolve())
    validate_output_path(args.output)
    read_sources(args.sources,args.sources_sha)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    command=[sys.executable,'-B',str(Path(__file__).resolve()),'--worker',
        '--sources',str(args.sources),'--sources-sha',args.sources_sha,'--features',str(args.features),'--output',str(args.output)]
    result=run_bounded(command,cwd=ROOT,env=env,log_path=args.output/'run.log',
                       timeout_seconds=180,memory_limit_bytes=2*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Training-source CV failed')
    completion=read_report(args.output/'completion.json')
    if (completion['status']!='completed' or completion['runtime']!=RUNTIME
            or completion['runner_sha256']!=digest(Path(__file__))
            or completion['linear_runner_sha256']!=digest(ROOT/'tools/research/infant_head_screen.py')):
        raise SystemExit('Source-CV completion differs')
    print(json.dumps(dict(status='completed',mode='source_cv',product_changed=False)))


if __name__=='__main__':
    main()
