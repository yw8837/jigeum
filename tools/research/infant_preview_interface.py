"""Acquire three permitted public previews; inspect existing YAMNet features locally."""
import argparse
from dataclasses import asdict
import hashlib
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import urllib.request

from ondevice_av_probe import validate_output_path

AUDIT_SHA='592099e29c0f5e15289f8d31df938ca696a06c0031cd12713fd4edbd8078c9c6'
YAMNET_SHA='4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf'
IDS=('377237','113220','18274')
URLS={
    '377237':'https://cdn.freesound.org/previews/377/377237_5491823-lq.mp3',
    '113220':'https://cdn.freesound.org/previews/113/113220_4948-lq.mp3',
    '18274':'https://cdn.freesound.org/previews/18/18274_7037-lq.mp3'}
LICENSES={'http://creativecommons.org/publicdomain/zero/1.0/','http://creativecommons.org/licenses/by/3.0/'}
ROOT=Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def selected_sources(path):
    if path.stat().st_size!=445428 or digest(path)!=AUDIT_SHA:
        raise ValueError('Source audit differs from fixed metadata')
    source=json.loads(path.read_text('utf-8'))
    rows=source['eligible_public_metadata_index']
    selected=[]
    for identity in IDS:
        matches=[row for row in rows if str(row['id'])==identity]
        if len(matches)!=1:
            raise ValueError('Missing/duplicate pinned source ID')
        row=matches[0]
        if (row['official_preview_url']!=URLS[identity] or row['audio_license_url'] not in LICENSES
                or row['approved_training_label'] is not None or row['audio_sha256'] is not None
                or row['local_audio_review_status']!='not_acquired_not_reviewed'):
            raise ValueError('Source license/provisional boundary differs')
        selected.append(row)
    return selected


def filter_actual_public_pcm(pcm):
    """Keep sub-second public clip statistics; zero pad only after the filter."""
    import numpy as np
    from vtc1_guarded_probe import cleanup
    if len(pcm)>=16000:
        return cleanup(pcm)
    if pcm.dtype!=np.float32 or pcm.ndim!=1 or not len(pcm) or not np.isfinite(pcm).all() or np.any(np.abs(pcm)>1):
        raise ValueError('Invalid sub-second public PCM')
    mean=float(np.mean(pcm,dtype=np.float64))
    omega=2*math.pi*80/16000
    alpha=math.sin(omega)/math.sqrt(2)
    a0=1+alpha
    b0=(1+math.cos(omega))/(2*a0)
    b1,a1,a2=-2*b0,-2*math.cos(omega)/a0,(1-alpha)/a0
    x1=x2=y1=y2=0.
    output=np.empty_like(pcm)
    for i,value in enumerate(pcm):
        x=float(value)-mean
        y=b0*x+b1*x1+b0*x2-a1*y1-a2*y2
        x2,x1,y2,y1=x1,x,y1,y
        output[i]=max(-1.,min(1.,y))
    return output


def dequantize_embedding(value,details):
    import numpy as np
    if (value.shape!=(1,1,1,1024) or value.dtype!=np.int8
            or details['quantization']!=(0.022328350692987442,-128)):
        raise ValueError('Fixed embedding quantization differs')
    scale,zero=details['quantization']
    output=(value.astype(np.float32)-np.float32(zero))*np.float32(scale)
    if not np.isfinite(output).all():
        raise ValueError('Nonfinite decoded embedding')
    return output


class FixedRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if newurl not in URLS.values():
            raise ValueError('Unexpected public preview redirect')
        return super().redirect_request(req,fp,code,msg,headers,newurl)


