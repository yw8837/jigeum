"""Evaluate frozen AST head on exact permissive public sources; never train them."""
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
import urllib.parse
import urllib.request

from ast_embedding_interface import EMBEDDING,RUNTIME
from ast_infant_head_graph import HEAD_SHA,OUTPUT,frozen_head
from ast_infant_head_probe import spans,score
from infant_head_screen import ROOT,NoRedirects,allowed_license,digest,read_report,verified_reuse,write,offline
from infant_presence_probe import supported
from ondevice_ast_features import extract
from ondevice_av_probe import validate_output_path,verify_model

SOURCES_SHA='a7658a7a85688ed360377d5607101b31d1d1a115be96e7c58abc9aebbd3d045b'
IDS=('416677','416681','416635','416702','259609','254454','254455','254465','254471')
CANDIDATE_SHA='4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a'


def verify_final_pins(source_path, model_path, head_directory):
    actual = dict(source=digest(source_path), model=digest(model_path),
                  head=digest(head_directory/'head-ast-presence-public.npz'))
    if actual != dict(source=SOURCES_SHA, model=CANDIDATE_SHA, head=HEAD_SHA):
        raise ValueError('Extra frozen evaluation metadata/model/head changed')
    return actual


def sources(path):
    if path.is_symlink() or not 1<=path.stat().st_size<=2*1024**2 or digest(path)!=SOURCES_SHA:
        raise ValueError('Fixed extra source metadata differs')
    raw=read_report(path)['files']
    if [r['id'] for r in raw]!=list(IDS) or len({r['sourcegroup'] for r in raw})!=3:
        raise ValueError('Expected exact9 sources in3 uploader groups')
    rows=[]
    for row in raw:
        if not row['training_blocked'] or row['approved_training_label'] is not None:
            raise ValueError('Extra weak sources must remain training blocked')
        rows.append(dict(id=row['id'],sourcegroup=row['sourcegroup'],preview_url=row['preview_url'],
            current_license_url=row['current_license_url'],page_url=row['url'],training_blocked=True,
            approved_training_label=None,role='evaluation_only',caption_category=
                ('grunt' if row['id']=='416635' else 'laugh' if row['id']=='416702' else
                 'dada' if row['id'] in ('254465','254471') else 'babble_gibberish'),
            speaker_claim_strength='tags_only' if 'wjoojoo' in row['sourcegroup'] else 'caption_toddler_baby',
            age_verified=False,naturalness='unknown',mixed='explicit_crinkle' if row['id']=='259609' else 'unknown'))
    rows.append(dict(id='128479',sourcegroup='FSD50K:eval:gipsyGA',
        preview_url='https://cdn.freesound.org/previews/128/128479_2331961-lq.mp3',
        current_license_url='https://creativecommons.org/licenses/by/4.0/',
        page_url='https://freesound.org/people/gipsyGA/sounds/128479/',training_blocked=True,
        approved_training_label=None,role='evaluation_only',caption_category='cartoon_grandpa_gibberish',
        speaker_claim_strength='uploader_self_voice_cartoon_grandpa',age_verified=False,
        naturalness='unknown',mixed='unknown'))
    for row in rows:
        url=urllib.parse.urlsplit(row['preview_url'])
        pattern='/previews/'+str(int(row['id'])//1000)+'/'+row['id']+r'_[0-9]+-lq\.mp3'
        if (url.scheme!='https' or url.netloc!='cdn.freesound.org' or not re.fullmatch(pattern,url.path)
                or url.query or url.fragment or not allowed_license(row['current_license_url'])):
            raise ValueError('Unpermitted exact public preview/license')
    return rows


def acquire(args,rows, *, source_sha=SOURCES_SHA, max_bytes=2*1024**2):
    if type(max_bytes) is not int or not 1 <= max_bytes <= 3*1024**2:
        raise ValueError('Public preview byte cap must be an integer within 3MiB')
    opener=urllib.request.build_opener(NoRedirects()); records=[]
    for row in rows:
        data=verified_reuse(args.reuse,row,max_bytes=max_bytes); reused=data is not None
        if data is None:
            print(json.dumps(dict(acquiring_public_id=row['id'])),flush=True)
            request=urllib.request.Request(row['preview_url'],headers={'User-Agent':'Agattok-local-research/1.0'})
            with opener.open(request,timeout=20) as response:
                if (response.status!=200 or response.url!=row['preview_url']
                        or response.headers.get_content_type() not in ('audio/mpeg','application/octet-stream')):
                    raise ValueError('Exact public MP3 response differs')
                data=response.read(max_bytes+1)
        if not 1<=len(data)<=max_bytes: raise ValueError('Extra preview exceeds size cap')
        path=args.output/(row['id']+'.mp3')
        with path.open('xb') as stream: stream.write(data)
        records.append(dict(row,bytes=len(data),sha256=digest(path),reused_previous_preview=reused,
                            original_wav_flac=False,audio_review_completed=False))
        write(args.output/'acquisition-progress.json',dict(status='partial',files=records,source_sha256=source_sha,
            training=False,private_media_read=False))
    write(args.output/'acquisition.json',dict(files=records,source_sha256=source_sha,training=False,private_media_read=False))
    return records


def worker(args):
    # Only exact public CDN downloads precede the explicit offline inference boundary.
    rows=sources(args.sources); w,b=frozen_head(args.head)
    verify_model(args.model,dict(size_bytes=91138597,sha256=CANDIDATE_SHA))
    records=acquire(args,rows); offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual=dict(python=platform.python_version(),numpy=np.__version__,onnx=onnx.__version__,ort=ort.__version__)
    if actual!=RUNTIME: raise ValueError('Extra evaluation fixed runtime differs')
    options=ort.SessionOptions(); options.intra_op_num_threads=4; options.inter_op_num_threads=1
    options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL; options.enable_mem_pattern=False
    session=ort.InferenceSession(str(args.model),sess_options=options,providers=['CPUExecutionProvider'])
    if [o.name for o in session.get_outputs()]!=['logits',EMBEDDING,OUTPUT]:
        raise ValueError('Frozen extra model outputs differ')
    cases=[]
    for row in records:
        path=args.output/(row['id']+'.mp3')
        decoded=subprocess.run([str(args.ffmpeg),'-v','error','-protocol_whitelist','file,pipe',
            '-format_whitelist','mp3','-i',str(path),'-map','0:a:0','-vn','-t','20',
            '-ac','1','-ar','16000','-f','s16le','pipe:1'],capture_output=True,timeout=20)
        if (decoded.returncode or not 2<=len(decoded.stdout)<=640000 or len(decoded.stdout)%2
                or digest(path)!=row['sha256']): raise ValueError('Extra public decode failed/changed')
        pcm=np.frombuffer(decoded.stdout,dtype='<i2').astype(np.float32)/32768; frames=[]
        for block,frame in spans(pcm):
            if frame['rms']<.003:
                frames.append(dict(frame,quiet=True,infant_score=None,infant_support=False)); continue
            logits,embedding,presence=session.run(None,{'input_values':extract(block)[None]})
            if (logits.shape!=(1,527) or embedding.shape!=(1,768) or presence.shape!=(1,1)
                    or any(v.dtype!=np.float32 or not np.isfinite(v).all() for v in (logits,embedding,presence))):
                raise ValueError('Extra model output tensor differs')
            value=float(presence[0,0]); reference=score(embedding[0],w,b); error=abs(value-reference['infant_score'])
            if error>5e-5 or supported(value)!=reference['infant_support']:
                raise ValueError('Extra graph head parity failed')
            frames.append(dict(frame,quiet=False,infant_score=value,infant_support=supported(value),head_max_absolute_error=error))
        cases.append(dict(row,actual_samples=len(pcm),spans=frames,
            any_supported_span=any(f['infant_support'] for f in frames),
            quiet_only=all(f['quiet'] for f in frames),event_ground_truth=False))
    final_pins = verify_final_pins(args.sources, args.model, args.head)
    write(args.output/'evaluation.json',dict(requested_cases=10,decoded_cases=len(cases),cases=cases,
        source_sha256=final_pins['source'],head_sha256=final_pins['head'],model_sha256=final_pins['model'],runtime=actual,
        training=False,calibration=False,private_media_read=False,strong_ground_truth=False,
        quality_validated=False,android_validated=False,product_changed=False))
    files=('extra_babble_evaluation.py','ast_infant_head_graph.py','ast_infant_head_probe.py',
        'ondevice_ast_features.py','infant_head_screen.py','infant_presence_probe.py')
    write(args.output/'completion.json',dict(status='completed',runtime=actual,
        source_code_sha256={f:digest(ROOT/'tools/research'/f) for f in files},product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('sources','model','head','ffmpeg','output'): parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--reuse',type=Path); parser.add_argument('--worker',action='store_true'); args=parser.parse_args()
    for name in ('sources','model','head','ffmpeg','output','reuse'):
        if getattr(args,name) is not None: setattr(args,name,getattr(args,name).resolve())
    validate_output_path(args.output); sources(args.sources); frozen_head(args.head)
    verify_model(args.model,dict(size_bytes=91138597,sha256=CANDIDATE_SHA))
    if args.worker: worker(args); return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'); env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),'--worker']
    for name in ('sources','model','head','ffmpeg','output','reuse'):
        if getattr(args,name) is not None: argv+=['--'+name,str(getattr(args,name))]
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,memory_limit_bytes=3*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Extra frozen evaluation failed; inspect partial acquisition before reuse')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['runtime']!=RUNTIME
            or any(digest(ROOT/'tools/research'/name)!=sha for name,sha in done['source_code_sha256'].items())):
        raise SystemExit('Extra evaluation completion provenance differs')
    print(json.dumps(dict(status='completed',training=False,product_changed=False)))


if __name__=='__main__': main()
