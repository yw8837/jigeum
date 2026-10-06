"""One fixed weak-caption feasibility head, public training only; local evaluation."""
import argparse
from dataclasses import asdict
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import socket
import subprocess
import sys
import urllib.parse
import urllib.request

from infant_preview_interface import YAMNET_SHA, digest, dequantize_embedding, filter_actual_public_pcm
from ondevice_av_probe import validate_output_path

ROOT = Path(__file__).resolve().parents[2]
CLASSES = ('infant_vocal', 'adult_vocal', 'other_or_uncertain')
TRAIN = (('113220','113221','113223','46963','47371','377237','8157'),
         ('184432','168494','87680','122204'), ('256684','412332','148628','410616'))
VALIDATION = (('18274','18277','18276','18273'),('241477','17643'),('51319','118659'))
IDS = tuple(item for group in TRAIN + VALIDATION for item in group)
EXTRA_ADULT_TRAIN = ('92388','277549','119459','254869','319537','219663')
EXTRA_ADULT_VALIDATION = ('42841','134720','238819','384213','336537','353291')
EXTENDED_IDS = IDS + EXTRA_ADULT_TRAIN + EXTRA_ADULT_VALIDATION
RUNTIME = dict(python='3.12.14', numpy='2.5.3', litert='2.2.0', flatbuffers='25.12.19')


def offline():
    def denied(*unused, **unused_kw):
        raise RuntimeError('Network forbidden during feature/training/evaluation')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied


def runtime():
    import numpy as np
    actual = dict(python=platform.python_version(), numpy=np.__version__,
                  litert=importlib.metadata.version('ai-edge-litert'),
                  flatbuffers=importlib.metadata.version('flatbuffers'))
    if actual != RUNTIME:
        raise ValueError('Fixed head-screen runtime differs')
    return actual


def expected_role(identity):
    if identity in EXTRA_ADULT_TRAIN:
        return 'train','adult_vocal'
    if identity in EXTRA_ADULT_VALIDATION:
        return 'validation','adult_vocal'
    for role, groups in (('train',TRAIN),('validation',VALIDATION)):
        for label, group in enumerate(groups):
            if identity in group:
                return role, CLASSES[label]
    raise ValueError('ID outside pinned subset')


def allowed_license(value):
    parsed = urllib.parse.urlsplit(value)
    return (parsed.scheme in ('http','https') and parsed.netloc == 'creativecommons.org'
            and not parsed.query and not parsed.fragment
            and parsed.path in ('/publicdomain/zero/1.0/','/licenses/by/3.0/','/licenses/by/4.0/'))


def validate_sources(rows):
    if not isinstance(rows,list) or len(rows) not in (len(IDS),len(EXTENDED_IDS)):
        raise ValueError('Expected exact pinned 23 or 35 source rows (47370 excluded)')
    expected_ids=IDS if len(rows)==len(IDS) else EXTENDED_IDS
    seen = set()
    for row in rows:
        identity = row.get('id')
        if identity in seen:
            raise ValueError('Duplicate source ID')
        seen.add(identity)
        role, label = expected_role(identity)
        url = urllib.parse.urlsplit(row['preview_url'])
        pattern = '/previews/' + str(int(identity)//1000) + '/' + identity + r'_[0-9]+-lq\.mp3'
        if (url.scheme != 'https' or url.netloc != 'cdn.freesound.org'
                or not re.fullmatch(pattern,url.path) or url.query or url.fragment):
            raise ValueError('Preview must be exact-ID official CDN path')
        if not allowed_license(row['current_license_url']) or not allowed_license(row['archived_license_url']):
            raise ValueError('Unpermitted current/archived sound license')
        if (row['role'] != role or row['provisionalclass'] != label
                or row.get('training_blocked') or not isinstance(row.get('sourcegroup'),str)
                or not 1 <= len(row['sourcegroup']) <= 200):
            raise ValueError('Source role/weak class/provenance boundary differs')
    if seen != set(expected_ids):
        raise ValueError('Missing source ID')
    train_sources = {row['sourcegroup'] for row in rows if row['role']=='train'}
    validation_sources = {row['sourcegroup'] for row in rows if row['role']=='validation'}
    if train_sources & validation_sources:
        raise ValueError('Training and validation sources overlap')
    return sorted(rows,key=lambda row:expected_ids.index(row['id']))


def read_sources(path, expected_sha):
    if path.is_symlink() or not 1 <= path.stat().st_size <= 2*1024**2 or digest(path) != expected_sha:
        raise ValueError('Source snapshot hash/size differs')
    return validate_sources(json.loads(path.read_text('utf-8'))['files'])


def read_report(path):
    if path.is_symlink() or not 1 <= path.stat().st_size <= 8*1024**2:
        raise ValueError('Report size/location invalid')
    return json.loads(path.read_text('utf-8'))


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*unused,**unused_kw):
        raise ValueError('Public preview redirect prohibited')


