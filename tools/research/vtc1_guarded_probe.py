"""Research-only VTC1 filtered/quiet-window path; not raw graph acceptance."""
import argparse
from dataclasses import asdict
import json
import math
import os
from pathlib import Path
import platform
import sys
import time
from types import MethodType

from efficientat_probe import load_corpus
from ondevice_av_probe import validate_output_path
from vtc1_events import case_events, native_core
from vtc1_probe import PIN, LABELS, digest, native_model, native_coverage, verify_assets

GRAPH_SHA = 'dfb7abbd27bba6149c87fe6b129ce41da32f84252d364cadb1ac6f07b72cacc8'
NUMERIC_GATE = 5e-4


def validate_pcm(pcm, minimum=16000, maximum=320000):
    import numpy as np
    if (not isinstance(pcm,np.ndarray) or pcm.dtype!=np.float32 or pcm.ndim!=1
            or not minimum<=len(pcm)<=maximum or not np.isfinite(pcm).all()
            or np.any(np.abs(pcm)>1)):
        raise ValueError('Expected finite float32 mono PCM in [-1,1]')


def cleanup(pcm):
    import numpy as np
    validate_pcm(pcm)
    mean = float(np.mean(pcm,dtype=np.float64))
    omega = 2*math.pi*80/16000
    alpha = math.sin(omega)/math.sqrt(2)
    a0 = 1+alpha
    b0 = (1+math.cos(omega))/(2*a0)
    b1, a1, a2 = -2*b0, -2*math.cos(omega)/a0, (1-alpha)/a0
    x1=x2=y1=y2=0.
    out = np.empty_like(pcm)
    for i,value in enumerate(pcm):
        x = float(value)-mean
        y = b0*x+b1*x1+b0*x2-a1*y1-a2*y2
        x2,x1,y2,y1 = x1,x,y1,y
        out[i] = max(-1.,min(1.,y))
    return out


def strongest_second_rms(pcm):
    import numpy as np
    validate_pcm(pcm)
    width = min(16000,len(pcm))
    squares = pcm.astype(np.float64)**2
    cumulative = np.concatenate(([0.],np.cumsum(squares,dtype=np.float64)))
    return float(np.sqrt(max(0.,float((cumulative[width:]-cumulative[:-width]).max()))/width))


