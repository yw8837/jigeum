"""Internal local VTC1 graph/precision probe; no media, uploads or app changes."""
import argparse
from dataclasses import asdict
import json
import math
import os
from pathlib import Path
import platform
import sys
import time

from ondevice_av_probe import validate_output_path
from vtc1_probe import PIN, digest, native_model, verify_assets


def float_parity_passed(report):
    expected = {'silence_1s':(16000,581),'tone_2s':(32000,1174),'noise_1999ms':(31984,1173)}
    rows = report.get('cases',[])
    if report.get('status')!='passed' or not isinstance(rows,list) or len(rows)!=3:
        return False
    if {row.get('id') for row in rows}!=set(expected):
        return False
    for row in rows:
        samples,frames = expected[row['id']]
        error = row.get('max_absolute_error')
        if (row.get('pcm_samples')!=samples or row.get('shape')!=[1,frames,5]
                or row.get('finite') is not True or row.get('passed') is not True
                or type(error) not in (int,float) or not math.isfinite(error) or not 0<=error<=5e-4):
            return False
    return True


def worker(args):
    import socket
    def denied(*unused,**unused_kw):
        raise RuntimeError('Network prohibited during graph export')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    import numpy as np
    import onnx
    import onnxruntime as ort
    import torch
    pin = json.loads(PIN.read_text('utf-8'))
    runtime = dict(python=platform.python_version(),torch=torch.__version__,numpy=np.__version__)
    if runtime != pin['runtime'] or onnx.__version__!='1.20.1' or ort.__version__!='1.24.3':
        raise ValueError('Graph runtime differs from predeclared pin')
    verify_assets(args.assets,pin)
    torch.set_num_threads(4)
    torch.manual_seed(17)
    model, _, details = native_model(args.assets,pin)
    graph = args.output/'vtc1-float.onnx'
    if graph.exists():
        raise FileExistsError('Graph output already exists')
    started = time.monotonic()
    torch.onnx.export(model,(torch.zeros(1,32000,1),),str(graph),opset_version=17,
        input_names=['waveform'],output_names=['voice_scores'],
        dynamic_axes={'waveform':{1:'samples'},'voice_scores':{1:'frames'}},
        dynamo=False,external_data=False,do_constant_folding=True)
    export_seconds = time.monotonic()-started
    if not 1 <= graph.stat().st_size <= 16*1024**2:
        raise ValueError('Graph size outside 16MiB contract')
    onnx.checker.check_model(str(graph),full_check=True)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    session = ort.InferenceSession(str(graph),sess_options=options,providers=['CPUExecutionProvider'])
    generators = np.random.default_rng(17)
    signals = [('silence_1s',np.zeros(16000,dtype=np.float32)),
        ('tone_2s',(.03*np.sin(2*np.pi*350*np.arange(32000)/16000)).astype(np.float32)),
        ('noise_1999ms',generators.normal(0,.03,31984).astype(np.float32))]
    rows = []
    for identity,signal in signals:
        values = signal[None,:,None]
        with torch.inference_mode():
            reference = model(torch.from_numpy(values)).numpy()
        begin = time.monotonic()
        observed = session.run(['voice_scores'],{'waveform':values})[0]
        elapsed = time.monotonic()-begin
        expected_frames = (len(signal)-325)//27+1
        if (observed.shape != reference.shape or observed.shape != (1,expected_frames,5)
                or not np.isfinite(observed).all() or np.any((observed<0)|(observed>1))):
            raise ValueError('Graph shape/range differs')
        error = float(np.abs(reference-observed).max())
        rows.append(dict(id=identity,pcm_samples=len(signal),shape=list(observed.shape),
            max_absolute_error=error,ort_seconds=elapsed,finite=True,passed=error<=5e-4))
    if digest(args.assets/pin['weight']['name']) != pin['weight']['sha256']:
        raise ValueError('Weight changed during export')
    report = dict(status='passed' if all(r['passed'] for r in rows) else 'failed',
        private_classifier_forwards=0,synthetic_comparison_cases=len(rows),
        runtime=dict(runtime,onnx=onnx.__version__,onnxruntime=ort.__version__),
        runner_sha256=digest(Path(__file__)),weight_sha256=pin['weight']['sha256'],architecture=details,
        graph_sha256=digest(graph),graph_bytes=graph.stat().st_size,opset=17,
        max_absolute_error_gate=5e-4,export_seconds=export_seconds,cases=rows,
        commercial_permission_confirmed=False,quality_validated=False,android_validated=False,product_changed=False)
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    if not float_parity_passed(report):
        raise SystemExit('Float graph failed predeclared precision gate')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--worker',action='store_true')
    args = parser.parse_args()
    args.assets = args.assets.resolve(strict=True)
    args.output = args.output.resolve()
    for path in (args.assets,args.output):
        validate_output_path(path)
    if args.worker:
        worker(args)
        return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    env = dict(os.environ,PYTHONDONTWRITEBYTECODE='1',HF_HUB_OFFLINE='1')
    env.pop('OMX_ROOT',None)
    env.pop('OMX_STATE_ROOT',None)
    result = run_bounded([sys.executable,str(Path(__file__).resolve()),'--worker','--assets',str(args.assets),
        '--output',str(args.output)],cwd=Path(__file__).resolve().parents[2],env=env,
        log_path=args.output/'export.log',timeout_seconds=180,memory_limit_bytes=3*1024**3)
    (args.output/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete or result.remaining_job_processes:
        raise SystemExit('VTC1 graph probe failed; inspect local process/log')
    report = json.loads((args.output/'report.json').read_text('utf-8'))
    if not float_parity_passed(report):
        raise SystemExit('Incomplete graph evidence')
    print(json.dumps(dict(status='passed',graph_bytes=report['graph_bytes'],
        synthetic_cases=len(report['cases']),private_classifier_forwards=0,product_changed=False)))


if __name__ == '__main__':
    main()