def verified_reuse(reuse_root, row):
    if reuse_root is None:
        return None
    snapshot=reuse_root/'acquisition.json'
    if not snapshot.exists():
        snapshot=reuse_root/'acquisition-progress.json'
    previous=[r for r in read_report(snapshot)['files'] if r['id']==row['id']]
    if not previous:
        return None
    path=reuse_root/(row['id']+'.mp3')
    item=previous[0]
    if (len(previous)!=1 or path.is_symlink() or not 1<=path.stat().st_size<=2*1024**2
            or path.stat().st_size!=item['bytes'] or digest(path)!=item['sha256']):
        raise ValueError('Reused preview differs from earlier snapshot')
    if (item.get('preview_url') or item.get('url')) != row['preview_url']:
        raise ValueError('Reused preview URL differs')
    return path.read_bytes()


def acquire(args):
    rows = read_sources(args.sources,args.sources_sha)
    opener = urllib.request.build_opener(NoRedirects())
    result = []
    for row in rows:
        data = verified_reuse(args.reuse,row)
        reused = data is not None
        if data is None:
            print(json.dumps(dict(acquiring_public_id=row['id'])),flush=True)
            request = urllib.request.Request(row['preview_url'],headers={'User-Agent':'Agattok-local-research/1.0'})
            with opener.open(request,timeout=20) as response:
                if (response.status!=200 or response.url!=row['preview_url']
                        or response.headers.get_content_type() not in ('audio/mpeg','application/octet-stream')):
                    raise ValueError('Expected fixed public MP3 response')
                data = response.read(2*1024**2+1)
        if not 1 <= len(data) <= 2*1024**2:
            raise ValueError('Public preview size exceeds cap')
        path = args.output/(row['id']+'.mp3')
        with path.open('xb') as stream:
            stream.write(data)
        result.append(dict(row,bytes=len(data),sha256=digest(path),reused_previous_preview=reused,
                           lossy_preview_not_fsd_pcm=True,audio_review_completed=False))
        # A failed network response is never reusable. Only fully written, hashed files enter progress.
        write(args.output/'acquisition-progress.json',dict(files=result,status='partial',
              source_snapshot_sha256=args.sources_sha,runner_sha256=digest(Path(__file__)),
              training=False,private_media_read=False))
    write(args.output/'acquisition.json',dict(files=result,source_snapshot_sha256=args.sources_sha,
          runner_sha256=digest(Path(__file__)),training=False,private_media_read=False))