def acquire(args):
    sources=selected_sources(args.audit)
    args.output.mkdir(parents=True,exist_ok=False)
    opener=urllib.request.build_opener(FixedRedirects())
    rows=[]
    for row in sources:
        identity=str(row['id'])
        request=urllib.request.Request(URLS[identity],headers={'User-Agent':'Agattok-local-research/1.0'})
        with opener.open(request,timeout=20) as response:
            if response.status!=200 or response.url!=URLS[identity]:
                raise ValueError('Preview HTTP contract differs')
            content_type=response.headers.get_content_type()
            if content_type not in ('audio/mpeg','application/octet-stream'):
                raise ValueError('Expected public mp3, not a web page')
            data=response.read(2*1024**2+1)
            if not 1<=len(data)<=2*1024**2:
                raise ValueError('Public preview exceeds fixed cap')
        path=args.output/(identity+'.mp3')
        with path.open('xb') as file:
            file.write(data)
        rows.append(dict(id=identity,url=URLS[identity],bytes=len(data),sha256=digest(path),
            content_type=content_type,audio_license=row['audio_license_url'],attribution_url=row['attribution_url'],
            uploader=row['uploader'],source_id=row['source_id'],role=row['provisional_split_role'],
            caption_age_months=row['caption_age_months'],lossy_preview_not_fsd_pcm=True,
            approved_training_label=None,audio_review_completed=False))
    (args.output/'acquisition.json').write_text(json.dumps(dict(stage='public_preview_acquired_not_audio_review',
        source_audit_sha256=AUDIT_SHA,runner_sha256=digest(Path(__file__)),files=rows,
        private_media_read=False,training=False,product_changed=False),indent=2),encoding='utf-8')
    print(json.dumps(dict(public_files=len(rows),total_bytes=sum(row['bytes'] for row in rows),training=False)))


