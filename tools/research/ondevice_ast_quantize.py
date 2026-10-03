"""One dynamic INT8 candidate, unchanged FP32 baseline, local CPU comparison.

Only constant MatMul/Gemm weights use QInt8. Conv stays FP32. Threshold
crossings are exploratory numerical sensitivity, NOT calibrated decisions.
Independent processes measure runtime-only memory; PC figures are not ARM.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time
import wave

from ondevice_ast_features import extract, synthetic_pcm
from ondevice_audio_candidate import MONITORED, window_ranges
from ondevice_ast_portability import FP32, graph_report, offline
from ondevice_av_probe import validate_bundle, validate_output_path, verify_model


def memory():
    import psutil
    info = psutil.Process().memory_info()
    return dict(rss_bytes=info.rss, peak_working_set_bytes=getattr(info, "peak_wset", None))


def session(path):
    import onnxruntime as ort
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    return ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])


def memory_worker(args):
    offline()
    import numpy as np
    import onnxruntime as ort
    model = Path(args.memory_model).resolve()
    manifest = json.loads(Path(args.memory_manifest).read_text(encoding="utf-8"))
    verify_model(model, manifest)
    report = dict(onnxruntime=ort.__version__, cpu_threads=4, initial=memory(), android_validated=False)
    start = time.monotonic()
    runtime = session(model)
    report.update(load_seconds=round(time.monotonic() - start, 6), after_load=memory())
    elapsed = []
    for a, b in window_ranges(320000):
        features = extract(synthetic_pcm(20000, "sweep")[a:b])[None]
        start = time.monotonic()
        logits = runtime.run(["logits"], {"input_values": features})[0]
        elapsed.append(round(time.monotonic() - start, 6))
        if not np.isfinite(logits).all():
            raise RuntimeError("Nonfinite runtime output")
    report.update(after_inference=memory(), three_20s_window_seconds=elapsed)
    Path(args.output).write_text(json.dumps(report, indent=2), encoding="utf-8")


def run_worker(args):
    offline()
    import numpy as np
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic
    fp32 = Path(args.fp32).resolve()
    verify_model(fp32, FP32)
    target = Path(args.output).resolve()
    validate_output_path(target)
    # Metadata is pinned via the FP32 model and the original config digest.
    from ondevice_audio_candidate import CONFIG_FILES
    verify_model(Path(args.model_config), CONFIG_FILES["config.json"])
    config = json.loads(Path(args.model_config).read_text(encoding="utf-8"))
    id2label = {int(k): v for k, v in config["id2label"].items()}
    label2id = {v: k for k, v in id2label.items()}
    signals = {f"synthetic_{kind}_{ms}": synthetic_pcm(ms, kind)
               for ms in (1000, 2847, 10000, 15000, 20000)
               for kind in ("tone", "sweep", "silence", "impulse")}
    if args.cases:
        mapping = json.loads(Path(args.cases).read_text(encoding="utf-8"))
        import re
        if not isinstance(mapping, dict) or not mapping:
            raise ValueError("Expected anonymous case mapping")
        for name, path in mapping.items():
            if not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z0-9_-]+", name):
                raise ValueError("Invalid anonymous ID")
            bundle = json.loads(Path(path).read_text(encoding="utf-8"))
            validate_bundle(bundle, audio_only=True)
            with wave.open(bundle["audio"], "rb") as handle:
                signals[name] = np.frombuffer(handle.readframes(handle.getnframes()), dtype="<i2").astype(np.float32) / 32768
    int8 = target / "ast-dynamic-int8.onnx"
    start = time.monotonic()
    # ONNX native path-based shape inference loses Windows Unicode paths,
    # including ORT's user-temp directory. Use the same native shape inference
    # on an in-memory protobuf, with Python reading/writing those exact files.
    import onnx
    original_infer_path = onnx.shape_inference.infer_shapes_path
    def memory_infer_path(source, destination="", check_type=False, strict_mode=False, data_prop=False):
        inferred = onnx.shape_inference.infer_shapes(onnx.load(str(source)),
            check_type=check_type, strict_mode=strict_mode, data_prop=data_prop)
        onnx.save(inferred, str(destination or source))
    try:
        onnx.shape_inference.infer_shapes_path = memory_infer_path
        quantize_dynamic(str(fp32), str(int8),
                         op_types_to_quantize=["MatMul", "Gemm"],
                         weight_type=QuantType.QInt8, per_channel=True, reduce_range=False)
    finally:
        onnx.shape_inference.infer_shapes_path = original_infer_path
    graph = graph_report(int8)
    report = dict(baseline=FP32, candidate=graph, quantize_seconds=round(time.monotonic() - start, 3),
                  quantization="dynamic QInt8 per-channel MatMul/Gemm only; Conv remains FP32",
                  onnxruntime=ort.__version__, windows=[], quality_validated=False,
                  android_validated=False, adopted_decision_gate=None, runtime_memory={})
    fp_session, int_session = session(fp32), session(int8)
    sigmoid = lambda x: 1 / (1 + np.exp(-x))
    thresholds = (0.01, 0.05, 0.1, 0.2, 0.3, 0.5)
    per_class_max = np.zeros(527)
    for name, samples in signals.items():
        for a, b in window_ranges(len(samples)):
            features = extract(samples[a:b])[None]
            fp_logits = fp_session.run(["logits"], {"input_values": features})[0][0]
            int_logits = int_session.run(["logits"], {"input_values": features})[0][0]
            fp_scores, int_scores = sigmoid(fp_logits), sigmoid(int_logits)
            drift = np.abs(fp_scores - int_scores)
            per_class_max = np.maximum(per_class_max, drift)
            np.savez_compressed(target / f"{name}_{a // 16}_{b // 16}-scores-private.npz",
                                fp32_logits=fp_logits, int8_logits=int_logits)
            report["windows"].append(dict(case_id=name, start_ms=a // 16, end_ms=b // 16,
                logits_max_abs=float(np.max(np.abs(fp_logits - int_logits))), sigmoid_max_abs=float(drift.max()),
                top1_changed=bool(np.argmax(fp_logits) != np.argmax(int_logits)),
                monitored={label: dict(fp32=float(fp_scores[label2id[label]]), int8=float(int_scores[label2id[label]]),
                    score_abs_drift=float(drift[label2id[label]]),
                    exploratory_threshold_crossings=[value for value in thresholds
                        if (fp_scores[label2id[label]] >= value) != (int_scores[label2id[label]] >= value)])
                    for label in MONITORED if label in label2id}))
            (target / "quantization.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    report["max_sigmoid_drift_by_class"] = {id2label[i]: float(value) for i, value in enumerate(per_class_max)}
    del fp_session, int_session
    for name, path, manifest in (("fp32", fp32, FP32), ("int8", int8, graph)):
        manifest_file = target / f"{name}-manifest.json"
        manifest_file.write_text(json.dumps(manifest), encoding="utf-8")
        output = target / f"{name}-memory.json"
        subprocess.run([sys.executable, str(Path(__file__).resolve()), "--memory-worker",
            "--memory-model", str(path), "--memory-manifest", str(manifest_file),
            "--output", str(output)], timeout=180, check=True)
        report["runtime_memory"][name] = json.loads(output.read_text(encoding="utf-8"))
    (target / "quantization.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fp32")
    parser.add_argument("--model-config")
    parser.add_argument("--cases")
    parser.add_argument("--output", required=True)
    parser.add_argument("--timeout", type=int, default=1200)
    parser.add_argument("--worker", action="store_true")
    parser.add_argument("--memory-worker", action="store_true")
    parser.add_argument("--memory-model")
    parser.add_argument("--memory-manifest")
    args = parser.parse_args()
    target = Path(args.output).resolve()
    validate_output_path(target)
    if args.memory_worker:
        if target.exists():
            raise FileExistsError("Memory output must be new")
        memory_worker(args)
        return
    if not args.fp32 or not args.model_config or not 1 <= args.timeout <= 3600:
        parser.error("Require pinned FP32 model/config and 1..3600s timeout")
    if args.worker:
        run_worker(args)
        return
    target.mkdir(parents=True, exist_ok=False)
    command = [sys.executable, str(Path(__file__).resolve()), "--worker", "--fp32", args.fp32,
               "--model-config", args.model_config, "--output", str(target)]
    if args.cases:
        command.extend(["--cases", args.cases])
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