class FixedEncoder:
    def __init__(self):
        import flatbuffers
        import numpy as np
        from ai_edge_litert import schema_py_generated as schema
        from ai_edge_litert.interpreter import Interpreter
        self.path=ROOT/'outputs/MoaAndroid/app/src/main/assets/yamnet.tflite'
        if digest(self.path)!=YAMNET_SHA:
            raise ValueError('Existing encoder differs')
        data=self.path.read_bytes()
        graph=schema.ModelT.InitFromObj(schema.Model.GetRootAsModel(data,0))
        if len(graph.subgraphs)!=1 or list(graph.subgraphs[0].outputs)!=[120]:
            raise ValueError('Fixed single-output encoder differs')
        tensor=graph.subgraphs[0].tensors[115]
        if tensor.name!=b'tower0/network/layer28/reduce_mean' or list(tensor.shape)!=[1,1,1,1024]:
            raise ValueError('Fixed embedding tensor differs')
        graph.subgraphs[0].outputs=np.asarray([120,115],np.int32)
        builder=flatbuffers.Builder(0)
        builder.Finish(graph.Pack(builder),file_identifier=b'TFL3')
        self.memory=bytes(builder.Output())
        self.original=Interpreter(model_content=data,num_threads=4)
        self.exposed=Interpreter(model_content=self.memory,num_threads=4)
        for interpreter in (self.original,self.exposed):
            interpreter.resize_tensor_input(interpreter.get_input_details()[0]['index'],[15600],strict=False)
            interpreter.allocate_tensors()
        self.details=next(row for row in self.exposed.get_output_details() if row['index']==115)

    def extract(self,pcm):
        import numpy as np
        if not 1<=len(pcm)<=320000:
            raise ValueError('Expected actual 1 sample..20s PCM')
        cleaned=filter_actual_public_pcm(pcm)
        arrays=[]
        frames=[]
        for start in range(0,len(cleaned),15600):
            kept=min(15600,len(cleaned)-start)
            block=np.zeros(15600,np.float32)
            block[:kept]=cleaned[start:start+kept]
            for interpreter in (self.original,self.exposed):
                interpreter.set_tensor(interpreter.get_input_details()[0]['index'],block)
                interpreter.invoke()
            old,new=self.original.get_tensor(120),self.exposed.get_tensor(120)
            if (old.shape!=(1,521) or new.shape!=(1,521) or not np.isfinite(old).all()
                    or not np.isfinite(new).all() or np.max(np.abs(old-new))>1e-6):
                raise ValueError('Feature exposure changed original scores')
            arrays.append(dequantize_embedding(self.exposed.get_tensor(115),self.details).reshape(1024))
            frames.append(dict(start_sample=start,real_samples=kept,padded_samples=15600-kept,
                               rms=float(np.sqrt(np.mean(block[:kept].astype(np.float64)**2))),
                               score_max_absolute_error=float(np.max(np.abs(old-new)))))
        if digest(self.path)!=YAMNET_SHA:
            raise ValueError('Encoder changed during extraction')
        return np.stack(arrays),frames


def public_features(args):
    import numpy as np
    sources=read_sources(args.sources,args.sources_sha)
    acquired=read_report(args.media/'acquisition.json')
    if acquired['source_snapshot_sha256']!=args.sources_sha or [r['id'] for r in acquired['files']]!=[r['id'] for r in sources]:
        raise ValueError('Acquisition differs')
    encoder=FixedEncoder()
    arrays={}
    cases=[]
    for row,item in zip(sources,acquired['files']):
        path=args.media/(row['id']+'.mp3')
        if path.is_symlink() or path.stat().st_size!=item['bytes'] or digest(path)!=item['sha256']:
            raise ValueError('Preview changed')
        decoded=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(path),'-map','0:a:0','-vn',
            '-t','20','-ac','1','-ar','16000','-f','f32le','pipe:1'],capture_output=True,timeout=20)
        if decoded.returncode or not 4<=len(decoded.stdout)<=320000*4 or len(decoded.stdout)%4:
            raise ValueError('Public preview decode failed/over cap')
        pcm=np.frombuffer(decoded.stdout,dtype='<f4').copy()
        if not np.isfinite(pcm).all():
            raise ValueError('Nonfinite decoded preview')
        raw_sha=hashlib.sha256(pcm.tobytes()).hexdigest()
        peak=float(np.abs(pcm).max())
        clamps=int(np.count_nonzero(np.abs(pcm)>1))
        np.clip(pcm,-1,1,out=pcm)
        arrays[row['id']],frames=encoder.extract(pcm)
        if digest(path)!=item['sha256']:
            raise ValueError('Preview changed during decode')
        cases.append(dict(id=row['id'],role=row['role'],weak_class=row['provisionalclass'],
            sourcegroup=row['sourcegroup'],frames=frames,actual_pcm_samples=len(pcm),
            raw_pcm_sha256=raw_sha,raw_peak=peak,clamped_samples=clamps,
            input_pcm_sha256=hashlib.sha256(pcm.tobytes()).hexdigest(),preview_sha256=item['sha256'],
            decode_policy='first20s_only_actual_length_preserved',audio_review_completed=False))
    np.savez_compressed(args.output/'features-public.npz',**arrays)
    write(args.output/'features.json',dict(cases=cases,features_sha256=digest(args.output/'features-public.npz'),
        source_snapshot_sha256=args.sources_sha,model_sha256=YAMNET_SHA,quality_validated=False,
        private_media_read=False,training=False,memory_graph_bytes=len(encoder.memory)))


