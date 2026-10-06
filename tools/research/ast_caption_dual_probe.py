"""Two frozen correlated heads on retained public10; additive diagnostic, not product."""
import argparse
from dataclasses import asdict
import os
from pathlib import Path
import platform
import subprocess
import sys
from types import SimpleNamespace
import uuid

from ast_caption_wholebag_probe import (
    ROOT, RUNTIME, EXPERIMENT, verify_head, offline, read_report, digest, write,
    provenance as caption_provenance,
)
from ast_infant_head_graph import frozen_head, OUTPUT
from ast_embedding_interface import EMBEDDING
from ast_infant_head_probe import score, spans
from extra_babble_evaluation import sources, CANDIDATE_SHA
from public_infant_review_acquire import FFMPEG, verify_ffmpeg
from ondevice_ast_features import extract
from ondevice_av_probe import validate_output_path, verify_model

EVIDENCE_ROOT = ROOT / 'work/ast-caption-dual-20261007'
SOURCE_PATH = ROOT / 'work/infant-head-20261007/extra-babble-sources.json'
MEDIA_ROOT = ROOT / 'work/infant-head-20261007/extra-eval01'
ACQUISITION_SHA = 'aad52273f4fcd2036f03e9541acee2c7dc127ae0e814678b434e95a60bb7ad3a'
MODEL = ROOT / 'work/infant-head-20261007/ast-head-graph01/ast-infant-presence-candidate.onnx'
BASE_HEAD = ROOT / 'work/infant-head-20261007/ast-head35-01'
CAPTION_HEAD = ROOT / 'work/ast-caption-wholebag-20261007/fit01'
CAPTION_SHA = '4929cee8c7c3d11cc98c351a73148837f9c312250c50e7ef9a41a5420c3e4ad5'


def verify_inputs():
    rows = sources(SOURCE_PATH)
    path = MEDIA_ROOT / 'acquisition.json'
    if digest(path) != ACQUISITION_SHA:
        raise ValueError('Frozen public10 acquisition differs')
    report = read_report(path)
    if (len(report['files']) != 10 or report['training'] is not False
            or report['private_media_read'] is not False):
        raise ValueError('Public10 acquisition boundary differs')
    for row, record in zip(rows, report['files']):
        if any(record.get(k) != v for k, v in row.items()) or record['audio_review_completed'] is not False:
            raise ValueError('Public10 caption/role/rights differs')
        audio = MEDIA_ROOT / (row['id'] + '.mp3')
        if audio.is_symlink() or audio.stat().st_size != record['bytes'] or digest(audio) != record['sha256']:
            raise ValueError('Frozen public10 audio differs')
    verify_model(MODEL, dict(size_bytes=91138597,sha256=CANDIDATE_SHA))
    verify_head(SimpleNamespace(head=CAPTION_HEAD,head_sha=CAPTION_SHA))
    return report['files']


def head_pair(vector, base_w, base_b, caption_w, caption_b, rms):
    import numpy as np
    if not np.isfinite(rms) or not 0 <= rms <= 1:
        raise ValueError('Same-window RMS invalid')
    if rms < .003:
        return dict(base_score=None,caption_score=None,base_support=False,caption_support=False,
                    paired_support=False,quiet=True)
    base = score(vector,base_w,base_b)
    caption = score(vector,caption_w,caption_b)
    return dict(base_score=base['infant_score'],caption_score=caption['infant_score'],
        base_support=base['infant_support'],caption_support=caption['infant_support'],
        paired_support=base['infant_support'] or caption['infant_support'],quiet=False,
        base_float32_error=base['float32_absolute_error'],caption_float32_error=caption['float32_absolute_error'])


def provenance():
    return dict(caption_provenance(), **{name:digest(ROOT/'tools/research'/name) for name in
        ('ast_caption_dual_probe.py','ast_infant_head_graph.py','extra_babble_evaluation.py')})


def validate_artifact(output):
    from ast_caption_completion import case_ids, flags, frames
    report=read_report(output/'evaluation.json');expected=verify_inputs()
    rows=case_ids(report,[r['id'] for r in expected])
    flags(report,dict(base_head_sha256=digest(BASE_HEAD/'head-ast-presence-public.npz'),caption_head_sha256=CAPTION_SHA,
        model_sha256=CANDIDATE_SHA,acquisition_sha256=ACQUISITION_SHA,scope='ADDITIVE_BROAD_PRESENCE_DIAGNOSTIC',
        threshold=.8,margin=.4,correlated_heads=True,calibrated_probability=False,new_raw_files=0,
        training=False,product_authorization=False,quality_validated=False,android_validated=False,product_changed=False))
    for source,row in zip(expected,rows):
        if any(row.get(k)!=v or type(row.get(k)) is not type(v) for k,v in source.items()):
            raise ValueError('Dual source/review state differs')
        if row.get('event_ground_truth') is not False:raise ValueError('Caption-only event boundary differs')
        frames(row.get('spans'),row.get('actual_pcm_samples'),dual=True)
        for name in ('base','caption','paired'):
            if row.get(name+'_any') is not any(f[name+'_support'] for f in row['spans']):
                raise ValueError('Dual summary contradicts span supports')


def worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    actual=dict(python=platform.python_version(),numpy=np.__version__,onnx=onnx.__version__,ort=ort.__version__)
    if actual!=RUNTIME:raise ValueError('Dual head fixed runtime differs')
    records=verify_inputs();verify_ffmpeg(FFMPEG)
    bw,bb=frozen_head(BASE_HEAD)
    with np.load(CAPTION_HEAD/'head-ast-presence-public.npz',allow_pickle=False) as archive:
        cw,cb=archive['weights'],archive['bias']
    options=ort.SessionOptions();options.intra_op_num_threads=4;options.inter_op_num_threads=1
    options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL;options.enable_mem_pattern=False
    session=ort.InferenceSession(str(MODEL),sess_options=options,providers=['CPUExecutionProvider'])
    if [o.name for o in session.get_outputs()]!=['logits',EMBEDDING,OUTPUT]:
        raise ValueError('Dual original graph outputs differ')
    cases=[]
    for record in records:
        path=MEDIA_ROOT/(record['id']+'.mp3')
        result=subprocess.run([str(FFMPEG),'-v','error','-protocol_whitelist','file,pipe',
            '-format_whitelist','mp3','-i',str(path),'-map','0:a:0','-vn','-t','20',
            '-ac','1','-ar','16000','-f','s16le','pipe:1'],capture_output=True,timeout=20)
        if result.returncode or not 2<=len(result.stdout)<=640000 or len(result.stdout)%2:
            raise ValueError('Public10 decode failed/oversized')
        pcm=np.frombuffer(result.stdout,dtype='<i2').astype(np.float32)/32768
        frames=[]
        for block,frame in spans(pcm):
            if frame['rms']<.003:
                paired=head_pair(None,bw,bb,cw,cb,frame['rms'])
            else:
                logits,embedding,presence=session.run(None,{'input_values':extract(block)[None]})
                if (logits.shape!=(1,527) or embedding.shape!=(1,768) or presence.shape!=(1,1)
                        or any(a.dtype!=np.float32 or not np.isfinite(a).all() for a in (logits,embedding,presence))):
                    raise ValueError('Dual graph tensor invalid')
                paired=head_pair(embedding[0],bw,bb,cw,cb,frame['rms'])
                error=abs(float(presence[0,0])-paired['base_score'])
                if error>5e-5:raise ValueError('Original graph/base-head parity differs')
                paired['original_graph_base_error']=error
            frames.append(dict(frame,**paired))
        if digest(path)!=record['sha256']:raise ValueError('Public10 audio changed')
        cases.append(dict(record,actual_pcm_samples=len(pcm),spans=frames,
            base_any=any(f['base_support'] for f in frames),caption_any=any(f['caption_support'] for f in frames),
            paired_any=any(f['paired_support'] for f in frames),event_ground_truth=False))
    verify_inputs()
    write(args.output/'evaluation.json',dict(cases=cases,base_head_sha256=digest(BASE_HEAD/'head-ast-presence-public.npz'),
        caption_head_sha256=CAPTION_SHA,model_sha256=CANDIDATE_SHA,acquisition_sha256=ACQUISITION_SHA,
        scope='ADDITIVE_BROAD_PRESENCE_DIAGNOSTIC',threshold=.8,margin=.4,correlated_heads=True,
        calibrated_probability=False,new_raw_files=0,training=False,product_authorization=False,
        quality_validated=False,android_validated=False,product_changed=False))
    write(args.output/'completion.json',dict(status='completed',runtime=actual,source_code_sha256=provenance(),
        evaluation_sha256=digest(args.output/'evaluation.json'),product_changed=False))


def output_path(path):
    validate_output_path(path)
    if path==EVIDENCE_ROOT.resolve() or not path.is_relative_to(EVIDENCE_ROOT.resolve()):
        raise ValueError('Dual output must stay inside dedicated evidence root')


def claim(args):
    token=os.environ.get('AGATTOK_DUAL_WORKER_TOKEN')
    if not token or not (args.output/'launch.json').is_file():
        raise ValueError('Direct dual worker invocation prohibited')
    launch=read_report(args.output/'launch.json')
    if launch['token']!=token or launch['output']!=str(args.output) or launch['source_code_sha256']!=provenance():
        raise ValueError('Dual worker launch differs')
    if any((args.output/name).exists() for name in ('evaluation.json','completion.json','process.json')):
        raise ValueError('Existing dual terminal output must be preserved')
    with (args.output/'worker-claim.json').open('x',encoding='utf-8') as stream:stream.write(str(os.getpid()))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--worker',action='store_true')
    args=parser.parse_args();args.output=args.output.resolve();output_path(args.output)
    if args.worker:claim(args);worker(args);return
    verify_inputs();pins=provenance();args.output.mkdir(parents=True,exist_ok=False)
    launch=dict(token=uuid.uuid4().hex,output=str(args.output),source_code_sha256=pins)
    write(args.output/'launch.json',launch)
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1',AGATTOK_DUAL_WORKER_TOKEN=launch['token'])
    env.pop('OMX_ROOT',None);env.pop('OMX_STATE_ROOT',None)
    from bounded_process import run_bounded
    result=run_bounded([sys.executable,'-B',str(Path(__file__).resolve()),'--worker','--output',str(args.output)],
        cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=180,memory_limit_bytes=3*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes or result.output_truncated:
        raise SystemExit('Dual head evaluation failed')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['runtime']!=RUNTIME or done['source_code_sha256']!=pins
            or provenance()!=pins or done['evaluation_sha256']!=digest(args.output/'evaluation.json')
            or done.get('product_changed') is not False):
        raise SystemExit('Dual completion provenance differs')
    validate_artifact(args.output)
    print('Dual public evaluation completed; product_authorization=false')


if __name__=='__main__':main()
