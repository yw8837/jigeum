"""Expose existing AST representation in memory; public-only numeric interface probe."""
import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time

from infant_head_screen import ROOT,digest,read_sources,read_report,offline,write
from ondevice_ast_features import extract,synthetic_pcm
from ondevice_av_probe import validate_output_path,verify_model

MODEL=ROOT/'outputs/MoaAndroid/app/src/astProbe/assets/ast-dynamic-int8.onnx'
MODEL_SHA='3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
SOURCE_SHA='27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9'
EMBEDDING='/source/classifier/layernorm/LayerNormalization_output_0'
PUBLIC_IDS=('113221','184432','256684')
RUNTIME=dict(python='3.13.14',numpy='2.4.6',onnx='1.20.1',ort='1.24.4')


def validate_inputs(args):
    if args.sources_sha!=SOURCE_SHA:
        raise ValueError('Fixed AST interface source SHA differs')
    sources=read_sources(args.sources,args.sources_sha)
    acquisition=read_report(args.media/'acquisition.json')
    if acquisition['source_snapshot_sha256']!=SOURCE_SHA or [r['id'] for r in acquisition['files']]!=[r['id'] for r in sources]:
        raise ValueError('AST public acquisition differs')
    files=[]
    for identity in PUBLIC_IDS:
        item=next(r for r in acquisition['files'] if r['id']==identity)
        path=args.media/(identity+'.mp3')
        if path.is_symlink() or path.stat().st_size!=item['bytes'] or digest(path)!=item['sha256']:
            raise ValueError('AST interface preview differs')
        files.append((identity,path,item['sha256']))
    return files


def compare(original,modified):
    import numpy as np
    if (original.shape!=(1,527) or modified.shape!=(1,527)
            or original.dtype!=np.float32 or modified.dtype!=np.float32
            or not np.isfinite(original).all() or not np.isfinite(modified).all()):
        raise ValueError('Invalid AST interface logits')
    error=float(np.max(np.abs(original-modified)))
    if not np.array_equal(original,modified):
        raise ValueError(f'AST interface exact logits parity failed; maxabs={error}')
    return error


class AstRepresentation:
    """Two CPU sessions for research parity; never a product cost measurement."""
    def __init__(self):
        import onnx
        import onnxruntime as ort
        verify_model(MODEL,dict(size_bytes=91134123,sha256=MODEL_SHA))
        graph=onnx.load(str(MODEL),load_external_data=False)
        if (len(graph.graph.node)!=852 or [v.name for v in graph.graph.output]!=['logits']
                or any(t.external_data for t in graph.graph.initializer)
                or not any(EMBEDDING in n.output for n in graph.graph.node)):
            raise ValueError('Fixed AST graph interface differs')
        graph.graph.output.append(onnx.helper.make_tensor_value_info(EMBEDDING,onnx.TensorProto.FLOAT,[1,768]))
        onnx.checker.check_model(graph)
        memory=graph.SerializeToString()
        self.memory_sha256=hashlib.sha256(memory).hexdigest()
        self.memory_bytes=len(memory)
        options=ort.SessionOptions()
        options.intra_op_num_threads=4; options.inter_op_num_threads=1
        options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL; options.enable_mem_pattern=False
        self.baseline=ort.InferenceSession(str(MODEL),sess_options=options,providers=['CPUExecutionProvider'])
        self.exposed=ort.InferenceSession(memory,sess_options=options,providers=['CPUExecutionProvider'])
        if (self.baseline.get_inputs()[0].shape!=[1,1024,128] or self.exposed.get_inputs()[0].shape!=[1,1024,128]
                or [v.name for v in self.baseline.get_outputs()]!=['logits']
                or [v.name for v in self.exposed.get_outputs()]!=['logits',EMBEDDING]):
            raise ValueError('Unexpected AST sessions')

    def infer(self,pcm):
        import numpy as np
        features=extract(pcm)[None]
        old=self.baseline.run(['logits'],{'input_values':features})[0]
        new,embedding=self.exposed.run(['logits',EMBEDDING],{'input_values':features})
        error=compare(old,new)
        if embedding.shape!=(1,768) or embedding.dtype!=np.float32 or not np.isfinite(embedding).all():
            raise ValueError('Invalid AST representation')
        return embedding[0],error


