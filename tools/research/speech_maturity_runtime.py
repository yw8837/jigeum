"""Research-only reconstruction of the pinned W2V2-LL4300-Pro-SM.

No downloads, training recipe, media decoder, product decision, or Android use.
The initial contract is ONE 9217-sample, mono 16kHz utterance per call. This
produces 28 convolution frames, already divisible by Fairseq's required two.
Configuration provenance: docs/research/20261004-speech-maturity-reconstruction.md.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import re

import torch
from torch import nn
from torch.nn import functional as F
from transformers import Wav2Vec2Config, Wav2Vec2Model


SAMPLE_RATE = 16000
INPUT_SAMPLES = 9217
FEATURE_FRAMES = 28
LABELS = ("Junk", "Non-Canonical", "Canonical", "Laughing", "Crying")
REVISION = "9aac676c2b29c42d580b862a0294bc3807788847"
CHECKPOINTS = {
    "head": ("CKPT+2025-02-08+21-43-25+00/model.ckpt", 3169378,
             "cbd674239f8f1f6b9fe7d7b4d604b8de9eea2ca3fa60633c1f84388a61d335e8"),
    "ll4300": ("CKPT+2025-02-08+21-43-25+00/wav2vec2.ckpt", 377566819,
               "f778e63ca45d2e7e632244762162eb2772107c0f0846511be8818a8dce8bbd0e"),
    "asr": ("wav2vec_asr.ckpt", 377572016,
            "f112f7506c754cd01d5c0b7163cc3b572e4edd4e6775277a600685ad03c7a540"),
}


def center_pad_utterance(samples: torch.Tensor) -> torch.Tensor:
    """Center pad one short utterance under this local research contract.

    The 70 ms lower bound is a conservative local restriction, not a proven
    training minimum. The author's inference recipe instead end-pads shorter
    signals to 70 ms. Segmentation and that alternative padding are external.
    """
    if samples.ndim != 1 or not 1120 <= samples.numel() <= INPUT_SAMPLES:
        raise ValueError("Expected one 70..576 ms utterance; segmenting is outside this wrapper")
    if samples.dtype != torch.float32 or not bool(torch.isfinite(samples).all()):
        raise ValueError("Expected finite float32 mono PCM")
    missing = INPUT_SAMPLES - samples.numel()
    return F.pad(samples, (missing // 2, missing - missing // 2))[None]


def canonical_encoder_key(key: str, branch: str) -> str:
    if branch not in ("ll4300", "asr") or not key.startswith("model."):
        raise ValueError("Unexpected branch or state key prefix")
    if branch == "ll4300":
        if key == "model.mask_emb":
            key = "model.masked_spec_embed"
        key = re.sub(r"^model\.feature_extractor\.conv_layers\.(\d+)\.0\.",
                     r"model.feature_extractor.conv_layers.\1.conv.", key)
        substitutions = (
            ("model.feature_extractor.conv_layers.0.2.", "model.feature_extractor.conv_layers.0.layer_norm."),
            ("model.post_extract_proj.", "model.feature_projection.projection."),
            ("model.layer_norm.", "model.feature_projection.layer_norm."),
            ("model.encoder.pos_conv.0.", "model.encoder.pos_conv_embed.conv."),
            (".self_attn_layer_norm.", ".layer_norm."),
            (".self_attn.", ".attention."),
            (".fc1.", ".feed_forward.intermediate_dense."),
            (".fc2.", ".feed_forward.output_dense."),
        )
        for source, target in substitutions:
            key = key.replace(source, target)
    return key.removeprefix("model.")


def strict_encoder_state(state: dict, module: nn.Module, branch: str) -> dict:
    """Map every key to the actual installed module; refuse silent omissions."""
    target = module.state_dict()
    mapped = {}
    for source_key, value in state.items():
        key = canonical_encoder_key(source_key, branch)
        if key not in target and key.endswith((".weight_g", ".weight_v")):
            stem, suffix = key.rsplit(".", 1)
            key = stem + ".parametrizations.weight." + ("original0" if suffix == "weight_g" else "original1")
        if key in mapped or key not in target or not isinstance(value, torch.Tensor):
            raise ValueError(f"Unexpected or duplicated {branch} state key: {source_key}")
        if value.shape != target[key].shape or value.dtype != target[key].dtype:
            raise ValueError(f"Shape/dtype mismatch: {source_key}")
        mapped[key] = value
    if set(mapped) != set(target):
        raise ValueError(f"Incomplete {branch} state: {sorted(set(target) - set(mapped))}")
    return mapped


def _encoder(branch: str) -> Wav2Vec2Model:
    # ASR values come from pinned facebook/wav2vec2-base config. LL4300 values
    # are independently confirmed in bootstrap cfg, never guessed from shapes.
    config = Wav2Vec2Config(
        hidden_size=768, num_hidden_layers=12, num_attention_heads=12,
        intermediate_size=3072, hidden_act="gelu", feat_extract_activation="gelu",
        conv_dim=(512,) * 7, conv_kernel=(10, 3, 3, 3, 3, 2, 2),
        conv_stride=(5, 2, 2, 2, 2, 2, 2), conv_bias=False,
        feat_extract_norm="group", do_stable_layer_norm=False, layer_norm_eps=1e-5,
        num_conv_pos_embeddings=128, num_conv_pos_embedding_groups=16,
        hidden_dropout=.1, attention_dropout=.1, activation_dropout=0.,
        feat_proj_dropout=.1, layerdrop=0., apply_spec_augment=False,
        mask_time_prob=.65 if branch == "ll4300" else .05,
        mask_feature_prob=0., add_adapter=False,
    )
    config._attn_implementation = "eager"
    return Wav2Vec2Model(config)


class _Linear(nn.Module):
    def __init__(self, inputs: int, outputs: int, bias: bool = True):
        super().__init__()
        self.w = nn.Linear(inputs, outputs, bias=bias)

    def forward(self, values):
        return self.w(values)


class _Norm(nn.Module):
    def __init__(self):
        super().__init__()
        self.norm = nn.BatchNorm1d(512, eps=1e-5, momentum=.1)

    def forward(self, values):
        return self.norm(values)


class _Dnn(nn.Module):
    def __init__(self):
        super().__init__()
        self.linear = _Linear(1536, 512)
        self.norm = _Norm()
        self.act = nn.LeakyReLU()
        self.dropout = nn.Dropout(.1)

    def forward(self, values):
        return self.dropout(self.act(self.norm(self.linear(values))))


def _statistics_mean(values: torch.Tensor, generator: torch.Generator) -> torch.Tensor:
    """Author mean/noise formula using a dedicated reproducible CPU RNG.

    This does not reproduce the author's global RNG stream byte-for-byte.
    """
    mean = values.mean(dim=1)
    noise = torch.randn(mean.shape, generator=generator, dtype=mean.dtype, device=mean.device)
    noise = noise - noise.min()
    denominator = noise.max()
    if not bool(denominator > 0):
        raise ValueError("Degenerate pooling noise")
    noise = 1e-5 * ((1 - 9) * (noise / denominator) + 9)
    return mean + noise


class SpeechMaturityReconstruction(nn.Module):
    def __init__(self):
        super().__init__()
        self.ll4300 = _encoder("ll4300")
        self.asr = _encoder("asr")
        self.head = nn.ModuleList([_Dnn(), _Linear(512, 5, bias=False), _Linear(12, 1, bias=False)])
        self._loaded = False

    @torch.inference_mode()
    def forward(self, waveform: torch.Tensor, *, pooling_seed: int = 2025) -> torch.Tensor:
        if not self._loaded or self.training:
            raise RuntimeError("Require fully loaded evaluation model")
        if waveform.shape != (1, INPUT_SAMPLES) or waveform.dtype != torch.float32 or waveform.device.type != "cpu":
            raise ValueError("Initial contract is CPU float32 [1,9217]; no dynamic/batched utterances")
        if not bool(torch.isfinite(waveform).all()):
            raise ValueError("Nonfinite waveform")
        generator = torch.Generator(device="cpu").manual_seed(pooling_seed)
        # LL4300 cfg.task.normalize is FALSE. Do not normalize this waveform.
        ll_result = self.ll4300(waveform, output_hidden_states=True, return_dict=True)
        if len(ll_result.hidden_states) != 13:
            raise ValueError("Unexpected layer output count")
        layers = torch.stack(ll_result.hidden_states[1:], dim=0)
        if layers.shape != (12, 1, FEATURE_FRAMES, 768):
            raise ValueError("Unexpected feature shape; Fairseq multiple-of-two contract not met")
        layers = F.layer_norm(layers, layers.shape, eps=1e-5)
        # ASR uses the pinned per-waveform normalization, unlike LL4300.
        asr_waveform = F.layer_norm(waveform, waveform.shape[1:], eps=1e-5)
        asr_hidden = self.asr(asr_waveform, return_dict=True).last_hidden_state
        if asr_hidden.shape != (1, FEATURE_FRAMES, 768):
            raise ValueError("Unexpected ASR feature shape")
        asr_hidden = F.layer_norm(asr_hidden, asr_hidden.shape[1:], eps=1e-5)
        # Match author's call order: ASR pool first, then LL layer pools 0..11.
        asr_pooled = _statistics_mean(asr_hidden, generator)
        layer_pooled = torch.stack([_statistics_mean(layer, generator) for layer in layers], dim=-1)
        layer_weights = F.softmax(self.head[2].w.weight, dim=1).squeeze(0)
        ll_pooled = torch.matmul(layer_pooled, layer_weights)
        combined = torch.cat((ll_pooled, asr_pooled), dim=1)
        logits = self.head[1](self.head[0](combined))
        if logits.shape != (1, 5) or not bool(torch.isfinite(logits).all()):
            raise ValueError("Invalid classifier output")
        return logits


def load_frozen_model(directory: str | Path) -> SpeechMaturityReconstruction:
    """Load only local pinned task states. Never load bootstrap pickle/cfg here."""
    directory = Path(directory).resolve()
    model = SpeechMaturityReconstruction()
    for role, (name, size, expected_sha) in CHECKPOINTS.items():
        path = (directory / name).resolve()
        if not path.is_relative_to(directory) or path.stat().st_size != size:
            raise ValueError(f"Invalid local checkpoint path/size: {role}")
        digest = hashlib.sha256()
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected_sha:
            raise ValueError(f"Checkpoint digest mismatch: {role}")
        state = torch.load(path, map_location="cpu", weights_only=True)
        module = model.head if role == "head" else getattr(model, role)
        mapped = state if role == "head" else strict_encoder_state(state, module, role)
        module.load_state_dict(mapped, strict=True)
        del state, mapped
    model.eval().requires_grad_(False)
    model._loaded = True
    return model
