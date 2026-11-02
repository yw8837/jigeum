"""Single diagnostic ORT backend comparison with recorded Torch; no adoption."""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import platform
import sys
import time
from types import MethodType

from efficientat_probe import load_corpus
from ondevice_av_probe import validate_output_path
from vtc1_events import read_json, SCORES_LIMIT_BYTES
from vtc1_probe import PIN, LABELS, digest, native_model, verify_assets, native_coverage

GRAPH_SHA = 'dfb7abbd27bba6149c87fe6b129ce41da32f84252d364cadb1ac6f07b72cacc8'


def worker(args):
    import socket
    def denied(*unused,**unused_kw):
        raise RuntimeError('Network prohibited during local backend diagnostic')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    import numpy as np
    import torch
    import onnxruntime as ort
    pin = json.loads(PIN.read_text('utf-8'))
    if dict(python=platform.python_version(),torch=torch.__version__,numpy=np.__version__)!=pin['runtime'] or ort.__version__!='1.24.3':
        raise ValueError('Backend runtime differs from pin')
    verify_assets(args.assets,pin)
    if args.graph.stat().st_size!=4544244 or digest(args.graph)!=GRAPH_SHA:
        raise ValueError('Graph differs from fixed failed-export diagnostic')
    reference = read_json(args.scores,SCORES_LIMIT_BYTES)
    entries = load_corpus(args.corpus)
    if (reference['status']!='completed' or reference['model_sha256']!=pin['weight']['sha256']
            or reference['corpus_manifest_sha256']!=[digest(args.corpus/'manifest.json')]
            or len(reference['cases'])!=len(entries)):
        raise ValueError('Recorded reference pin differs')
    recorded_runtime = reference.get('runtime',{})
    if ({key:recorded_runtime.get(key) for key in ('torch','numpy')} !=
            {key:pin['runtime'][key] for key in ('torch','numpy')}
            or recorded_runtime.get('python',pin['runtime']['python'])!=pin['runtime']['python']):
        raise ValueError('Recorded reference runtime differs')
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    session = ort.InferenceSession(str(args.graph),sess_options=options,providers=['CPUExecutionProvider'])
    torch.set_num_threads(4)
    model,ns,_ = native_model(args.assets,pin)
    forwards = [0]
    def ort_forward(unused_self,inputs,return_intermediate=None):
        if return_intermediate is not None:
            raise ValueError('Intermediate output unsupported')
        forwards[0] += 1
        observed = session.run(['voice_scores'],{'waveform':inputs.numpy()})[0]
        return torch.from_numpy(observed)
    model.forward = MethodType(ort_forward,model)
    rows = []
    for entry,recorded in zip(entries,reference['cases']):
        if recorded['id']!=entry['id'] or recorded['source_pcm_sha256']!=entry['pcm_sha256']:
            raise ValueError('Recorded source/order differs')
        path = args.corpus/(entry['id']+'.f32')
        pcm = np.fromfile(path,dtype='<f4').copy()
        features = ns['SlidingWindowFeature'](pcm[:,None],ns['SlidingWindow'](
            start=-.5/16000,duration=1/16000,step=1/16000))
        chunks = ns['SlidingWindow'](duration=2.,step=.5)
        began = time.monotonic()
        with torch.inference_mode():
            output = model.slide(features,chunks,batch_size=1,device='cpu').data
        coverage,_ = native_coverage(features,model.resolution,chunks,model.alignment)
        starts = np.arange(len(output))*27
        keep = (starts+325<=len(pcm)) & (coverage>0)
        expected = np.asarray(recorded['scores'],dtype=np.float32)
        if (recorded['labels']!=list(LABELS)
                or not np.array_equal(starts[keep],recorded['frame_start_samples'])
                or not np.array_equal(starts[keep]+325,recorded['frame_end_samples'])
                or output[keep].shape!=expected.shape or not np.isfinite(output).all()
                or not np.isfinite(expected).all() or np.any((expected<0)|(expected>1))
                or np.any((output<0)|(output>1))):
            raise ValueError('Diagnostic coordinate/shape differs')
        error = float(np.abs(output[keep]-expected).max())
        if digest(path)!=entry['pcm_sha256']:
            raise ValueError('Source changed during diagnostic')
        rows.append(dict(id=entry['id'],pcm_sha256=entry['pcm_sha256'],frames=len(expected),
            max_absolute_error=error,within_unchanged_numeric_gate=error<=5e-4,ort_slide_seconds=time.monotonic()-began))
    report = dict(status='completed',scope='diagnostic_only_float_export_failed_silence',
        private_cases=len(rows),torch_classifier_forwards=0,onnx_chunk_forwards=forwards[0],
        original_scores_sha256=digest(args.scores),runner_sha256=digest(Path(__file__)),graph_sha256=GRAPH_SHA,
        recorded_reference_runtime=recorded_runtime,historical_python_recorded='python' in recorded_runtime,
        max_absolute_error_gate=5e-4,cases=rows,
        commercial_permission_confirmed=False,quality_validated=False,android_validated=False,product_changed=False)
    (args.output/'report-private.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ('assets','graph','scores','corpus','output'):
        parser.add_argument('--'+option,type=Path,required=True)
    parser.add_argument('--worker',action='store_true')
    args = parser.parse_args()
    for name in ('assets','graph','scores','corpus','output'):
        path = getattr(args,name).resolve()
        validate_output_path(path)
        setattr(args,name,path)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ,PYTHONDONTWRITEBYTECODE='1',HF_HUB_OFFLINE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    result = run_bounded([sys.executable,str(Path(__file__).resolve()),'--worker',
        *[arg for name in ('assets','graph','scores','corpus','output') for arg in ('--'+name,str(getattr(args,name)))]],
        cwd=Path(__file__).resolve().parents[2],env=env,log_path=args.output/'diagnostic.log',
        timeout_seconds=180,memory_limit_bytes=3*1024**3)
    (args.output/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Backend diagnostic failed; inspect local log/process')
    report = read_json(args.output/'report-private.json',131072)
    if report['status']!='completed':
        raise SystemExit('Incomplete backend evidence')
    print(json.dumps(dict(status='completed',private_cases=report['private_cases'],
        within_numeric_gate=sum(r['within_unchanged_numeric_gate'] for r in report['cases']),
        float_export_accepted=False,product_changed=False)))


if __name__ == '__main__':
    main()