def worker(args):
    offline()
    import numpy as np
    import onnx
    import onnxruntime as ort
    import psutil
    actual=dict(python=platform.python_version(),numpy=np.__version__,onnx=onnx.__version__,ort=ort.__version__)
    if actual!=RUNTIME:
        raise ValueError('Fixed AST interface runtime differs')
    files=validate_inputs(args)
    verify_model(MODEL,dict(size_bytes=91134123,sha256=MODEL_SHA))
    graph=onnx.load(str(MODEL),load_external_data=False)
    if (len(graph.graph.node)!=852 or [v.name for v in graph.graph.output]!=['logits']
            or any(t.external_data for t in graph.graph.initializer)):
        raise ValueError('Fixed AST graph interface differs')
    if not any(node.output and EMBEDDING in node.output for node in graph.graph.node):
        raise ValueError('AST embedding tensor missing')
    graph.graph.output.append(onnx.helper.make_tensor_value_info(EMBEDDING,onnx.TensorProto.FLOAT,[1,768]))
    onnx.checker.check_model(graph)
    memory=graph.SerializeToString()
    options=ort.SessionOptions()
    options.intra_op_num_threads=4; options.inter_op_num_threads=1
    options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL; options.enable_mem_pattern=False
    baseline=ort.InferenceSession(str(MODEL),sess_options=options,providers=['CPUExecutionProvider'])
    exposed=ort.InferenceSession(memory,sess_options=options,providers=['CPUExecutionProvider'])
    if (baseline.get_inputs()[0].shape!=[1,1024,128] or exposed.get_inputs()[0].shape!=[1,1024,128]
            or [v.name for v in baseline.get_outputs()]!=['logits']
            or [v.name for v in exposed.get_outputs()]!=['logits',EMBEDDING]):
        raise ValueError('Unexpected AST sessions')
    arrays={}; cases=[]
    inputs=[('synthetic_silence1',synthetic_pcm(1000,'silence'),None),
            ('synthetic_tone10',synthetic_pcm(10000,'tone'),None),
            ('synthetic_sweep2',synthetic_pcm(2000,'sweep'),None)]
    for identity,path,sha in files:
        decoded=subprocess.run([str(args.ffmpeg),'-v','error','-i',str(path),'-map','0:a:0',
            '-vn','-t','10','-ac','1','-ar','16000','-f','s16le','pipe:1'],capture_output=True,timeout=20)
        if decoded.returncode or not 32000<=len(decoded.stdout)<=320000 or len(decoded.stdout)%2:
            raise ValueError('AST interface public decode failed or under1s')
        if digest(path)!=sha:
            raise ValueError('AST interface preview changed during decode')
        inputs.append((identity,np.frombuffer(decoded.stdout,dtype='<i2').astype(np.float32)/32768,sha))
    for identity,pcm,preview_sha in inputs:
        features=extract(pcm)[None]
        started=time.monotonic()
        old=baseline.run(['logits'],{'input_values':features})[0]
        reference_seconds=time.monotonic()-started
        started=time.monotonic()
        new,embedding=exposed.run(['logits',EMBEDDING],{'input_values':features})
        exposed_seconds=time.monotonic()-started
        error=compare(old,new)
        if embedding.shape!=(1,768) or embedding.dtype!=np.float32 or not np.isfinite(embedding).all():
            raise ValueError('Invalid AST representation')
        arrays[identity]=embedding[0]
        cases.append(dict(id=identity,actual_samples=len(pcm),preview_sha256=preview_sha,
            input_features_sha256=hashlib.sha256(features.tobytes()).hexdigest(),
            exact_527_logits_equal=True,max_absolute_logit_error=error,embedding_shape=list(embedding.shape),
            reference_seconds=reference_seconds,exposed_seconds=exposed_seconds))
    if digest(MODEL)!=MODEL_SHA:
        raise ValueError('Original AST model changed')
    np.savez_compressed(args.output/'public-embedding.npz',**arrays)
    write(args.output/'interface.json',dict(cases=cases,model_sha256=MODEL_SHA,
        memory_model_sha256=hashlib.sha256(memory).hexdigest(),memory_model_bytes=len(memory),
        embeddings_sha256=digest(args.output/'public-embedding.npz'),source_snapshot_sha256=SOURCE_SHA,
        process_rss_two_sessions_bytes=psutil.Process().memory_info().rss,
        product_memory_measurement=False,training=False,private_media_read=False,
        quality_validated=False,android_validated=False,product_changed=False))
    write(args.output/'completion.json',dict(status='completed',runtime=actual,
        runner_sha256=digest(Path(__file__)),frontend_sha256=digest(ROOT/'tools/research/ondevice_ast_features.py'),
        model_sha256=MODEL_SHA,product_changed=False))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('sources','media','ffmpeg','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--sources-sha',required=True); parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('sources','media','ffmpeg','output'): setattr(args,name,getattr(args,name).resolve())
    validate_output_path(args.output)
    validate_inputs(args)
    if args.worker: worker(args); return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    env.pop('OMX_ROOT',None); env.pop('OMX_STATE_ROOT',None)
    argv=[sys.executable,'-B',str(Path(__file__).resolve()),'--worker']
    for name in ('sources','sources_sha','media','ffmpeg','output'):
        argv+=['--'+name.replace('_','-'),str(getattr(args,name))]
    result=run_bounded(argv,cwd=ROOT,env=env,log_path=args.output/'run.log',timeout_seconds=120,memory_limit_bytes=3*1024**3)
    write(args.output/'process.json',asdict(result))
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('AST embedding interface failed; inspect logs')
    done=read_report(args.output/'completion.json')
    if (done['status']!='completed' or done['runtime']!=RUNTIME or done['runner_sha256']!=digest(Path(__file__))
            or done['frontend_sha256']!=digest(ROOT/'tools/research/ondevice_ast_features.py')
            or done['model_sha256']!=MODEL_SHA):
        raise SystemExit('AST embedding completion provenance differs')
    print(json.dumps(dict(status='completed',product_changed=False)))


if __name__=='__main__': main()
