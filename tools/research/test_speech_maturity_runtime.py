"""Focused offline synthetic gates. Execute only under a process-tree supervisor.

Example: python test_speech_maturity_runtime.py --weights LOCAL --output LOCAL.json
No raw media paths, decoder, network, training, or threshold decisions.
"""
import argparse
import json
import os
from pathlib import Path
import socket
import time


def blocked(*args, **kwargs):
    raise RuntimeError("Network prohibited during synthetic runtime verification")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    if output.exists():
        raise FileExistsError("Synthetic result already exists; do not rerun implicitly")
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", TORCH_FORCE_WEIGHTS_ONLY_LOAD="1")
    socket.socket.connect = socket.socket.connect_ex = socket.create_connection = socket.getaddrinfo = blocked
    import torch
    import transformers
    from speech_maturity_runtime import INPUT_SAMPLES, center_pad_utterance, load_frozen_model
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    torch.manual_seed(2025)
    report = dict(torch=torch.__version__, transformers=transformers.__version__, model="W2V2-LL4300-Pro-SM reconstruction",
                  input_kind="synthetic_only", media_read=False, android_validated=False, author_runtime_numerical_parity=False)
    # Center-pad values/length: no resampling, truncation, or amplitude adjustment.
    short = torch.full((8000,), .2, dtype=torch.float32)
    padded = center_pad_utterance(short)
    assert padded.shape == (1, INPUT_SAMPLES)
    assert torch.equal(padded[0, 608:8608], short)
    assert not bool(padded[0, :608].any()) and not bool(padded[0, 8608:].any())
    t = torch.arange(8000, dtype=torch.float32) / 16000
    waveform = center_pad_utterance(.2 * torch.sin(2 * torch.pi * (180 * t + 220 * t * t)))
    started = time.monotonic()
    model = load_frozen_model(args.weights)
    report['load_seconds'] = round(time.monotonic() - started, 6)
    report['strict_state_keys'] = dict(head=len(model.head.state_dict()), ll4300=len(model.ll4300.state_dict()), asr=len(model.asr.state_dict()))
    report['parameter_requires_grad'] = any(p.requires_grad for p in model.parameters())
    assert not report['parameter_requires_grad']
    elapsed = []
    outputs = []
    # The second identical-seed forward specifically checks eval/pooling determinism.
    for _ in range(2):
        started = time.monotonic()
        outputs.append(model(waveform, pooling_seed=2025))
        elapsed.append(round(time.monotonic() - started, 6))
    assert torch.equal(outputs[0], outputs[1]), "Same input/seed changed output"
    assert outputs[0].shape == (1, 5) and bool(torch.isfinite(outputs[0]).all())
    probs = torch.softmax(outputs[0], dim=-1)
    assert torch.allclose(probs.sum(dim=-1), torch.ones(1), atol=1e-6)
    invalid = [torch.zeros(2, INPUT_SAMPLES), torch.zeros(1, 160000), torch.full((1, INPUT_SAMPLES), float('nan'))]
    rejected = 0
    for values in invalid:
        try:
            model(values)
        except ValueError:
            rejected += 1
    assert rejected == len(invalid)
    report.update(status="passed", synthetic_forward_seconds=elapsed, logits_shape=list(outputs[0].shape),
                  finite=True, fixed_seed_exact_repeat=True, invalid_inputs_rejected=rejected,
                  product_quality_validated=False)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