def normalize(vector):
    import numpy as np
    vector=np.asarray(vector,dtype=np.float64)
    if vector.shape!=(1024,) or not np.isfinite(vector).all():
        raise ValueError('Expected finite 1024-vector')
    norm=float(np.linalg.norm(vector))
    if norm<1e-12:
        raise ValueError('Empty/zero embedding')
    return vector/norm


def pooled(features,frames):
    import numpy as np
    if (features.shape!=(len(frames),1024) or not np.isfinite(features).all()
            or not 1<=len(frames)<=21):
        raise ValueError('Feature/window shape invalid')
    eligible=[i for i,row in enumerate(frames) if row['rms']>=.003]
    if not eligible:
        raise ValueError('Quiet-only clip cannot be a training/pooling example')
    weights=np.asarray([frames[i]['real_samples'] for i in eligible],np.float64)
    if np.any(weights<=0) or np.any(weights>15600) or not np.isfinite(weights).all():
        raise ValueError('Invalid real sample weights')
    return normalize(np.average(features[eligible],axis=0,weights=weights))


def source_weights(cases):
    import numpy as np
    if not cases or any(c['role']!='train' for c in cases):
        raise ValueError('Validation/private data prohibited in fit')
    result=[]
    for case in cases:
        label=case['weak_class']
        class_sources={c['sourcegroup'] for c in cases if c['weak_class']==label}
        per_source=sum(c['sourcegroup']==case['sourcegroup'] and c['weak_class']==label for c in cases)
        result.append(1/(3*len(class_sources)*per_source))
    output=np.asarray(result,np.float64)
    if abs(float(output.sum())-1)>1e-10 or {c['weak_class'] for c in cases}!=set(CLASSES):
        raise ValueError('Expected all three weak classes')
    return output


def softmax(logits):
    import numpy as np
    if not np.isfinite(logits).all() or logits.shape[-1]!=3:
        raise ValueError('Nonfinite/invalid head logits')
    exp=np.exp(logits-np.max(logits,axis=-1,keepdims=True))
    return exp/exp.sum(axis=-1,keepdims=True)


def train_linear(x,labels,weights,penalty=.01):
    import numpy as np
    if (x.shape!=(len(labels),1024) or not np.isfinite(x).all()
            or penalty not in (.0001,.001,.01)):
        raise ValueError('Invalid train features')
    y=np.eye(3,dtype=np.float64)[labels]
    w=np.zeros((1024,3),np.float64)
    b=np.zeros(3,np.float64)
    mw=vw=np.zeros_like(w)
    mb=vb=np.zeros_like(b)
    for step in range(1,301):
        probs=softmax(x@w+b)
        grad=(probs-y)*weights[:,None]
        gw=x.T@grad+penalty*w
        gb=grad.sum(axis=0)
        mw=.9*mw+.1*gw
        vw=.999*vw+.001*gw**2
        mb=.9*mb+.1*gb
        vb=.999*vb+.001*gb**2
        w-=.03*(mw/(1-.9**step))/(np.sqrt(vw/(1-.999**step))+1e-8)
        b-=.03*(mb/(1-.9**step))/(np.sqrt(vb/(1-.999**step))+1e-8)
    if not np.isfinite(w).all() or not np.isfinite(b).all():
        raise ValueError('Nonfinite fitted head')
    return w,b


