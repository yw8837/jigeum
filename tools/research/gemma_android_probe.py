"""Private, separately packaged Android Gemma research runner. Never a beta deploy.

Requires an explicit adb serial, inspected debug-only APK, and pinned local model.
It does not change device network settings or touch com.moa.babylanguage.
All input and native output stays in this checkout's Git-ignored work directory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import uuid
import wave

from ondevice_av_probe import (MANIFEST, validate_bundle, validate_output_path,
                               verify_model, whole_interval_prompt)

PACKAGE = "com.agattok.gemmaprobe"
RUNNER = PACKAGE + "/" + PACKAGE + ".ProbeInstrumentation"


def run_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9_-]{1,40}", value):
        raise ValueError("Invalid run id")
    return value


def validate_apk_badging(text):
    match = re.search(r"^package: name='([^']+)'", text, re.MULTILINE)
    if not match or match[1] != PACKAGE or "application-debuggable" not in text.splitlines():
        raise ValueError("Only the separate debuggable research APK may be installed")


def validate_apk_permissions(text):
    if "android.permission.INTERNET" in text:
        raise ValueError("The research APK must not have Internet permission")


def validate_result(value, expected_id, expected_visual_budget=None):
    if not isinstance(value, dict) or value.get("run_id") != run_id(expected_id):
        raise ValueError("Missing or stale terminal result")
    if value.get("cleanup_complete") is not True:
        raise ValueError("Native cleanup not confirmed")
    if expected_visual_budget is not None and (
            type(value.get("visual_token_budget")) is not int or
            value["visual_token_budget"] != expected_visual_budget):
        raise ValueError("Visual budget differs from requested experiment")
    status = value.get("status")
    if status == "completed":
        if (not isinstance(value.get("inference_ms"), (int, float)) or
                not 0 <= value["inference_ms"] < float("inf") or
                type(value.get("response_bytes")) is not int or value["response_bytes"] <= 0):
            raise ValueError("Completed inference lacks measured nonempty output")
    elif status == "cancelled":
        if value.get("cancel_requested") is not True:
            raise ValueError("Cancellation not requested")
        for metric in ("cancel_request_ms", "cancel_call_ms", "cancel_terminal_ms"):
            number = value.get(metric)
            if type(number) not in (int, float) or not 0 <= number < float("inf"):
                raise ValueError("Missing or invalid acknowledged cancellation timing")
    else:
        raise ValueError("Inference did not complete or cancel cleanly")
    return status


def public_summary(value):
    allowed = {"run_id", "status", "cleanup_complete", "inference_ms", "response_bytes",
               "load_ms", "verify_model_ms", "first_token_ms", "first_token_metric", "peak_pss_kb",
               "thermal_before", "thermal_after", "device_model", "device_api", "device_abi",
               "cancel_requested", "error_kind", "visual_token_budget", "max_vision_token_budget"}
    return {k: v for k, v in value.items() if k in allowed and
            (isinstance(v, (str, int, float, bool)) or v is None)}


def verify_response(value, data):
    if (len(data) != value.get("response_bytes") or
            hashlib.sha256(data).hexdigest() != value.get("response_sha256")):
        raise ValueError("Response differs from terminal size/hash evidence")


def prepare_input(source, output, mode, video_only=False):
    output.mkdir()
    if mode == "text":
        # No user media is needed for a native text smoke.
        audio = output / "audio.wav"
        with wave.open(str(audio), "wb") as writer:
            writer.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
            writer.writeframes(b"\0\0" * 16000)
        value = {"duration_ms": 1000, "prompt": "Write only this Korean greeting: 안녕하세요",
                 "audio": "audio.wav", "frames": []}
    else:
        if source is None:
            raise ValueError("AV mode requires an already prepared local bundle")
        bundle = json.loads(Path(source).read_text(encoding="utf-8"))
        validate_bundle(bundle, video_only=video_only)
        value = {"duration_ms": bundle["duration_ms"],
                 "prompt": whole_interval_prompt(bundle["duration_ms"], False, video_only),
                 "audio": None, "frames": []}
        if bundle.get("audio") and not video_only:
            shutil.copyfile(bundle["audio"], output / "audio.wav")
            value["audio"] = "audio.wav"
        for i, frame in enumerate(bundle["frames"]):
            name = f"frame-{i:03d}.png"
            from PIL import Image
            with Image.open(frame["path"]) as image:
                if not (0 < image.width <= 512 and 0 < image.height <= 512):
                    raise ValueError("Prepared images must be at most 512 pixels per side")
                # Lossless encoding of the same decoded pixels, no resampling/cropping.
                image.convert("RGB").save(output / name, format="PNG")
            value["frames"].append({"offset_ms": frame["offset_ms"], "path": name})
    (output / "bundle.json").write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    return value


def command(args, timeout=60, **kwargs):
    result = subprocess.run(args, capture_output=True, timeout=timeout, **kwargs)
    if result.returncode:
        # Never print native messages: the caller stores failure output privately.
        raise subprocess.CalledProcessError(result.returncode, args, result.stdout, result.stderr)
    return result.stdout


def require_no_symlink(adb, relative):
    result = subprocess.run(adb + ["shell", "run-as", PACKAGE, "test", "-L", relative],
                            capture_output=True, timeout=15)
    if result.returncode != 1 or result.stderr.strip():
        raise RuntimeError("Symlink or unverified device destination")


def prepare_device_paths(adb):
    root = command(adb + ["shell", "run-as", PACKAGE, "readlink", "-f", "."]).decode().strip()
    if not root.startswith("/") or not root.endswith("/" + PACKAGE):
        raise RuntimeError("Unexpected device sandbox")
    for path in ("files", "files/input", "files/results", "files/model.litertlm"):
        require_no_symlink(adb, path)
    command(adb + ["shell", "run-as", PACKAGE, "mkdir", "-p", "files/input", "files/results"])
    for path in ("files/input", "files/results"):
        resolved = command(adb + ["shell", "run-as", PACKAGE, "readlink", "-f", path]).decode().strip()
        if resolved != root + "/" + path:
            raise RuntimeError("Device directory escaped the sandbox")


def require_stopped(adb):
    result = subprocess.run(adb + ["shell", "pidof", PACKAGE], capture_output=True, timeout=15)
    if result.returncode != 1 or result.stdout.strip() or result.stderr.strip():
        raise RuntimeError("Research process termination not confirmed")


def transfer_public_model(adb, local):
    # Only the pinned PUBLIC model uses shell staging. User media never does.
    stage = "/data/local/tmp/agattok-public-model-" + uuid.uuid4().hex + ".litertlm"
    try:
        command(adb + ["push", "-Z", str(local), stage], timeout=300)
        command(adb + ["shell", "chmod", "644", stage])
        require_no_symlink(adb, "files/model.litertlm")
        command(adb + ["shell", "run-as", PACKAGE, "cp", stage, "files/model.litertlm"], timeout=120)
        size = command(adb + ["shell", "run-as", PACKAGE, "stat", "-c", "%s", "files/model.litertlm"])
        if size.strip() != str(Path(local).stat().st_size).encode():
            raise RuntimeError("Model copy is incomplete")
    finally:
        command(adb + ["shell", "rm", "-f", "--", stage], timeout=15)
        removed = subprocess.run(adb + ["shell", "test", "-e", stage], capture_output=True, timeout=15)
        if removed.returncode != 1 or removed.stderr.strip():
            raise RuntimeError("Public model staging cleanup not confirmed")


def device_run(args, destination, plan):
    identity = plan["run_id"]
    adb = [args.adb, "-s", args.serial]
    failure = cleanup_error = result = None
    touched = executed = False
    stage = "device_check"
    started = time.monotonic()
    try:
        if command(adb + ["get-state"]).strip() != b"device":
            raise RuntimeError("Explicit device is not available")
        if command(adb + ["shell", "am", "get-current-user"]).strip() != b"0":
            raise RuntimeError("Research runner requires the primary user")
        stage = "installation"
        touched = True
        command(adb + ["install", "--user", "0", "-r", args.apk], timeout=120)
        command(adb + ["shell", "am", "force-stop", "--user", "0", PACKAGE])
        require_stopped(adb)
        stage = "private_path_preflight"
        prepare_device_paths(adb)
        existing = subprocess.run(adb + ["shell", "run-as", PACKAGE, "test", "-e", f"files/results/{identity}.json"],
                                  capture_output=True, timeout=15)
        if existing.returncode != 1 or existing.stderr.strip():
            raise RuntimeError("Result id already exists or existence check failed")

        def transfer(local, relative):
            require_no_symlink(adb, relative)
            # Prepared WAV/PNG/JSON files are small and bounded. A pipe avoids the
            # observed Windows adb truncation with an inherited regular-file stdin.
            data = Path(local).read_bytes()
            command(adb + ["exec-in", "run-as", PACKAGE, "dd", f"of={relative}", "bs=65536"],
                    timeout=30, input=data)
            size = command(adb + ["shell", "run-as", PACKAGE, "stat", "-c", "%s", relative])
            if size.strip() != str(len(data)).encode():
                raise RuntimeError("Input transfer is incomplete")

        stage = "input_transfer"
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        size = subprocess.run(adb + ["shell", "run-as", PACKAGE, "stat", "-c", "%s", "files/model.litertlm"],
                              capture_output=True, timeout=15)
        if size.returncode or size.stdout.strip() != str(manifest["size_bytes"]).encode():
            transfer_public_model(adb, args.model)
        for source in sorted((destination / "input").iterdir()):
            if not re.fullmatch(r"(bundle\.json|audio\.wav|frame-[0-9]{3}\.png)", source.name):
                raise ValueError("Unexpected prepared input name")
            transfer(source, "files/input/" + source.name)
        instrument = adb + ["shell", "am", "instrument", "--user", "0", "-w"]
        options = {"run_id": identity, "mode": args.mode, "backend": args.backend,
                   "vision_backend": args.vision_backend, "context_tokens": args.context_tokens,
                   "output_tokens": args.output_tokens, "cancel_after_ms": args.cancel_after_ms,
                   "video_only": str(args.video_only).lower(), "media_order": args.media_order,
                   "visual_token_budget": args.visual_token_budget}
        for key, value in options.items():
            instrument += ["-e", key, str(value)]
        instrument += [RUNNER]
        stage = "instrumentation"
        executed = True
        with (destination / "instrument-private.log").open("wb") as log:
            process = subprocess.run(instrument, stdout=log, stderr=subprocess.STDOUT, timeout=args.timeout)
        if process.returncode:
            raise RuntimeError("Instrumentation process failed")
        stage = "terminal_evidence"
        raw = command(adb + ["exec-out", "run-as", PACKAGE, "cat", f"files/results/{identity}.json"])
        (destination / "result-private.json").write_bytes(raw)
        result = json.loads(raw)
        validate_result(result, identity, args.visual_token_budget)
        response = command(adb + ["exec-out", "run-as", PACKAGE, "cat", f"files/results/{identity}-response.txt"])
        (destination / "response-private.txt").write_bytes(response)
        verify_response(result, response)
    except Exception as error:
        failure = type(error).__name__
        if isinstance(error, (subprocess.CalledProcessError, subprocess.TimeoutExpired)):
            (destination / "command-error-private.log").write_bytes(
                (error.stdout or b"") + b"\n" + (error.stderr or b""))
    finally:
        if touched:
            try:
                command(adb + ["shell", "am", "force-stop", "--user", "0", PACKAGE], timeout=15)
                require_stopped(adb)
            except Exception as error:
                cleanup_error = type(error).__name__
    summary = {**plan, **public_summary(result or {}), "run_id": identity,
               "android_executed": executed, "host_elapsed_s": round(time.monotonic() - started, 3),
               "host_status": "failed" if failure or cleanup_error else "finished",
               "host_error": failure, "host_stage": stage, "host_cleanup_error": cleanup_error,
               "quality_validated": False}
    (destination / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--mode", choices=["text", "av"], default="text")
    parser.add_argument("--bundle")
    parser.add_argument("--model")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--apk")
    parser.add_argument("--aapt")
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--backend", choices=["cpu", "gpu"], default="cpu")
    parser.add_argument("--vision-backend", choices=["cpu", "gpu"], default="gpu")
    parser.add_argument("--context-tokens", type=int, choices=[4096, 8192, 16384], default=4096)
    parser.add_argument("--output-tokens", type=int, default=120)
    parser.add_argument("--visual-token-budget", type=int, choices=[0, 70, 140, 280], default=0,
                        help="0 preserves SDK default; explicit budgets require AV and the pinned model")
    parser.add_argument("--cancel-after-ms", type=int, default=0)
    parser.add_argument("--video-only", action="store_true")
    parser.add_argument("--media-order", choices=["legacy", "recommended"], default="legacy")
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()
    identity = run_id(args.run_id)
    if not 1 <= args.output_tokens <= 700 or not 0 <= args.cancel_after_ms <= 10000 or not 10 <= args.timeout <= 600:
        parser.error("Output/cancel/timeout limits invalid")
    if args.video_only and args.mode != "av":
        parser.error("video-only is only valid in AV mode")
    if args.visual_token_budget and args.mode != "av":
        parser.error("Explicit visual token budget requires AV mode")
    destination = Path(args.output).resolve()
    validate_output_path(destination)
    if destination.exists():
        raise ValueError("Use a new output directory; preserve prior evidence")
    destination.mkdir(parents=True)
    bundle = prepare_input(args.bundle, destination / "input", args.mode, args.video_only)
    plan = {"run_id": identity, "mode": args.mode, "duration_ms": bundle["duration_ms"],
            "frame_count": len(bundle["frames"]), "backend": args.backend,
            "context_tokens": args.context_tokens, "output_tokens": args.output_tokens,
            "visual_token_budget": args.visual_token_budget,
            "cancel_after_ms": args.cancel_after_ms, "media_order": args.media_order,
            "quality_validated": False, "android_executed": False}
    (destination / "plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
    if args.prepare_only:
        print(json.dumps(plan))
        return
    if not all((args.apk, args.aapt, args.adb, args.serial, args.model)):
        parser.error("Execution needs explicit apk/aapt/adb/serial/model")
    # Inspect exact artifact before any device mutation.
    validate_apk_badging(command([args.aapt, "dump", "badging", args.apk]).decode())
    validate_apk_permissions(command([args.aapt, "dump", "permissions", args.apk]).decode())
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    verify_model(Path(args.model), manifest)
    summary = device_run(args, destination, plan)
    print(json.dumps(summary))
    if summary["host_status"] != "finished":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
