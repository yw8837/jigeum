"""Bounded synthetic-only ONNX portability probe; never a product decision."""
import argparse
import ast
from collections import Counter
from dataclasses import asdict
import importlib.metadata
import json
import os
from pathlib import Path
import socket
import sys
import time
import warnings

from efficientat_probe import digest
from ondevice_av_probe import validate_output_path

PIN = Path(__file__).with_name('efficientsed-models.json')
NAME = 'fmn10+tf-256_advanced-kd-weak-strong.pt'


def float_parity_passed(result):
    """Require all six fixed-shape, finite, tolerance-passing float outputs."""
    rows=[x for x in result.get('exports',[]) if x.get('name')=='mel']
    if len(rows)!=1 or rows[0].get('status')!='completed':
        return False
    values=rows[0].get('numerical',[])
    expected={(case,output) for case in ('silence','tone','noise') for output in (0,1)}
    if len(values)!=len(expected) or {(x.get('case'),x.get('output')) for x in values}!=expected:
        return False
    return all(x.get('finite') is True and x.get('passes_float_tolerance') is True
               and x.get('shape')==([1,447,250] if x['output']==0 else [1,447]) for x in values)


def require_completed_float_result(result):
    """Do not trust a completed label without the independently checked gate."""
    if (result.get('status')!='completed' or result.get('float_parity_passed') is not True
            or not float_parity_passed(result)):
        raise RuntimeError('Required float mel export/parity failed')