def supports(probs):
    import numpy as np
    values=np.sort(probs)
    return bool(np.argmax(probs)==0 and probs[0]>=.8 and values[-1]-values[-2]>=.4)


def fit(args):
    import numpy as np
    sources=read_sources(args.sources,args.sources_sha)
    metadata=read_report(args.features/'features.json')
    path=args.features/'features-public.npz'
    if metadata['source_snapshot_sha256']!=args.sources_sha or digest(path)!=metadata['features_sha256']:
        raise ValueError('Features provenance differs')
    cases=metadata['cases']
    if [c['id'] for c in cases]!=[r['id'] for r in sources]:
        raise ValueError('Feature cases differ')
    for c,s in zip(cases,sources):
        if (c['role'],c['weak_class'],c['sourcegroup'])!=(s['role'],s['provisionalclass'],s['sourcegroup']):
            raise ValueError('Feature role/source differs')
    with np.load(path,allow_pickle=False) as data:
        # Build train inputs before loading any validation feature into memory.
        train=[c for c in cases if c['role']=='train']
        x=np.stack([pooled(data[c['id']],c['frames']) for c in train])
        labels=np.asarray([CLASSES.index(c['weak_class']) for c in train])
        weights=source_weights(train)
        w,b=train_linear(x,labels,weights)
        np.savez_compressed(args.output/'head-weak-public.npz',weights=w.astype(np.float32),bias=b.astype(np.float32))
        rows=[]
        for c in cases:
            features=data[c['id']]
            vector=pooled(features,c['frames'])
            probs=softmax(vector@w+b)
            fp32=softmax(vector.astype(np.float32)@w.astype(np.float32)+b.astype(np.float32))
            if np.max(np.abs(probs-fp32))>5e-5:
                raise ValueError('Float32 head parity differs')
            predictions=[]
            for feature,frame in zip(features,c['frames']):
                if frame['rms']<.003:
                    predictions.append(dict(start_sample=frame['start_sample'],real_samples=frame['real_samples'],quiet=True,support=False))
                    continue
                probability=softmax(normalize(feature)@w+b)
                predictions.append(dict(start_sample=frame['start_sample'],real_samples=frame['real_samples'],
                    quiet=False,support=supports(probability),probabilities=probability.tolist()))
            rows.append(dict(id=c['id'],role=c['role'],weak_class=c['weak_class'],sourcegroup=c['sourcegroup'],
                predicted=CLASSES[int(np.argmax(probs))],probabilities=probs.tolist(),infant_support=supports(probs),
                float32_max_absolute_error=float(np.max(np.abs(probs-fp32))),windows=predictions))
    confusion={}
    for role in ('train','validation'):
        matrix=np.zeros((3,3),int)
        for row in rows:
            if row['role']==role:
                matrix[CLASSES.index(row['weak_class']),CLASSES.index(row['predicted'])]+=1
        confusion[role]=matrix.tolist()
    write(args.output/'fit.json',dict(cases=rows,confusion_weak_caption_only=confusion,
        classes=CLASSES,train_ids=[c['id'] for c in train],train_cases=len(train),validation_cases=len(cases)-len(train),
        parameters=3075,fp32_parameter_bytes=12300,head_sha256=digest(args.output/'head-weak-public.npz'),
        features_report_sha256=digest(args.features/'features.json'),source_snapshot_sha256=args.sources_sha,
        configuration=dict(steps=300,lr=.03,l2=.01,initialization='zero',normalization='clip_L2_only',
            class_source_clip_balanced=True,infant_threshold=.8,margin=.4),
        strong_labels_approved=False,private_training=False,calibrated_probability=False,
        quality_validated=False,android_validated=False,product_changed=False))


