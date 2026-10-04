"""Offline, research-only paired TalkNet score sensitivity on a prepared track.

No detection, download, class threshold, infant label or product activation.
Run under tools/research/bounded_process.py; outputs contain private observations.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import sys
import time

MFCC_HASHES = {
    "__init__.py": "1119a63b1cfff6652422e71c35bcd46b963a8152cb543772738702c5b08251b6",
    "base.py": "5da9dd3a98ebf697d5ac0dec1cc79e605f6df4874d1aa327096acf3002aaabd1",
    "sigproc.py": "f4769a1c7b01c1d42d11549e2c5db4a84159e755d61a2f72c59afdbaad0b1352",
}
CONDITIONS = ("baseline", "silence_pcm", "audio_advance_500ms", "audio_delay_500ms")


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def block_network(*args, **kwargs):
    raise RuntimeError("External access prohibited during local AV comparison")


def main():
    parser = argparse.ArgumentParser()
    for name in ("bundle", "vendor", "checkpoint", "sources", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(exist_ok=False)
    os.environ["TORCH_FORCE_WEIGHTS_ONLY_LOAD"] = "1"
    socket.socket.connect = socket.socket.connect_ex = socket.create_connection = socket.getaddrinfo = block_network
    for relative, expected in MFCC_HASHES.items():
        if sha(args.vendor / "python_speech_features" / relative) != expected:
            raise ValueError("MFCC source integrity mismatch")
    if any(n == "python_speech_features" or n.startswith("python_speech_features.") for n in sys.modules):
        raise RuntimeError("MFCC import collision")
    sys.path.insert(0, str(args.vendor.resolve()))
    import numpy as np
    from scipy.io import wavfile
    import python_speech_features as psf
    import torch
    from talknet_probe import load_frozen_probe
    if not Path(psf.__file__).resolve().is_relative_to(args.vendor.resolve()):
        raise ValueError("Unexpected MFCC module origin")
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    torch.manual_seed(2025)

    bundle = args.bundle
    face = json.loads((bundle / "face-report.json").read_text("utf-8"))
    media = json.loads((bundle / "media-report.json").read_text("utf-8"))
    contract = json.loads((bundle / "contract.json").read_text("utf-8"))
    review = json.loads((bundle / "crop-review.json").read_text("utf-8"))
    timing = json.loads((bundle / "time-alignment-report.json").read_text("utf-8"))
    if (not timing["checksum_mapped"] or timing["max_resample_pts_difference_seconds"] >= .04
            or timing["audio_stream_start_seconds"] != timing["video_stream_start_seconds"]
            or timing["audio_stream_start_seconds"] != 0):
        raise ValueError("Source audio/video timing not supported by this prepared-bundle contract")
    if not review["mouth_in_sampled_crops"] or not review["single_continuous_face"]:
        raise ValueError("Prepared crop review is not suitable for this sensitivity probe")
    if contract["conditions"] != list(CONDITIONS) or contract["fixed_shift_samples"] != 8000:
        raise ValueError("Comparison conditions differ from prepared contract")
    if sha(bundle / "visual-crops.npy") != face["visual_sha256"] or sha(bundle / "audio.wav") != media["audio_sha256"]:
        raise ValueError("Prepared input integrity mismatch")
    visual = np.load(bundle / "visual-crops.npy", allow_pickle=False)
    frame_ids = np.load(bundle / "face-frames.npy", allow_pickle=False)
    sample_times = json.loads((bundle / "sample-frame-times.json").read_text("utf-8"))
    if visual.dtype != np.float32 or visual.shape != (len(frame_ids), 112, 112):
        raise ValueError("Unexpected prepared visual shape/dtype")
    if frame_ids.ndim != 1 or not np.issubdtype(frame_ids.dtype, np.integer) or len(frame_ids) < 75 or len(frame_ids) > 500:
        raise ValueError("Need one continuous 3..20-second face track")
    if frame_ids[0] < 0 or frame_ids[-1] >= len(sample_times) or not np.all(np.diff(frame_ids) == 1):
        raise ValueError("Non-contiguous or invalid source frame mapping")
    if (int(frame_ids[0]) != face["selected_first"] or int(frame_ids[-1]) != face["selected_last"]
            or len(frame_ids) != face["selected_frames"]):
        raise ValueError("Frame mapping disagrees with the prepared visual track")
    if any(abs(sample_times[int(i)]["seconds"]-int(i)/25) > 1e-6 for i in frame_ids):
        raise ValueError("Sample frame times do not match the prepared 25fps mapping")
    sr, full_pcm = wavfile.read(bundle / "audio.wav")
    if sr != 16000 or full_pcm.ndim != 1 or full_pcm.dtype != np.int16:
        raise ValueError("Expected mono PCM16 at 16kHz, no implicit gain/filter")
    start_sample = int(frame_ids[0]) * 640
    end_sample = (int(frame_ids[-1]) + 1) * 640
    if end_sample > len(full_pcm):
        raise ValueError("Audio does not cover the face track")
    pcm = full_pcm[start_sample:end_sample].copy()
    advance = np.zeros_like(pcm)
    delay = np.zeros_like(pcm)
    advance[:-8000] = pcm[8000:]
    delay[8000:] = pcm[:-8000]
    variants = dict(zip(CONDITIONS, (pcm, np.zeros_like(pcm), advance, delay)))
    prepared = {}
    shapes = {}
    for name, samples in variants.items():
        features = psf.mfcc(samples, 16000, numcep=13, winlen=.025, winstep=.010)
        if features.shape[1] != 13 or not np.isfinite(features).all():
            raise ValueError("Invalid MFCC features")
        shapes[name] = list(features.shape)
        prepared[name] = features
    if len({tuple(s) for s in shapes.values()}) != 1:
        raise ValueError("Condition feature lengths disagree")
    raw_mfcc_frames = len(prepared["baseline"])
    frames = min(raw_mfcc_frames // 4, len(visual))
    visual_tensor = torch.from_numpy(np.ascontiguousarray(visual[:frames][None]))
    local_times = np.arange(frames, dtype=np.float64) / 25
    # Excludes direct +/-500ms zero-fill and four MFCC windows' 55ms support.
    # Global pooling/attention still lets boundary conditions affect central outputs.
    common_mask = (local_times >= .56) & (local_times + .56 <= len(pcm)/16000)
    if not common_mask.any():
        raise ValueError("No shared central comparison support")
    execution_contract = dict(conditions=list(CONDITIONS), shift_samples=8000,
                              edge_exclusion_seconds=.56, common_frame_indices=np.flatnonzero(common_mask).tolist(),
                              track_first_frame=int(frame_ids[0]), input_video_frames=len(visual),
                              raw_mfcc_shapes=shapes, trimmed_mfcc_frames=frames*4, model_video_frames=frames,
                              trailing_video_frames_omitted=len(visual)-frames,
                              window_policy="one_full_track_window_not_demo_ensemble", event_labels_available=False,
                              criterion="descriptive_paired_raw_logit_differences_no_threshold_or_accuracy",
                              contextual_boundary_effects_excluded=False,
                              provenance_sha256={name:sha(bundle/name) for name in (
                                  "face-frames.npy", "sample-frame-times.json", "time-alignment-report.json",
                                  "source-pts-map-private.json", "face-report.json", "contract.json", "crop-review.json")},
                              visual_sha256=face["visual_sha256"], pcm_sha256=hashlib.sha256(pcm.tobytes()).hexdigest(),
                              all_prepared_before_inference=True, source_script_sha256=sha(Path(__file__)))
    (args.output / "execution-contract-private.json").write_text(json.dumps(execution_contract, indent=2), encoding="utf-8")
    started = time.monotonic()
    model = load_frozen_probe(args.checkpoint, args.sources)
    load_seconds = time.monotonic()-started
    scores = {}
    timings = {}
    for name in CONDITIONS:
        audio_tensor = torch.from_numpy(np.ascontiguousarray(prepared[name][:4*frames][None], dtype=np.float32))
        started = time.monotonic()
        result = model(audio_tensor, visual_tensor, start_seconds=int(frame_ids[0])/25)
        timings[name] = time.monotonic()-started
        scores[name] = result.active_speaker_logit.numpy().copy()
        np.savez(args.output / (name + "-private.npz"), logits=result.av_logits.numpy(),
                 nominal_times=result.nominal_frame_times_seconds.numpy(), common_mask=common_mask)
        print(json.dumps(dict(condition=name, forward_completed=True, seconds=timings[name])), flush=True)
    baseline = scores["baseline"][common_mask]
    paired = {}
    for name in CONDITIONS[1:]:
        delta = baseline-scores[name][common_mask]
        paired[name] = dict(mean_delta=float(np.mean(delta)), median_delta=float(np.median(delta)),
                            min_delta=float(np.min(delta)), max_delta=float(np.max(delta)),
                            baseline_higher_frame_count=int(np.count_nonzero(delta>0)), frames=len(delta))
    summary = dict(status="executed_not_quality_approved", load_seconds=load_seconds, forward_seconds=timings,
                   model_calls=4, aligned_video_frames=frames, common_frame_count=int(common_mask.sum()),
                   condition_central_mean={name:float(values[common_mask].mean()) for name,values in scores.items()},
                   paired=paired, event_labels_available=False, infant_speaker_accuracy_validated=False,
                   official_demo_ensemble=False, android_validated=False, product_change=False,
                   private_results=True, external_media_transfer=False)
    (args.output / "comparison-private.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(dict(status=summary["status"], calls=4, aligned_video_frames=frames,
                         common_frames=int(common_mask.sum()), private_results_saved=True)))


if __name__ == "__main__":
    main()
