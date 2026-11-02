"""One synthetic 1-second feature forward; run under bounded Windows supervisor.

No raw audio/video, MFCC extraction, face detection, training, or quality claim.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import time


def blocked(*args, **kwargs):
    raise RuntimeError("Network prohibited during TalkNet probe")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--sources", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    if output.exists() or output.with_suffix(".started.json").exists():
        raise FileExistsError("Existing run evidence; do not rerun implicitly")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix(".started.json").write_text(json.dumps({"started": True, "synthetic_only": True}), encoding="utf-8")
    os.environ.update(TORCH_FORCE_WEIGHTS_ONLY_LOAD="1", HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1")
    socket.socket.connect = socket.socket.connect_ex = socket.create_connection = socket.getaddrinfo = blocked
    import torch
    from talknet_probe import EXPECTED_STATE_COUNTS, SOURCE_COMMIT, SOURCE_HASHES, load_frozen_probe, validate_features
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    torch.manual_seed(2025)
    # These are synthetic FEATURES, not valid physical MFCC or a rendered face.
    audio_time = torch.arange(100, dtype=torch.float32)[:, None]
    coefficients = torch.arange(13, dtype=torch.float32)[None, :]
    audio = (3 * torch.sin(audio_time * .13 + coefficients * .21))[None].contiguous()
    yy = torch.arange(112, dtype=torch.float32)[:, None]
    xx = torch.arange(112, dtype=torch.float32)[None, :]
    video_time = torch.arange(25, dtype=torch.float32)[:, None, None]
    visual = (127.5 + 60 * torch.sin(xx * .06 + video_time * .2) + 30 * torch.cos(yy * .07))[None].contiguous()
    assert validate_features(audio, visual) == 25
    rejected = []
    cases = [
        ("audio_video_misaligned", audio[:, :-1].contiguous(), visual, 0.),
        ("batch2", audio.repeat(2, 1, 1), visual.repeat(2, 1, 1, 1), 0.),
        ("wrong_crop224", audio, torch.zeros(1, 25, 224, 224), 0.),
        ("empty_track", torch.zeros(1, 0, 13), torch.zeros(1, 0, 112, 112), 0.),
        ("longer_than20sec", torch.zeros(1, 2004, 13), torch.zeros(1, 501, 112, 112), 0.),
        ("audio_nan", torch.full_like(audio, float("nan")), visual, 0.),
        ("visual_infinity", audio, torch.full_like(visual, float("inf")), 0.),
        ("visual_out_of_range", audio, torch.full_like(visual, 256), 0.),
        ("wrong_dtype", audio.double(), visual, 0.),
        ("negative_origin", audio, visual, -1.),
    ]
    for name, a, v, start in cases:
        try:
            validate_features(a, v, start)
        except ValueError:
            rejected.append(name)
        else:
            raise AssertionError("Invalid input accepted: " + name)
    del cases
    started = time.monotonic()
    model = load_frozen_probe(args.checkpoint, args.sources)
    load_seconds = time.monotonic() - started
    counts = {group: sum(key.startswith(group + ".") for key in model.state_dict()) for group in EXPECTED_STATE_COUNTS}
    assert counts == EXPECTED_STATE_COUNTS
    assert not any(parameter.requires_grad for parameter in model.parameters())
    started = time.monotonic()
    result = model(audio, visual, start_seconds=0.)
    forward_seconds = time.monotonic() - started
    assert result.av_logits.shape == (25, 2) and result.active_speaker_logit.shape == (25,)
    assert bool(torch.isfinite(result.av_logits).all())
    assert torch.equal(result.active_speaker_logit, result.av_logits[:, 1])
    assert torch.equal(result.nominal_frame_times_seconds, torch.arange(25, dtype=torch.float64) / 25)
    # Do not interpret a sign, threshold, probability, or synthetic label.
    report = dict(status="passed", source_commit=SOURCE_COMMIT, official_source_hashes=SOURCE_HASHES,
                  torch=torch.__version__, strict_state_keys=counts, input_kind="synthetic_features_only",
                  input_shapes={"audio": list(audio.shape), "visual": list(visual.shape)},
                  intermediate_shapes=result.intermediate_shapes, output_shape=list(result.av_logits.shape),
                  output_dtype=str(result.av_logits.dtype), output_finite=True,
                  official_class1_raw_logit_verified=True, nominal_25fps_times_verified=True,
                  rejected_inputs=rejected, all_parameters_frozen=True, load_seconds=load_seconds,
                  forward_seconds=forward_seconds, inference_calls=1, private_media_read=False,
                  raw_media_read=False, mfcc_preprocessing_validated=False, face_tracking_validated=False,
                  silence_shift_quality_validated=False, infant_domain_validated=False,
                  author_torch16_numerical_parity=False, android_validated=False,
                  product_quality_validated=False, twenty_second_resource_limit_validated=False,
                  probe_source_sha256=hashlib.sha256(Path(__file__).with_name("talknet_probe.py").read_bytes()).hexdigest())
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