def evaluate(args):
    import numpy as np
    from efficientat_probe import load_corpus
    metadata=read_report(args.head/'fit.json')
    path=args.head/'head-weak-public.npz'
    if digest(path)!=metadata['head_sha256'] or metadata['configuration']['steps']!=300:
        raise ValueError('Fixed weak head differs')
    with np.load(path,allow_pickle=False) as arrays:
        w,b=arrays['weights'],arrays['bias']
    if w.shape!=(1024,3) or b.shape!=(3,) or not np.isfinite(w).all() or not np.isfinite(b).all():
        raise ValueError('Invalid head shape/values')
    entries=load_corpus(args.corpus)
    encoder=FixedEncoder()
    cases=[]
    for row in entries:
        pcm_path=args.corpus/(row['id']+'.f32')
        features,frames=encoder.extract(np.fromfile(pcm_path,dtype='<f4'))
        predictions=[]
        for feature,frame in zip(features,frames):
            probability=softmax(normalize(feature)@w+b) if frame['rms']>=.003 else None
            predictions.append(dict(frame,quiet=probability is None,
                probabilities=None if probability is None else probability.tolist(),
                infant_support=False if probability is None else supports(probability)))
        # Temporal support is descriptive, never a source/need ground truth.
        support_samples=sum(f['real_samples'] for f in predictions if f['infant_support'])
        consecutive=any(a['infant_support'] and c['infant_support'] for a,c in zip(predictions,predictions[1:]))
        if digest(pcm_path)!=row['pcm_sha256']:
            raise ValueError('Evaluation PCM changed')
        cases.append(dict(id=row['id'],pcm_sha256=row['pcm_sha256'],expected=row.get('expected'),
            role=row.get('role'),duration_ms=row['duration_ms'],windows=predictions,
            support_seconds=support_samples/16000,consecutive_support=consecutive))
    write(args.output/'evaluation-local.json',dict(cases=cases,head_sha256=digest(path),
        corpus_manifest_sha256=digest(args.corpus/'manifest.json'),training=False,
        private_data_evaluation_only=True,quality_validated=False,android_validated=False,product_changed=False))


def write(path,value):
    path.write_text(json.dumps(value,indent=2),encoding='utf-8')


def worker(args):
    actual=runtime()
    if args.mode!='acquire':
        offline()
    {'acquire':acquire,'features':public_features,'fit':fit,'evaluate':evaluate}[args.mode](args)
    write(args.output/'completion.json',dict(status='completed',mode=args.mode,runtime=actual,
        runner_sha256=digest(Path(__file__)),model_sha256=YAMNET_SHA,product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('acquire','features','fit','evaluate'))
    parser.add_argument('--output',type=Path,required=True)
    for name in ('sources','reuse','media','ffmpeg','features','head','corpus'):
        parser.add_argument('--'+name,type=Path)
    parser.add_argument('--sources-sha')
    parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('output','sources','reuse','media','ffmpeg','features','head','corpus'):
        value=getattr(args,name)
        if value is not None:
            setattr(args,name,value.resolve())
    validate_output_path(args.output)
    requirements={'acquire':('sources','sources_sha'),'features':('sources','sources_sha','media','ffmpeg'),
                  'fit':('sources','sources_sha','features'),'evaluate':('head','corpus')}
    if any(getattr(args,name) is None for name in requirements[args.mode]):
        raise SystemExit('Missing required mode arguments')
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
    command=[sys.executable,'-B',str(Path(__file__).resolve()),args.mode,'--worker','--output',str(args.output)]
    for name in ('sources','sources_sha','reuse','media','ffmpeg','features','head','corpus'):
        value=getattr(args,name)
        if value is not None:
            command.extend(['--'+name.replace('_','-'),str(value)])
    result=run_bounded(command,cwd=ROOT,env=env,log_path=args.output/'run.log',
                       timeout_seconds=180,memory_limit_bytes=2*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Head screen failed; inspect run/process log')
    completion=read_report(args.output/'completion.json')
    if (completion['status']!='completed' or completion['mode']!=args.mode
            or completion['runtime']!=RUNTIME or completion['runner_sha256']!=digest(Path(__file__))):
        raise SystemExit('Head screen completion/provenance differs')
    print(json.dumps(dict(status='completed',mode=args.mode,product_changed=False)))


if __name__=='__main__':
    main()