def make_guarded_forward(original, session, counters, backend):
    def forward(unused_self, inputs, return_intermediate=None):
        import numpy as np
        import torch
        if return_intermediate is not None or inputs.dtype!=torch.float32 or inputs.device.type!='cpu':
            raise ValueError('Unexpected native forward contract')
        values = inputs.numpy()
        if values.ndim!=3 or values.shape[0]!=1 or values.shape[2]!=1:
            raise ValueError('Expected native batch1 mono')
        samples = values[0,:,0]
        validate_pcm(samples)
        shape = (1,(len(samples)-325)//27+1,5)
        if strongest_second_rms(samples)<.003:
            counters['skipped']+=1
            return torch.zeros(shape,dtype=torch.float32)
        counters['classified']+=1
        if backend=='torch':
            output = original(inputs)
        elif backend=='ort':
            output = torch.from_numpy(session.run(['voice_scores'],{'waveform':values})[0])
        else:
            raise ValueError('Unsupported backend')
        if output.shape!=shape or not bool(torch.isfinite(output).all()) or bool(((output<0)|(output>1)).any()):
            raise ValueError('Classifier output contract invalid')
        return output
    return forward


def worker(args):
    import socket
    def denied(*unused,**unused_kw):
        raise RuntimeError('Network prohibited during local comparison')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    import numpy as np
    import torch
    import onnxruntime as ort
    pin = json.loads(PIN.read_text('utf-8'))
    runtime = dict(python=platform.python_version(),torch=torch.__version__,numpy=np.__version__)
    if runtime!=pin['runtime'] or ort.__version__!='1.24.3':
        raise ValueError('Runtime differs from pin')
    verify_assets(args.assets,pin)
    if args.graph.stat().st_size!=4544244 or digest(args.graph)!=GRAPH_SHA:
        raise ValueError('Graph differs from diagnostic pin')
    entries = load_corpus(args.corpus)
    torch.set_num_threads(4)
    model,ns,_ = native_model(args.assets,pin)
    options = ort.SessionOptions()
    options.intra_op_num_threads=4
    options.inter_op_num_threads=1
    options.execution_mode=ort.ExecutionMode.ORT_SEQUENTIAL
    session = ort.InferenceSession(str(args.graph),sess_options=options,providers=['CPUExecutionProvider'])
    original = model.forward
    counts = {backend:dict(classified=0,skipped=0) for backend in ('torch','ort')}
    def run(values,backend):
        model.forward = MethodType(make_guarded_forward(original,session,counts[backend],backend),model)
        with torch.inference_mode():
            return model(torch.from_numpy(values[None,:,None])).numpy()
    signals = [('silence_1s',np.zeros(16000,dtype=np.float32)),
        ('tiny_noise_2s',np.random.default_rng(17).normal(0,1e-6,32000).astype(np.float32)),
        ('below_gate_tone_2s',(.003*np.sin(2*np.pi*350*np.arange(32000)/16000)).astype(np.float32)),
        ('above_gate_tone_2s',(.006*np.sin(2*np.pi*350*np.arange(32000)/16000)).astype(np.float32)),
        ('dc_1s',np.full(16000,.15,dtype=np.float32))]
    synthetic=[]
    for identity,signal in signals:
        clean = cleanup(signal)
        a,b = run(clean,'torch'),run(clean,'ort')
        error=float(np.abs(a-b).max())
        synthetic.append(dict(id=identity,rms=strongest_second_rms(clean),shape=list(a.shape),
            max_absolute_error=error,skipped=strongest_second_rms(clean)<.003,passed=error<=NUMERIC_GATE))
    if not all(row['passed'] for row in synthetic):
        raise ValueError('Guarded synthetic parity failed')
    core,_ = native_core(args.assets)
    rows=[]
    for entry in entries:
        path = args.corpus/(entry['id']+'.f32')
        pcm = np.fromfile(path,dtype='<f4').copy()
        clean=cleanup(pcm)
        features=ns['SlidingWindowFeature'](clean[:,None],ns['SlidingWindow'](
            start=-.5/16000,duration=1/16000,step=1/16000))
        chunks=ns['SlidingWindow'](duration=2.,step=.5)
        began=time.monotonic()
        outputs=[]
        before={key:dict(value) for key,value in counts.items()}
        for backend in ('torch','ort'):
            model.forward=MethodType(make_guarded_forward(original,session,counts[backend],backend),model)
            with torch.inference_mode():
                outputs.append(model.slide(features,chunks,batch_size=1,device='cpu').data)
        coverage,_=native_coverage(features,model.resolution,chunks,model.alignment)
        starts=np.arange(len(outputs[0]))*27
        keep=(starts+325<=len(pcm)) & (coverage>0)
        a,b=outputs[0][keep],outputs[1][keep]
        if (a.shape!=b.shape or a.shape!=(int(keep.sum()),5) or not np.isfinite(a).all()
                or not np.isfinite(b).all() or np.any((a<0)|(a>1)) or np.any((b<0)|(b>1))):
            raise ValueError('Supported slide output invalid')
        error=float(np.abs(a-b).max())
        events=case_events(dict(id=entry['id'],pcm_samples=len(pcm),labels=list(LABELS),
            frame_start_samples=starts[keep].tolist(),frame_end_samples=(starts[keep]+325).tolist(),
            scores=b.tolist(),source_pcm_sha256=entry['pcm_sha256']),core)
        if digest(path)!=entry['pcm_sha256']:
            raise ValueError('Source changed')
        rows.append(dict(id=entry['id'],source_pcm_sha256=entry['pcm_sha256'],
            cleaned_pcm_sha256=__import__('hashlib').sha256(clean.tobytes()).hexdigest(),
            supported_frames=len(a),max_absolute_error=error,passed=error<=NUMERIC_GATE,
            filtered_strongest_second_rms=strongest_second_rms(clean),elapsed_seconds=time.monotonic()-began,
            backend_counts={key:{name:value[name]-before[key][name] for name in value} for key,value in counts.items()},
            event_durations_ms=events['event_durations_ms'],events=events['events']))
        clean.fill(0)
        pcm.fill(0)
    if digest(args.graph)!=GRAPH_SHA:
        raise ValueError('Graph changed during comparison')
    verify_assets(args.assets,pin)
    report=dict(status='passed' if all(row['passed'] for row in rows) else 'failed',
        scope='filtered_quiet_window_pipeline_not_raw_graph_acceptance',
        runtime=runtime,onnxruntime=ort.__version__,runner_sha256=digest(Path(__file__)),
        graph_sha256=GRAPH_SHA,corpus_manifest_sha256=digest(args.corpus/'manifest.json'),
        max_absolute_error_gate=NUMERIC_GATE,synthetic_cases=synthetic,cases=rows,backend_counts=counts,
        raw_float_graph_accepted=False,commercial_permission_confirmed=False,
        quality_validated=False,android_validated=False,product_changed=False)
    (args.output/'report-private.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    if report['status']!='passed':
        raise SystemExit('Guarded pipeline failed unchanged numeric gate')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('assets','graph','corpus','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--worker',action='store_true')
    args=parser.parse_args()
    for name in ('assets','graph','corpus','output'):
        path=getattr(args,name).resolve()
        validate_output_path(path)
        setattr(args,name,path)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1',HF_HUB_OFFLINE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    command=[sys.executable,str(Path(__file__).resolve()),'--worker',
        *[value for name in ('assets','graph','corpus','output') for value in ('--'+name,str(getattr(args,name)))]]
    result=run_bounded(command,cwd=Path(__file__).resolve().parents[2],env=env,
        log_path=args.output/'comparison.log',timeout_seconds=180,memory_limit_bytes=3*1024**3)
    (args.output/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('Guarded comparison failed; inspect process/log')
    report=json.loads((args.output/'report-private.json').read_text('utf-8'))
    if report['status']!='passed' or not all(row['passed'] for row in report['cases']):
        raise SystemExit('Incomplete guarded evidence')
    print(json.dumps(dict(status='passed',cases=len(report['cases']),
        synthetic_cases=len(report['synthetic_cases']),raw_float_graph_accepted=False,product_changed=False)))


if __name__=='__main__':
    main()