def features(args):
    import socket
    def denied(*unused,**unused_kw):
        raise RuntimeError('Network prohibited during local feature preparation')
    socket.socket.connect=denied
    socket.socket.connect_ex=denied
    socket.create_connection=denied
    import flatbuffers
    import numpy as np
    from ai_edge_litert import schema_py_generated as schema
    from ai_edge_litert.interpreter import Interpreter
    selected_sources(args.audit)
    snapshot=json.loads((args.media/'acquisition.json').read_text('utf-8'))
    if snapshot['source_audit_sha256']!=AUDIT_SHA or [row['id'] for row in snapshot['files']]!=list(IDS):
        raise ValueError('Preview acquisition differs')
    model=ROOT/'outputs/MoaAndroid/app/src/main/assets/yamnet.tflite'
    if digest(model)!=YAMNET_SHA:
        raise ValueError('Existing encoder differs')
    value=schema.Model.GetRootAsModel(model.read_bytes(),0)
    graph=schema.ModelT.InitFromObj(value)
    if len(graph.subgraphs)!=1 or list(graph.subgraphs[0].outputs)!=[120]:
        raise ValueError('Expected fixed single-output graph')
    tensor=graph.subgraphs[0].tensors[115]
    if tensor.name!=b'tower0/network/layer28/reduce_mean' or list(tensor.shape)!=[1,1,1,1024]:
        raise ValueError('Expected fixed embedding tensor')
    graph.subgraphs[0].outputs=np.asarray([120,115],dtype=np.int32)
    builder=flatbuffers.Builder(0)
    builder.Finish(graph.Pack(builder),file_identifier=b'TFL3')
    memory_graph=bytes(builder.Output())
    reference=Interpreter(model_content=model.read_bytes(),num_threads=4)
    exposed=Interpreter(model_content=memory_graph,num_threads=4)
    for interpreter in (reference,exposed):
        interpreter.resize_tensor_input(interpreter.get_input_details()[0]['index'],[15600],strict=False)
        interpreter.allocate_tensors()
    embedding_details=next(value for value in exposed.get_output_details() if value['index']==115)
    rows=[]
    arrays={}
    for item in snapshot['files']:
        path=args.media/(item['id']+'.mp3')
        if path.is_symlink() or path.stat().st_size!=item['bytes'] or digest(path)!=item['sha256']:
            raise ValueError('Preview changed')
        result=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(path),'-map','0:a:0','-vn',
            '-t','30','-ac','1','-ar','16000','-f','f32le','pipe:1'],capture_output=True,timeout=20)
        if result.returncode or not 4<=len(result.stdout)<=2*1024**2 or len(result.stdout)%4:
            raise ValueError('Public preview decode failed or exceeded cap')
        pcm=np.frombuffer(result.stdout,dtype='<f4').copy()
        if not np.isfinite(pcm).all():
            raise ValueError('Decoded public PCM invalid')
        raw_pcm_sha=hashlib.sha256(pcm.tobytes()).hexdigest()
        decoded_peak=float(np.abs(pcm).max())
        clamped_samples=int(np.count_nonzero(np.abs(pcm)>1))
        # Lossy MP3/resampler can overshoot nominal PCM full scale. Match the
        # bounded decoder range; record every clamp, never apply gain normalization.
        np.clip(pcm,-1.,1.,out=pcm)
        # Short public samples remain short in metadata; padding is only a model input.
        cleaned=filter_actual_public_pcm(pcm)
        embeddings=[]
        maximum_error=0.
        frame_rows=[]
        for start in range(0,len(cleaned),15600):
            block=np.zeros(15600,dtype=np.float32)
            kept=min(15600,len(cleaned)-start)
            block[:kept]=cleaned[start:start+kept]
            for interpreter in (reference,exposed):
                interpreter.set_tensor(interpreter.get_input_details()[0]['index'],block)
                interpreter.invoke()
            old=reference.get_tensor(120)
            new=exposed.get_tensor(120)
            embedding=dequantize_embedding(exposed.get_tensor(115),embedding_details)
            if (old.shape!=(1,521) or new.shape!=(1,521) or embedding.shape!=(1,1,1,1024)
                    or embedding.dtype!=np.float32 or not np.isfinite(embedding).all()
                    or not np.isfinite(old).all() or not np.isfinite(new).all()):
                raise ValueError('Exposed tensor contract invalid')
            error=float(np.abs(old-new).max())
            if error>1e-6:
                raise ValueError('Embedding exposure changed existing class scores')
            maximum_error=max(maximum_error,error)
            embeddings.append(embedding.reshape(1024))
            frame_rows.append(dict(start_sample=start,real_samples=kept,padded_samples=15600-kept))
        arrays[item['id']]=np.stack(embeddings)
        if digest(path)!=item['sha256']:
            raise ValueError('Preview changed during local feature preparation')
        rows.append(dict(id=item['id'],preview_sha256=item['sha256'],actual_pcm_samples=len(pcm),
            raw_decoded_pcm_sha256=raw_pcm_sha,raw_decoded_peak=decoded_peak,
            clamped_samples=clamped_samples,input_pcm_sha256=hashlib.sha256(pcm.tobytes()).hexdigest(),frames=frame_rows,
            embedding_shape=list(arrays[item['id']].shape),all_521_scores_max_absolute_error=maximum_error,
            audio_review_completed=False,approved_training_label=None))
    if digest(model)!=YAMNET_SHA:
        raise ValueError('Encoder changed')
    np.savez_compressed(args.output/'embeddings-public.npz',**arrays)
    report=dict(stage='public_preview_embedding_interface_validated_not_training',cases=rows,
        model_sha256=YAMNET_SHA,source_audit_sha256=AUDIT_SHA,runner_sha256=digest(Path(__file__)),
        runtime=dict(python=platform.python_version(),numpy=np.__version__,
            litert=importlib.metadata.version('ai-edge-litert'),flatbuffers=flatbuffers.__version__),
        embedding_quantization=dict(dtype='int8',scale=0.022328350692987442,zero_point=-128,
            decoded_dtype='float32'),
        memory_only_exposed_graph_bytes=len(memory_graph),memory_graph_sha256=hashlib.sha256(memory_graph).hexdigest(),
        external_graph_written=False,private_media_read=False,training=False,quality_validated=False,
        android_validated=False,product_changed=False)
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=('acquire','features'))
    for name in ('audit','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--media',type=Path)
    parser.add_argument('--ffmpeg',type=Path)
    parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('audit','output','media'):
        value=getattr(args,name)
        if value is not None:
            value=value.resolve()
            validate_output_path(value)
            setattr(args,name,value)
    if args.mode=='acquire':
        acquire(args)
        return
    if args.media is None or args.ffmpeg is None:
        parser.error('features requires --media and --ffmpeg')
    args.ffmpeg=args.ffmpeg.resolve(strict=True)
    if args.worker:
        features(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    command=[sys.executable,'-B',str(Path(__file__).resolve()),'features','--worker',
        *[value for name in ('audit','output','media','ffmpeg') for value in ('--'+name,str(getattr(args,name)))]]
    result=run_bounded(command,cwd=ROOT,env=env,log_path=args.output/'features.log',
        timeout_seconds=180,memory_limit_bytes=2*1024**3)
    (args.output/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Public embedding interface failed')
    print(json.dumps(dict(status='validated',public_sources=3,training=False,product_changed=False)))


if __name__=='__main__':
    main()
