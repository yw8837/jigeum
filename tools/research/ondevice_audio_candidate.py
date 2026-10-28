"""Local AST comparison only; no calibrated infant decision or Android adoption.

Uses the selected WAV in chronological overlapping windows. A 10s classifier
cannot express long-term AV relations; retain its time axis for later evaluation.
All per-case scores remain in ignored work/. Never download from this runner.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import wave

from ondevice_av_probe import validate_bundle, validate_output_path, verify_model

MODEL = dict(size_bytes=346404948,
             sha256="ae0c1e2ad4e1381d851fa9bf298ba13ebc9c5a914cdee2dbe427a6583869924d")
REVISION = "f826b80d28226b62986cc218e5cec390b1096902"
CONFIG_FILES = {
    "config.json": dict(size_bytes=26763,
                        sha256="a93d525511d77e8ecc933d09674b85099815bbbb417c228a4edd655e252fb9ff"),
    "preprocessor_config.json": dict(size_bytes=297,
                        sha256="8d04ba5a9c6fca5d39d0de2b1fd05ecf79deb589fbba279728bbebac39934231")}
MONITORED = ("Babbling", "Baby cry, infant cry", "Baby laughter",
             "Child speech, kid speaking", "Speech", "Hubbub, speech noise, speech babble",
             "Sine wave", "Silence", "Music")


def window_ranges(sample_count: int) -> list[tuple[int, int]]:
    if type(sample_count) is not int or not 16000 <= sample_count <= 320000:
        raise ValueError("Expected 1 to 20 seconds of 16kHz samples")
    size, hop = 160000, 80000
    if sample_count <= size:
        return [(0, sample_count)]
    starts = list(range(0, sample_count - size + 1, hop))
    if starts[-1] != sample_count - size:
        starts.append(sample_count - size)
    return [(start, start + size) for start in starts]


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_worker(args):
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import socket
    def blocked(*unused, **unused_kw):
        raise RuntimeError("Networking prohibited during local inference")
    socket.socket.connect = blocked
    socket.socket.connect_ex = blocked
    socket.create_connection = blocked
    import numpy as np
    import torch
    import transformers
    from transformers import ASTFeatureExtractor, ASTForAudioClassification

    target = Path(args.output).resolve()
    validate_output_path(target)
    model_path = Path(args.model).resolve()
    verify_model(model_path / "model.safetensors", MODEL)
    for filename, manifest in CONFIG_FILES.items():
        verify_model(model_path / filename, manifest)
    cases = json.loads(Path(args.cases).read_text(encoding="utf-8"))
    if not isinstance(cases, dict) or not cases or any(
            not re.fullmatch(r"[a-zA-Z0-9_-]+", name) for name in cases):
        raise ValueError("Cases require unique anonymous alphanumeric IDs")
    # Validate every input before expensive loading.
    bundles = {name: json.loads(Path(path).read_text(encoding="utf-8")) for name, path in cases.items()}
    for bundle in bundles.values():
        validate_bundle(bundle, audio_only=True)
    torch.set_num_threads(4)
    started = time.monotonic()
    processor = ASTFeatureExtractor.from_pretrained(str(model_path), local_files_only=True)
    model = ASTForAudioClassification.from_pretrained(str(model_path), local_files_only=True,
                                                       trust_remote_code=False, use_safetensors=True)
    model.eval()
    model.to("cpu")
    summary = dict(model="MIT/ast-finetuned-audioset-10-10-0.4593", revision=REVISION,
                   model_sha256=MODEL["sha256"], load_seconds=round(time.monotonic() - started, 3),
                   config_sha256=digest(model_path / "config.json"),
                   preprocessor_sha256=digest(model_path / "preprocessor_config.json"),
                   torch=torch.__version__, transformers=transformers.__version__,
                   backend="cpu", threads=4, python_network_blocked=True,
                   quality_validated=False, android_validated=False, cases={})
    label_ids = {label: int(index) for index, label in model.config.id2label.items()}
    for name, bundle in bundles.items():
        wav_path = Path(bundle["audio"])
        with wave.open(str(wav_path), "rb") as wav:
            samples = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32) / 32768
        windows = []
        case_start = time.monotonic()
        for start, end in window_ranges(len(samples)):
            features = processor(samples[start:end], sampling_rate=16000, return_tensors="pt")
            with torch.inference_mode():
                # AudioSet is multilabel. Softmax would incorrectly force classes to compete.
                scores = model(**features).logits.sigmoid()[0].cpu().numpy()
            windows.append(dict(start_ms=start // 16, end_ms=end // 16,
                                scores={model.config.id2label[i]: float(score) for i, score in enumerate(scores)}))
        (target / f"{name}-scores-private.json").write_text(json.dumps(windows, indent=2), encoding="utf-8")
        summary["cases"][name] = dict(
            duration_ms=bundle["duration_ms"], wav_sha256=digest(wav_path),
            inference_seconds=round(time.monotonic() - case_start, 3),
            windows=[dict(start_ms=w["start_ms"], end_ms=w["end_ms"],
                          monitored_scores={label: round(w["scores"][label], 6)
                                            for label in MONITORED if label in label_ids}) for w in windows])
        (target / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--cases", required=True, help="Local JSON mapping anonymous IDs to bundle files")
    parser.add_argument("--output", required=True)
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--worker", action="store_true")
    args = parser.parse_args()
    target = Path(args.output).resolve()
    validate_output_path(target)
    if args.worker:
        run_worker(args)
        return
    target.mkdir(parents=True, exist_ok=False)
    command = [sys.executable, str(Path(__file__).resolve()), "--worker", "--model", args.model,
               "--cases", args.cases, "--output", str(target)]
    try:
        with (target / "stdout.log").open("wb") as out, (target / "stderr.log").open("wb") as err:
            result = subprocess.run(command, stdout=out, stderr=err, timeout=args.timeout)
        code, timeout = result.returncode, False
    except subprocess.TimeoutExpired:
        code, timeout = -1, True
    print(json.dumps(dict(exit_code=code, timed_out=timeout, quality_validated=False)))
    if code:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
