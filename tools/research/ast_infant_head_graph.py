"""Connect the frozen AST infant head without changing any original AST node."""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys

from ast_embedding_interface import MODEL,MODEL_SHA,EMBEDDING,RUNTIME,validate_inputs,compare
from ast_infant_head_probe import score
from infant_head_screen import ROOT,read_report,digest,offline,write
from infant_presence_probe import supported
from ondevice_ast_features import extract,synthetic_pcm
from ondevice_av_probe import validate_output_path,verify_model
from ondevice_audio_candidate import window_ranges

HEAD_SHA='81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510'
PREFIX='agattok_infant_presence_'
OUTPUT='infant_presence'


def frozen_head(root):
    import numpy as np
    path=root/'head-ast-presence-public.npz'
    metadata=read_report(root/'fit.json')
    if (path.is_symlink() or digest(path)!=HEAD_SHA or metadata['head_sha256']!=HEAD_SHA
            or metadata['model_sha256']!=MODEL_SHA or metadata['private_training']
            or metadata['configuration']['dimension']!=768 or metadata['configuration']['threshold']!=.8
            or metadata['configuration']['margin']!=.4):
        raise ValueError('Frozen graph head provenance differs')
    with np.load(path,allow_pickle=False) as data: w,b=data['weights'],data['bias']
    if (w.shape!=(768,) or b.shape!=() or w.dtype!=np.float32 or b.dtype!=np.float32
            or not np.isfinite(w).all() or not np.isfinite(b)):
        raise ValueError('Invalid frozen FP32 graph head')
    return w,b


def graph_bytes(w,b):
    import numpy as np
    import onnx
    from onnx import helper,numpy_helper,TensorProto
    verify_model(MODEL,dict(size_bytes=91134123,sha256=MODEL_SHA))
    graph=onnx.load(str(MODEL),load_external_data=False)
    if (len(graph.graph.node)!=852 or [v.name for v in graph.graph.output]!=['logits']
            or any(t.external_data for t in graph.graph.initializer)
            or not any(EMBEDDING in node.output for node in graph.graph.node)
            or next(o.version for o in graph.opset_import if o.domain=='')<13):
        raise ValueError('Fixed AST graph differs')
    names={v for node in graph.graph.node for v in (*node.input,*node.output)}
    if OUTPUT in names or any(n.startswith(PREFIX) for n in names):
        raise ValueError('AST head graph names collide')
    initializers={
        'weights':w.reshape(768,1),'bias':np.asarray(b).reshape(1,1),
        'axes':np.asarray([-1],np.int64),'floor':np.asarray([[1e-12]],np.float32)}
    for name,value in initializers.items():
        graph.graph.initializer.append(numpy_helper.from_array(value,PREFIX+name))
    def node(kind,inputs,out,**kwargs):
        graph.graph.node.append(helper.make_node(kind,inputs,[out],name=PREFIX+kind,**kwargs))
    node('Mul',[EMBEDDING,EMBEDDING],PREFIX+'squared')
    node('ReduceSum',[PREFIX+'squared',PREFIX+'axes'],PREFIX+'sum',keepdims=1)
    node('Sqrt',[PREFIX+'sum'],PREFIX+'norm')
    node('Max',[PREFIX+'norm',PREFIX+'floor'],PREFIX+'denominator')
    node('Div',[EMBEDDING,PREFIX+'denominator'],PREFIX+'normalized')
    node('MatMul',[PREFIX+'normalized',PREFIX+'weights'],PREFIX+'linear')
    node('Add',[PREFIX+'linear',PREFIX+'bias'],PREFIX+'logit')
    node('Sigmoid',[PREFIX+'logit'],OUTPUT)
    graph.graph.output.append(helper.make_tensor_value_info(EMBEDDING,TensorProto.FLOAT,[1,768]))
    graph.graph.output.append(helper.make_tensor_value_info(OUTPUT,TensorProto.FLOAT,[1,1]))
    onnx.checker.check_model(graph)
    return graph.SerializeToString()


def worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    from efficientat_probe import load_corpus
    actual=dict(python=platform.python_version(),numpy=np.__version__,onnx=onnx.__version__,ort=ort.__version__)
    if actual!=RUNTIME: raise ValueError('Frozen AST graph runtime differs')
    files=validate_inputs(args); w,b=frozen_head(args.head); memory=graph_bytes(w,b)
    options=ort.SessionOptions(); options.intra_op_num_threads=4; options.inter_op_num_threads=1
    options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL; options.enable_mem_pattern=False
    baseline=ort.InferenceSession(str(MODEL),sess_options=options,providers=['CPUExecutionProvider'])
    candidate=ort.InferenceSession(memory,sess_options=options,providers=['CPUExecutionProvider'])
    if [o.name for o in candidate.get_outputs()]!=['logits',EMBEDDING,OUTPUT]:
        raise ValueError('Frozen AST head graph outputs differ')
    inputs=[('synthetic_silence1',synthetic_pcm(1000,'silence'),False),
        ('synthetic_tone10',synthetic_pcm(10000,'tone'),False),
        ('synthetic_sweep2',synthetic_pcm(2000,'sweep'),False)]
    for identity,path,sha in files:
        decoded=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(path),'-map','0:a:0',
            '-vn','-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'],capture_output=True,timeout=20)
        if decoded.returncode or not 32000<=len(decoded.stdout)<=320000 or len(decoded.stdout)%2 or digest(path)!=sha:
            raise ValueError('Frozen AST head graph public decode failed')
        inputs.append((identity,np.frombuffer(decoded.stdout,dtype='<i2').astype(np.float32)/32768,False))
    entries=load_corpus(args.corpus)
    if [e['id'] for e in entries]!=['clip_b_0_base','clip_b_1_base','clip_c_0_base']:
        raise ValueError('Fixed retained3 graph inputs differ')
    for entry in entries:
        inputs.append((entry['id'],np.fromfile(args.corpus/(entry['id']+'.f32'),dtype='<f4'),True))
    inputs.append(('synthetic_tone20',synthetic_pcm(20000,'tone'),False))
    cases=[]
    for identity,pcm,private in inputs:
        rows=[]
        for start,end in window_ranges(len(pcm)):
            features=extract(pcm[start:end])[None]
            old=baseline.run(['logits'],{'input_values':features})[0]
            new,embedding,presence=candidate.run(None,{'input_values':features})
            logit_error=compare(old,new)
            if (embedding.shape!=(1,768) or embedding.dtype!=np.float32 or not np.isfinite(embedding).all()
                    or presence.shape!=(1,1) or presence.dtype!=np.float32 or not np.isfinite(presence).all()):
                raise ValueError('Frozen AST head graph tensor invalid')
            reference=score(embedding[0],w,b); value=float(presence[0,0]); error=abs(value-reference['infant_score'])
            if error>5e-5 or supported(value)!=reference['infant_support']:
                raise ValueError('Frozen AST head graph score/decision parity failed')
            rows.append(dict(start_sample=start,end_sample=end,exact_527_logits_equal=True,
                max_absolute_logit_error=logit_error,infant_score=value,numpy_score=reference['infant_score'],
                max_absolute_head_error=error,infant_support=supported(value)))
        cases.append(dict(id=identity,actual_samples=len(pcm),private_evaluation_only=private,spans=rows))
    # Recheck evaluation source after native inference; it never supplied fitting data.
    load_corpus(args.corpus)
    if digest(MODEL)!=MODEL_SHA or digest(args.head/'head-ast-presence-public.npz')!=HEAD_SHA:
        raise ValueError('Frozen graph sources changed')
    candidate_sha=hashlib.sha256(memory).hexdigest()
    if args.export:
        with (args.output/'ast-infant-presence-candidate.onnx').open('xb') as stream: stream.write(memory)
        if digest(args.output/'ast-infant-presence-candidate.onnx')!=candidate_sha:
            raise ValueError('Candidate export hash differs')
    write(args.output/'graph-interface.json',dict(cases=cases,model_sha256=MODEL_SHA,head_sha256=HEAD_SHA,
        candidate_sha256=candidate_sha,candidate_bytes=len(memory),additional_bytes=len(memory)-91134123,
        exported_candidate=args.export,training=False,actual_infant_event_timing_validated=False,
        quality_validated=False,android_validated=False,product_changed=False))
    write(args.output/'completion.json',dict(status='completed',runtime=actual,
        runner_sha256=digest(Path(__file__)),graph_source_sha256=digest(ROOT/'tools/research/ast_infant_head_probe.py'),
        interface_source_sha256=digest(ROOT/'tools/research/ast_embedding_interface.py'),
        frontend_sha256=digest(ROOT/'tools/research/ondevice_ast_features.py'),product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('sources','media','ffmpeg','head','corpus','output'): parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--sources-sha',required=True); parser.add_argument('--export',action='store_true')
    parser.add_argument('--worker',action='store_true'); args=parser.parse_args()
    for name in ('sources','media','ffmpeg','head','corpus','output'): setattr(args,name,getattr(args,name).resolve())
    validate_output_path(args.output); validate_inputs(args); frozen_head(args.head)
    if args.worker: worker(args); return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'); env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),'--worker']
    for name in ('sources','sources_sha','media','ffmpeg','head','corpus','output'):
        argv+=['--'+name.replace('_','-'),str(getattr(args,name))]
    if args.export: argv.append('--export')
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=120,memory_limit_bytes=3*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Frozen AST head graph probe failed')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['runtime']!=RUNTIME or done['runner_sha256']!=digest(Path(__file__))
            or done['graph_source_sha256']!=digest(ROOT/'tools/research/ast_infant_head_probe.py')
            or done['interface_source_sha256']!=digest(ROOT/'tools/research/ast_embedding_interface.py')
            or done['frontend_sha256']!=digest(ROOT/'tools/research/ondevice_ast_features.py')):
        raise SystemExit('Frozen AST head graph completion provenance differs')
    print(json.dumps(dict(status='completed',product_changed=False)))


if __name__=='__main__': main()
