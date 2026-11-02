"""Pinned FP32 AST ONNX export and CPU parity, private outputs under work/.

No downloads from this runner. Inputs, features, full outputs remain local.
Successful parity demonstrates execution equivalence, not infant accuracy.
Run with a separately installed onnx/onnxruntime environment.
"""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import wave

from ondevice_audio_candidate import CONFIG_FILES, MODEL, REVISION, window_ranges
from ondevice_ast_features import extract, synthetic_pcm
from ondevice_av_probe import validate_bundle, validate_output_path, verify_model

FP32 = dict(size_bytes=346557779, sha256="f171a6b0dc4c5d6051cc52f00d4cdf0183a116729677b08e4ef672899c0c4d98")


def digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(8 * 1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def offline():
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import socket
    def blocked(*args, **kwargs):
        raise RuntimeError("Networking prohibited during local inference")
    socket.socket.connect = blocked
    socket.socket.connect_ex = blocked
    socket.create_connection = blocked


def graph_report(model_path):
    import onnx
    graph = onnx.load(str(model_path), load_external_data=False)
    onnx.checker.check_model(graph)
    def tensor_info(value):
        return dict(name=value.name, shape=[d.dim_value for d in value.type.tensor_type.shape.dim],
                    element_type=value.type.tensor_type.elem_type)
    return dict(size_bytes=model_path.stat().st_size, sha256=digest(model_path),
                ir_version=graph.ir_version,
                opsets={entry.domain or "ai.onnx": entry.version for entry in graph.opset_import},
                operators=dict(sorted(Counter(node.op_type for node in graph.graph.node).items())),
                external_data=any(t.data_location for t in graph.graph.initializer),
                initializer_bytes=sum(len(t.raw_data) for t in graph.graph.initializer),
                inputs=[tensor_info(v) for v in graph.graph.input],
                outputs=[tensor_info(v) for v in graph.graph.output])


def run_worker(args):
    offline()
    import numpy as np
    import torch
    import transformers
    from transformers import ASTFeatureExtractor, ASTForAudioClassification
    from transformers.utils import is_speech_available
    target = Path(args.output).resolve()
    validate_output_path(target)
    model_path = Path(args.model).resolve()
    verify_model(model_path / "model.safetensors", MODEL)
    for name, manifest in CONFIG_FILES.items():
        verify_model(model_path / name, manifest)
    # Validate every declared public bundle before loading the model.
    cases = {}
    if args.cases:
        mapping = json.loads(Path(args.cases).read_text(encoding="utf-8"))
        if not isinstance(mapping, dict) or not mapping:
            raise ValueError("Cases require an object")
        for name, path in mapping.items():
            import re
            if not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z0-9_-]+", name):
                raise ValueError("Case IDs require anonymous safe names")
            bundle = json.loads(Path(path).read_text(encoding="utf-8"))
            validate_bundle(bundle, audio_only=True)
            cases[name] = bundle
    torch.set_num_threads(4)
    processor = ASTFeatureExtractor.from_pretrained(str(model_path), local_files_only=True)
    model = ASTForAudioClassification.from_pretrained(str(model_path), local_files_only=True,
        use_safetensors=True, trust_remote_code=False, attn_implementation="eager").eval()
    class Logits(torch.nn.Module):
        def __init__(self, source):
            super().__init__()
            self.source = source
        def forward(self, input_values):
            return self.source(input_values=input_values).logits
    onnx_path = Path(args.existing_onnx).resolve() if args.existing_onnx else target / "ast-fp32.onnx"
    started = time.monotonic()
    if args.existing_onnx:
        verify_model(onnx_path, FP32)
    else:
        with torch.inference_mode():
            torch.onnx.export(Logits(model), torch.zeros(1, 1024, 128), str(onnx_path),
                input_names=["input_values"], output_names=["logits"], opset_version=17,
                dynamo=False, external_data=False, do_constant_folding=True)
    graph = graph_report(onnx_path)
    report = dict(model="MIT/ast-finetuned-audioset-10-10-0.4593", revision=REVISION,
        weights_sha256=MODEL["sha256"], config_pins=CONFIG_FILES,
        torch=torch.__version__, transformers=transformers.__version__,
        torchaudio_frontend=is_speech_available(), graph=graph,
        export_seconds=round(time.monotonic() - started, 3),
        export_reused=bool(args.existing_onnx),
        python_network_blocked=True, quality_validated=False, android_validated=False,
        windows=[], source_files={})
    (target / "export.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    import onnxruntime as ort
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    session = ort.InferenceSession(str(onnx_path), sess_options=options,
                                   providers=["CPUExecutionProvider"])
    report.update(onnxruntime=ort.__version__, providers=session.get_providers(), threads=4)
    signals = {f"synthetic_{kind}_{ms}": synthetic_pcm(ms, kind)
               for ms in (1000, 2847, 10000, 15000, 20000)
               for kind in ("tone", "sweep", "silence", "impulse")}
    if args.cases_only:
        signals = {}
    for name, bundle in cases.items():
        with wave.open(bundle["audio"], "rb") as handle:
            signals[name] = np.frombuffer(handle.readframes(handle.getnframes()), dtype="<i2").astype(np.float32) / 32768
    for name, samples in signals.items():
        for start, end in window_ranges(len(samples)):
            pcm = samples[start:end]
            official = processor(pcm, sampling_rate=16000, return_tensors="np")["input_values"]
            portable = extract(pcm)[None]
            feature_error = float(np.max(np.abs(official - portable)))
            with torch.inference_mode():
                reference = model(input_values=torch.from_numpy(official)).logits.numpy()
            inference_start = time.monotonic()
            actual = session.run(["logits"], {"input_values": official})[0]
            inference_seconds = time.monotonic() - inference_start
            portable_logits = session.run(["logits"], {"input_values": portable})[0]
            logit_error = float(np.max(np.abs(reference - actual)))
            portable_error = float(np.max(np.abs(reference - portable_logits)))
            # AudioSet is multilabel, not a distribution of exclusive classes.
            sigmoid = lambda x: 1 / (1 + np.exp(-x))
            score_error = float(np.max(np.abs(sigmoid(reference) - sigmoid(actual))))
            passed = (feature_error <= 2e-5 and logit_error <= 2e-4 and portable_error <= 2e-4)
            report["windows"].append(dict(case_id=name, start_ms=start // 16, end_ms=end // 16,
                real_feature_frames=1 + (len(pcm) - 400) // 160, feature_max_abs=feature_error,
                fp32_logits_max_abs=logit_error, portable_logits_max_abs=portable_error,
                sigmoid_max_abs=score_error, ort_seconds=round(inference_seconds, 6), passed=passed))
            (target / "parity.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    report["parity_passed"] = all(w["passed"] for w in report["windows"])
    report["source_files"] = {"ASTFeatureExtractor": digest(Path(__import__(
        "inspect").getfile(ASTFeatureExtractor))), "portable_features": digest(Path(__file__).with_name("ondevice_ast_features.py"))}
    (target / "parity.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    if not report["parity_passed"]:
        raise RuntimeError("FP32 feature or logit parity failed; inspect local parity.json")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--cases", help="Optional local bundle mapping; never copied to public logs")
    parser.add_argument("--existing-onnx", help="Reuse only the pinned FP32 export for additional parity inputs")
    parser.add_argument("--cases-only", action="store_true", help="Require cases; omit already verified synthetic controls")
    parser.add_argument("--timeout", type=int, default=1200)
    parser.add_argument("--worker", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.timeout <= 3600:
        parser.error("Timeout must be 1..3600s")
    if args.cases_only and not args.cases:
        parser.error("--cases-only requires --cases")
    target = Path(args.output).resolve()
    validate_output_path(target)
    if args.worker:
        run_worker(args)
        return
    target.mkdir(parents=True, exist_ok=False)
    command = [sys.executable, str(Path(__file__).resolve()), "--worker", "--model", args.model,
               "--output", str(target)]
    if args.cases:
        command.extend(["--cases", args.cases])
    if args.existing_onnx:
        command.extend(["--existing-onnx", args.existing_onnx])
    if args.cases_only:
        command.append("--cases-only")
    try:
        with (target / "stdout.log").open("wb") as stdout, (target / "stderr.log").open("wb") as stderr:
            result = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=args.timeout)
        code, timeout = result.returncode, False
    except subprocess.TimeoutExpired:
        code, timeout = -1, True
    print(json.dumps(dict(exit_code=code, timed_out=timeout, quality_validated=False, android_validated=False)))
    if code:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