def worker(args):
    def denied(*a, **kw):
        raise RuntimeError('Network prohibited during synthetic export probe')
    socket.socket.connect = denied
    socket.socket.connect_ex = denied
    socket.create_connection = denied
    os.environ['HF_HUB_OFFLINE'] = '1'
    pin = json.loads(PIN.read_text(encoding='utf-8'))
    source = args.assets / 'source'
    for relative, expected in pin['source_files'].items():
        if digest(source / relative) != expected:
            raise ValueError('Source hash mismatch: ' + relative)
    asset = next(x for x in pin['weights'] if x['name'] == NAME)
    weights = args.assets / NAME
    assert weights.stat().st_size == asset['size'] and digest(weights) == asset['sha256']
    sys.path.insert(0, str(source))
    import numpy as np
    import torch
    import onnx
    import onnxruntime as ort
    from models.efficient_cnns.fmn.fmn_wrapper import FrameMNWrapper
    from models.sequence_models.helper import init_weights
    from models.sequence_models.roformer.transformer_block import TransformerBlock

    runtime={k:importlib.metadata.version(k.replace('_','-')) for k in pin['runtime']}
    runtime['torch']=torch.__version__
    assert runtime==pin['runtime'], 'Runtime differs from screening pin'

    torch.set_num_threads(4)
    torch.manual_seed(17)
    file = source / 'models/prediction_wrapper.py'
    tree = ast.parse(file.read_text(encoding='utf-8'))
    classes = [x for x in tree.body if isinstance(x, ast.ClassDef) and x.name == 'PredictionsWrapper']
    assert len(classes) == 1
    ns = dict(torch=torch, nn=torch.nn, warnings=warnings,
              init_weights=init_weights, TransformerBlock=TransformerBlock)
    exec(compile(ast.Module(body=classes, type_ignores=[]), str(file), 'exec'), ns)
    base = FrameMNWrapper(1.0)
    dim = base.state_dict()['fmn.features.16.1.bias'].shape[0]
    model = ns['PredictionsWrapper'](base, checkpoint=None, seq_model_type='tf',
                                   seq_model_dim=256, embed_dim=dim).float().eval()
    raw = torch.load(weights, map_location='cpu', weights_only=True)
    state = {('model.'+k[len('net.'):] if k.startswith('net.') else k):v for k,v in raw.items()}
    state = {('model.fmn.'+k[len('model.model.'):] if k.startswith('model.model.') else k):v for k,v in state.items()}
    state = {(k[len('model.'):] if not k.startswith('model.fmn.') else k):v for k,v in state.items()}
    model.load_state_dict(state, strict=True)
    del raw, state
    result = dict(status='running', revision=pin['revision'], asset=asset,
                  parameters=sum(p.numel() for p in model.parameters()),
                  versions=dict(torch=torch.__version__, onnx=onnx.__version__, onnxruntime=ort.__version__),
                  input_policy='synthetic only; fixed batch1 10s; no media or threshold tuning',
                  android_validated=False, exports=[])
    def save():
        (args.output/'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    save()
    t = torch.arange(160000)/16000
    samples = dict(silence=torch.zeros(1,160000),
                   tone=(.03*torch.sin(2*torch.pi*440*t)).unsqueeze(0),
                   noise=.005*torch.randn(1,160000))
    with torch.no_grad():
        mels = {k:model.mel_forward(v) for k,v in samples.items()}
        refs = {k:tuple(v.cpu().numpy() for v in model(x)) for k,x in mels.items()}
    result['mel_shape'] = list(mels['silence'].shape)

    class Waveform(torch.nn.Module):
        def __init__(self, net):
            super().__init__(); self.net=net
        def forward(self, pcm):
            return self.net(self.net.mel_forward(pcm))

    def validate(path, inputs):
        g = onnx.load(str(path))
        onnx.checker.check_model(g)
        options=ort.SessionOptions();options.intra_op_num_threads=4
        session=ort.InferenceSession(str(path), options, providers=['CPUExecutionProvider'])
        errors=[]
        for name, value in inputs.items():
            outputs=session.run(None, {session.get_inputs()[0].name:value.numpy()})
            assert len(outputs)==len(refs[name])
            for idx,(actual, expected) in enumerate(zip(outputs,refs[name])):
                assert actual.shape==expected.shape
                error=np.abs(actual-expected)
                errors.append(dict(case=name,output=idx,shape=list(actual.shape),
                    finite=bool(np.isfinite(actual).all()),max_absolute=float(error.max()),
                    mean_absolute=float(error.mean()),
                    passes_float_tolerance=bool(np.allclose(actual,expected,rtol=1e-3,atol=1e-4))))
        return dict(bytes=path.stat().st_size,sha256=digest(path),
            opsets=[dict(domain=x.domain,version=x.version) for x in g.opset_import],
            operators=dict(Counter((x.domain or 'ai.onnx')+'::'+x.op_type for x in g.graph.node)),
            numerical=errors)

    for name, net, values in [('mel',model,mels),('waveform',Waveform(model).eval(),samples)]:
        row=dict(name=name);result['exports'].append(row);start=time.monotonic()
        path=args.output/(name+'.onnx')
        try:
            torch.onnx.export(net, values['silence'], str(path), dynamo=False,
                opset_version=17, input_names=[name], output_names=['strong_logits','weak_logits'],
                do_constant_folding=True)
            row.update(validate(path, values));row['status']='completed'
        except Exception as exc:
            row.update(status='failed',exception_type=type(exc).__name__,error=str(exc)[:4000])
        row['elapsed_seconds']=time.monotonic()-start;save()
    if result['exports'][0]['status']=='completed':
        import ctypes
        from onnxruntime.quantization import quantize_dynamic, QuantType
        row=dict(name='mel_dynamic_int8_matmul');result['exports'].append(row)
        path=args.output/'mel-dynamic-int8.onnx'
        try:
            # ONNX native shape inference can mishandle Unicode Windows paths.
            # The Win32 short path names the same owned artifact, without copying.
            buf=ctypes.create_unicode_buffer(32768)
            n=ctypes.windll.kernel32.GetShortPathNameW(str(args.output/'mel.onnx'),buf,len(buf))
            if not n or n>=len(buf):
                raise RuntimeError('Win32 short path unavailable for shape inference')
            row['shape_inference_path']='same file via Win32 short path'
            quantize_dynamic(buf.value,str(path),weight_type=QuantType.QInt8,
                             op_types_to_quantize=['MatMul','Gemm'])
            row.update(validate(path,mels));row['status']='completed'
        except Exception as exc:
            row.update(status='failed',exception_type=type(exc).__name__,error=str(exc)[-4000:])
        save()
    result['float_parity_passed']=float_parity_passed(result)
    result['status']='completed' if result['float_parity_passed'] else 'failed'
    save()
    if not result['float_parity_passed']:
        raise SystemExit(2)
    print(json.dumps(dict(status='completed',exports=[(x['name'],x['status']) for x in result['exports']])))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--assets',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--worker',action='store_true')
    args=p.parse_args();args.assets=args.assets.resolve(strict=True);args.output=args.output.resolve()
    validate_output_path(args.output)
    if args.worker:
        worker(args);return
    args.output.mkdir(parents=True,exist_ok=False)
    from bounded_process import run_bounded
    result=run_bounded([sys.executable,str(Path(__file__).resolve()),'--worker',
        '--assets',str(args.assets),'--output',str(args.output)],
        cwd=Path(__file__).resolve().parents[2],log_path=args.output/'export.log',
        timeout_seconds=180,memory_limit_bytes=4*1024**3,env=dict(os.environ))
    (args.output/'process.json').write_text(json.dumps(asdict(result),indent=2),encoding='utf-8')
    if result.exit_code or result.termination or not result.cleanup_complete:
        raise SystemExit('Probe failed; inspect export.log and process.json')
    summary=json.loads((args.output/'result.json').read_text(encoding='utf-8'))
    require_completed_float_result(summary)
    print(json.dumps(summary,indent=2))


if __name__=='__main__':
    main()
