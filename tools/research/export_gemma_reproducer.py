"""Offline export of two pinned synthetic failures; default validates/lists only.

No inference, model, device connection or arbitrary work-tree collection. Native
responses remain under ignored work/. This validates fixture identity/safety,
not recognition quality or the cause of the observed SDK output.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import stat
import struct
import subprocess
import wave
import zlib

from PIL import Image
from ondevice_av_probe import whole_interval_prompt

REPO = Path(__file__).resolve().parents[2]
PROJECT = "tools/research/gemma_android_probe/"
SOURCE_FILES = tuple(PROJECT + name for name in (
    "build.gradle", "settings.gradle", "gradle.properties",
    "src/main/AndroidManifest.xml",
    "src/main/java/com/agattok/gemmaprobe/ProbeInstrumentation.java",
    "src/main/java/com/agattok/gemmaprobe/ProbeInput.java",
    "src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml",
)) + (
    "tools/research/gemma_android_probe.py", "tools/research/ondevice_av_probe.py",
    "tools/research/ondevice-av-model.json", "outputs/MoaAndroid/gradlew",
    "outputs/MoaAndroid/gradlew.bat", "outputs/MoaAndroid/gradle/wrapper/gradle-wrapper.jar",
    "outputs/MoaAndroid/gradle/wrapper/gradle-wrapper.properties",
)
RUNS = {
    "s25-av10-gpu-01": {
        "seconds": 10,
        "bundle_sha256": "997b676deb4592ba0c3a87beec1776a8def3568c32224067aef2d511c630c5ca",
        "wav_sha256": "2652bc2268a758c85de48b98ca5d4577796fe80a1c4b798c6cee4c0472df7ccd",
        "pcm_sha256": "c9203a9fc723d6c902068462563ada868fa054db6e2d945bca9360176b7ea1e8",
        "response_sha256": "11ac360c252e0e238d3f7f9af9edf913f62feb06e2afe48696d51f990d0426e4",
        "response_bytes": 613,
    },
    "s25-av20-gpu-01": {
        "seconds": 20,
        "bundle_sha256": "53992823ee6a852cf7a3c3b7098ba9a481daa6403f47959d478dfbc3ac511d04",
        "wav_sha256": "55a166ceacc9370d8a71b51b0d6f927f499e3c37a956720a57e6889bb4ee040e",
        "pcm_sha256": "d6c31f328edf6f37c77a77eb5588cc8ef4c60ccf5b80fdb3cb43ed36a29ca399",
        "response_sha256": "0eb773495be6d737b87cf47ffb2debe60cb573eea1f4d31a499b65373a9d1b5e",
        "response_bytes": 498,
    },
}
FRAME_HASHES = (
    "b3fd0bbde6f3ac7d42842d52d534a29724d6cf952c2683775df4de086b2577eb",
    "80a760fd1402dbccb5011d645d855aeb4a0830f9c380aa75f9bfd79429acad63",
)
INT_FIELDS = (
    "schema_version", "device_api", "device_total_ram_bytes", "thermal_before", "thermal_after",
    "context_tokens", "output_tokens_limit", "duration_ms", "frame_count", "cancel_after_ms",
    "model_bytes", "max_vision_token_budget", "memory_samples", "peak_pss_kb", "start_pss_kb",
    "end_pss_kb", "peak_native_pss_kb_sampled", "peak_self_rss_kb_sampled",
    "vm_hwm_kb_process_lifetime", "native_heap_allocated_bytes_after_cleanup", "response_bytes",
)
NUMBER_FIELDS = (
    "input_validation_ms", "verify_model_ms", "capabilities_ms", "load_ms",
    "conversation_create_ms", "first_callback_ms", "first_token_ms", "cleanup_ms",
    "inference_ms", "total_ms",
)
BOOL_FIELDS = (
    "quality_validated", "is_physical_device_verified", "video_only", "supports_thinking",
    "supports_function_calling", "cleanup_complete", "cancel_requested",
)
BENCHMARK_FIELDS = (
    "init_seconds", "first_token_seconds", "prefill_tokens", "decode_tokens",
    "prefill_tokens_per_second", "decode_tokens_per_second",
)
SETTINGS = {"mode": "av", "backend": "gpu", "vision_backend": "gpu",
            "audio_backend": "cpu", "media_order": "legacy", "status": "completed"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def no_links(path):
    """Reject symlinks and Windows junction/reparse points in all existing ancestors."""
    for part in (path, *path.parents):
        if part.exists() or part.is_symlink():
            info = part.lstat()
            require(not stat.S_ISLNK(info.st_mode) and
                    not getattr(info, "st_file_attributes", 0) & 0x400, "Linked path refused")


def source_path(root, relative):
    require(isinstance(relative, str) and "\\" not in relative and ":" not in relative and
            not Path(relative).is_absolute() and ".." not in Path(relative).parts,
            "Non-relative source path refused")
    path = root / relative
    no_links(path)
    require(path.resolve().is_relative_to(root.resolve()) and path.is_file(), "Source file missing or escaped")
    return path


def read_file(root, relative, max_bytes=2_000_000):
    path = source_path(root, relative)
    require(path.stat().st_size <= max_bytes, "Source exceeds bounded size")
    return path.read_bytes()


def numeric(value, integer=False):
    require(type(value) is int if integer else type(value) in (int, float), "Metric has invalid type")
    require(0 <= value <= 10**13 and math.isfinite(value), "Metric out of bounds")
    return value


def safe_result(raw, run, spec, response):
    value = json.loads(raw)
    require(isinstance(value, dict) and value.get("run_id") == run, "Result identity mismatch")
    require(value.get("response_sha256") == spec["response_sha256"] == digest(response) and
            value.get("response_bytes") == spec["response_bytes"] == len(response), "Response evidence mismatch")
    response.decode("utf-8")
    clean = {name: numeric(value.get(name), True) for name in INT_FIELDS}
    clean.update({name: numeric(value.get(name)) for name in NUMBER_FIELDS})
    for name in BOOL_FIELDS:
        require(type(value.get(name)) is bool, "Capability/flag has invalid type")
        clean[name] = value[name]
    for name, expected in SETTINGS.items():
        require(value.get(name) == expected, "Unexpected run setting")
        clean[name] = expected  # Only this fixed enum value, never an arbitrary string.
    require(clean["duration_ms"] == spec["seconds"] * 1000 and clean["frame_count"] == spec["seconds"] and
            clean["cleanup_complete"] and not clean["video_only"] and not clean["quality_validated"] and
            not clean["cancel_requested"], "Result does not describe the fixed synthetic run")
    capabilities = value.get("capabilities")
    require(isinstance(capabilities, dict), "Capabilities missing")
    clean["capabilities"] = {}
    for name, expected in {"text": True, "vision": True, "audio": True, "video": False}.items():
        require(type(capabilities.get(name)) is bool and capabilities[name] is expected, "Capabilities changed")
        clean["capabilities"][name] = expected
    benchmark = value.get("sdk_benchmark")
    require(isinstance(benchmark, dict), "SDK benchmark missing")
    clean["sdk_benchmark"] = {name: numeric(benchmark.get(name), name.endswith("_tokens"))
                              for name in BENCHMARK_FIELDS}
    return clean


def validated_wav(data, spec):
    require(digest(data) == spec["wav_sha256"], "Pinned WAV changed")
    require(data[:4] == b"RIFF" and data[8:12] == b"WAVE" and
            struct.unpack_from("<I", data, 4)[0] + 8 == len(data), "Invalid RIFF")
    offset = 12
    chunks = []
    while offset < len(data):
        require(offset + 8 <= len(data), "Truncated WAV chunk")
        kind, size = data[offset:offset + 4], struct.unpack_from("<I", data, offset + 4)[0]
        require(offset + 8 + size <= len(data), "Truncated WAV payload")
        if kind == b"LIST":
            require(data[offset + 8:offset + 8 + size] == b"INFOISFT\r\x00\x00\x00Lavf63.1.100\x00\x00",
                    "Unexpected WAV metadata")
        chunks.append(kind)
        offset += 8 + size + size % 2
    require(offset == len(data) and chunks == [b"fmt ", b"LIST", b"data"], "Unexpected WAV chunks")
    with wave.open(io.BytesIO(data), "rb") as wav:
        require((wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype()) ==
                (1, 2, 16000, "NONE") and wav.getnframes() == spec["seconds"] * 16000, "Unexpected PCM format")
        pcm = wav.readframes(wav.getnframes() + 1)
    require(digest(pcm) == spec["pcm_sha256"] and len(pcm) == spec["seconds"] * 32000, "Pinned PCM changed")
    # A single-bin DFT/least-squares tone fit per whole second. Exact PCM hashes
    # additionally reject arbitrary edits, including small hidden voice residuals.
    samples = struct.unpack("<" + "h" * (len(pcm) // 2), pcm)
    sine = [math.sin(2 * math.pi * 440 * i / 16000) for i in range(16000)]
    cosine = [math.cos(2 * math.pi * 440 * i / 16000) for i in range(16000)]
    dominance = []
    for start in range(0, len(samples), 16000):
        block = samples[start:start + 16000]
        energy = math.fsum(x * x for x in block)
        s = math.fsum(x * y for x, y in zip(block, sine))
        c = math.fsum(x * y for x, y in zip(block, cosine))
        ratio = (s * s + c * c) / (8000 * energy) if energy else 0
        require(ratio >= .995, "Synthetic 440Hz dominance not confirmed")
        dominance.append(ratio)
    # Known ffmpeg ISFT metadata has no identity and is pinned byte-for-byte.
    # Preserve it: changing the decoder input would obscure an unresolved cause.
    return data, {"original_file_sha256": digest(data), "exported_file_sha256": digest(data),
                  "decoded_pcm_sha256": digest(pcm), "original_bytes_preserved": True,
                  "metadata": "Strictly validated fmt/LIST(INFO/ISFT/Lavf63.1.100)/data chunks.",
                  "min_440hz_energy_fraction_per_second": min(dominance),
                  "provenance": "Decoded lossy synthetic 440Hz tone; codec residuals remain in identical PCM."}


def safe_frame(data, blue):
    require(digest(data) == FRAME_HASHES[int(blue)], "Pinned frame changed")
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "Invalid PNG")
    offset = 8
    kinds = []
    while offset < len(data):
        require(offset + 12 <= len(data), "Truncated PNG chunk")
        size = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 8 + size
        require(end + 4 <= len(data) and kind in (b"IHDR", b"IDAT", b"IEND"), "Unexpected PNG metadata")
        require(zlib.crc32(data[offset + 4:end]) == struct.unpack_from(">I", data, end)[0], "PNG CRC mismatch")
        kinds.append(kind)
        offset = end + 4
    require(kinds[0] == b"IHDR" and kinds[-1] == b"IEND", "Invalid PNG structure")
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        color = (0, 0, 254) if blue else (254, 0, 0)
        require(image.size == (384, 384) and image.mode == "RGB" and not image.info and
                image.getextrema() == tuple((x, x) for x in color), "Non-synthetic image refused")
    return data


def build_export(root=REPO):
    """Take a bounded in-memory snapshot; validate everything before any output."""
    root = Path(root).absolute()
    no_links(root)
    members = {name: read_file(root, name) for name in SOURCE_FILES}
    cases = {}
    for run, spec in RUNS.items():
        base = f"work/gemma-android/{run}"
        names = {"bundle.json", "audio.wav"} | {f"frame-{i:03d}.png" for i in range(spec["seconds"])}
        directory = root / base / "input"
        no_links(directory)
        require(directory.is_dir() and {p.name for p in directory.iterdir()} == names, "Unexpected input entries")
        raw = read_file(root, base + "/input/bundle.json", 8192)
        require(digest(raw) == spec["bundle_sha256"], "Pinned input manifest changed")
        bundle = json.loads(raw)
        expected = {"duration_ms": spec["seconds"] * 1000,
                    "prompt": whole_interval_prompt(spec["seconds"] * 1000, False, False),
                    "audio": "audio.wav", "frames": [{"offset_ms": i * 1000, "path": f"frame-{i:03d}.png"}
                                                         for i in range(spec["seconds"])]}
        require(bundle == expected, "Input manifest has unexpected paths or content")
        dest = f"cases/{run}"
        members[dest + "/input/bundle.json"] = raw
        members[dest + "/input/audio.wav"], audio_proof = validated_wav(read_file(root, base + "/input/audio.wav"), spec)
        for i in range(spec["seconds"]):
            name = f"frame-{i:03d}.png"
            members[dest + "/input/" + name] = safe_frame(read_file(root, base + "/input/" + name), i >= spec["seconds"] // 2)
        response = read_file(root, base + "/response-private.txt", 8192)
        metrics = safe_result(read_file(root, base + "/result-private.json", 16384), run, spec, response)
        members[dest + "/response.txt"] = response
        members[dest + "/metrics.json"] = json_bytes(metrics)
        portable = dict(expected, audio=dest + "/input/audio.wav",
                        frames=[dict(frame, path=dest + "/input/" + frame["path"]) for frame in expected["frames"]])
        members[dest + "/bundle-portable.json"] = json_bytes(portable)
        cases[run] = {"input_manifest": dest + "/bundle-portable.json", "metrics": dest + "/metrics.json",
                      "response": dest + "/response.txt", "response_sha256": digest(response),
                      "audio": audio_proof, "images": {"original_bytes_preserved": True, "decoded_pixels_verified": True}}
    manifest = {"schema_version": 1, "source_files": list(SOURCE_FILES), "source_file_count": len(SOURCE_FILES),
                "android_project_file_count": 8, "cases": cases,
                "input_kind": "Audio plus ordered time-captioned image list; native video capability is false.",
                "cause": "Unconfirmed; fixture export does not establish recognition quality or SDK root cause."}
    members["bundle-manifest.json"] = json_bytes(manifest)
    members["sha256-inventory.json"] = json_bytes({name: {"sha256": digest(data), "bytes": len(data)}
                                                  for name, data in sorted(members.items())})
    return members


def output_path(root, output):
    root = Path(root).absolute()
    path = Path(output)
    require(".." not in path.parts, "Output traversal refused")
    path = path.absolute() if path.is_absolute() else root / path
    no_links(path)
    require(path.resolve().is_relative_to((root / "work").resolve()) and path != root / "work",
            "Output must be a new directory below work")
    require(not path.exists() and path.parent.is_dir(), "Output exists or parent directory missing")
    ignored = subprocess.run(["git", "check-ignore", "--quiet", "--", str(path)], cwd=root, capture_output=True)
    require(ignored.returncode == 0, "Output is not Git-ignored")
    return path


def export(output=None, root=REPO):
    target = output_path(root, output) if output is not None else None
    members = build_export(root)
    if target is not None:
        target.mkdir()  # Exclusive creation; no existing artifacts are overwritten.
        for name, data in sorted(members.items()):
            destination = target / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("xb") as handle:
                handle.write(data)
    return {"written": target is not None, "files": sorted(members),
            "bytes": sum(map(len, members.values())), "file_count": len(members)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", help="Explicit new Git-ignored directory under work; absent means dry-run/list")
    args = parser.parse_args()
    try:
        print(json.dumps(export(args.output), indent=2))
    except (ValueError, OSError, EOFError, wave.Error) as error:
        # Never echo parsed media/result content or OS messages containing private paths.
        parser.exit(1, "Reproducer export refused: " + (str(error) if type(error) is ValueError else type(error).__name__) + "\n")


if __name__ == "__main__":
    main()
