"""Research-only CPU TalkNet-ASD probe using byte-exact official modules.

No decoder, face detector, network, training, product rule, or label inference.
Inputs are already-aligned MFCC and grayscale center crops, not raw media.
Execute imports/model loading/forward only under the bounded process supervisor.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import importlib.util
import math
from pathlib import Path
import sys
import threading
from types import ModuleType

import torch
from torch import nn


SOURCE_COMMIT = "6d6821479af485e251c4991487e40573b42181b4"
CHECKPOINT_BYTES = 63_227_827
CHECKPOINT_SHA256 = "d985ddd07dca08864a28337726f5b7d91b6426bebc7327f0e9b21cf9ff1cc937"
SOURCE_HASHES = {
    "model/audioEncoder.py": "d4334100ba4e3921dc2011eb56e4d0eb1fd8b69cce16235cb14c47a05d89c6c3",
    "model/visualEncoder.py": "5a33ee41194666eeccb48590f72ea1e59b52f40e19dca31390dd7b67d9f22d15",
    "model/attentionLayer.py": "5c0451f2dfd5a52c086ba3e5957d57987962ea979ccfae42360be2b2668aeaa7",
    "model/talkNetModel.py": "df788d96833f67178821f31abdb789fd3aa2a5144fd258d5437c7fbc43a69611",
    "loss.py": "9824ebff5c9b318de2ac73164e2ead78bb858408113a5b1ae2a923416af278b0",
}
VIDEO_FPS = 25
AUDIO_FEATURE_HZ = 100
MFCC_DIMENSIONS = 13
FACE_SIDE = 112
MIN_VIDEO_FRAMES = 1  # Short aligned final chunks are permitted; only T=25 is initially tested.
MAX_VIDEO_FRAMES = 500  # Contract cap, not a measured 20-second RAM/performance pass.
EXPECTED_STATE_COUNTS = {"model": 481, "lossAV": 2, "lossA": 2, "lossV": 2}
_IMPORT_LOCK = threading.RLock()


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _official_classes(source_directory: str | Path):
    """Load untouched source while containing its absolute `model.*` imports.

    Only the five hash-pinned files execute. The CUDA/training talkNet.py entry
    point is never imported. Temporary import names are removed on all exits.
    This loader is for an isolated single-purpose research subprocess.
    """
    source_directory = Path(source_directory).resolve()
    for relative, expected in SOURCE_HASHES.items():
        path = (source_directory / relative).resolve()
        if not path.is_relative_to(source_directory) or _sha256(path) != expected:
            raise ValueError(f"Official source integrity mismatch: {relative}")
    with _IMPORT_LOCK:
        if any(name == "model" or name.startswith("model.") for name in sys.modules):
            raise RuntimeError("Run in a fresh subprocess: existing model package would collide")
        package = ModuleType("model")
        # Empty path prevents unlisted modules being loaded from a mutable folder.
        package.__path__ = []
        sys.modules["model"] = package
        inserted = ["model"]
        loaded = {}
        try:
            for relative in SOURCE_HASHES:
                name = relative.removesuffix(".py").replace("/", ".")
                if name == "loss":
                    name = "_talknet_probe_official_loss"
                if name in sys.modules:
                    raise RuntimeError(f"Unexpected module import collision: {name}")
                spec = importlib.util.spec_from_file_location(name, source_directory / relative)
                if spec is None or spec.loader is None:
                    raise RuntimeError(f"Cannot load official module: {relative}")
                module = importlib.util.module_from_spec(spec)
                sys.modules[name] = module
                inserted.append(name)
                spec.loader.exec_module(module)
                loaded[relative] = module
            network = loaded["model/talkNetModel.py"].talkNetModel
            losses = loaded["loss.py"]
            return network, losses.lossAV, losses.lossA, losses.lossV
        finally:
            for name in reversed(inserted):
                sys.modules.pop(name, None)


def validate_features(audio: torch.Tensor, visual: torch.Tensor, start_seconds: float = 0.0) -> int:
    """Reject misalignment; never truncate, resample, normalize, or pad silently."""
    if not isinstance(audio, torch.Tensor) or not isinstance(visual, torch.Tensor):
        raise ValueError("Expected torch feature tensors")
    if audio.dtype != torch.float32 or visual.dtype != torch.float32:
        raise ValueError("Expected float32 MFCC and unnormalized grayscale pixels")
    if audio.device.type != "cpu" or visual.device.type != "cpu":
        raise ValueError("CPU-only probe")
    if visual.ndim != 4 or visual.shape[0] != 1 or visual.shape[2:] != (FACE_SIDE, FACE_SIDE):
        raise ValueError("Expected visual [1,T,112,112]; one face track per call")
    frames = visual.shape[1]
    if not MIN_VIDEO_FRAMES <= frames <= MAX_VIDEO_FRAMES:
        raise ValueError("Expected 1..500 aligned video frames, at most 20 nominal seconds")
    if audio.shape != (1, 4 * frames, MFCC_DIMENSIONS):
        raise ValueError("Require exactly four 13-dimensional MFCC frames per video frame")
    if not audio.is_contiguous() or not visual.is_contiguous():
        raise ValueError("Inputs must be contiguous; prepare copies explicitly upstream")
    if not bool(torch.isfinite(audio).all()) or not bool(torch.isfinite(visual).all()):
        raise ValueError("Nonfinite input")
    if bool((visual < 0).any()) or bool((visual > 255).any()):
        raise ValueError("Visual pixels must be in [0,255]; normalization is inside official model")
    if isinstance(start_seconds, bool) or not isinstance(start_seconds, (int, float)):
        raise ValueError("Expected numeric track start in seconds")
    if not math.isfinite(start_seconds) or start_seconds < 0:
        raise ValueError("Track start must be finite and nonnegative")
    return frames


@dataclass(frozen=True)
class TalkNetOutput:
    av_logits: torch.Tensor                 # [T,2], official FC output
    active_speaker_logit: torch.Tensor      # [T], official labels=None class-1 path
    nominal_frame_times_seconds: torch.Tensor  # [T], NOT source PTS proof
    intermediate_shapes: dict[str, list[int]]


class TalkNetProbe(nn.Module):
    """CPU container preserves every official checkpoint prefix, including A/V heads."""

    def __init__(self, source_directory: str | Path):
        super().__init__()
        network, av_head, audio_head, visual_head = _official_classes(source_directory)
        self.model = network()
        self.lossAV = av_head()
        self.lossA = audio_head()
        self.lossV = visual_head()
        self._fully_loaded = False

    @torch.inference_mode()
    def forward(self, audio: torch.Tensor, visual: torch.Tensor, *, start_seconds: float = 0.0) -> TalkNetOutput:
        frames = validate_features(audio, visual, start_seconds)
        if not self._fully_loaded or any(module.training for module in self.modules()):
            raise RuntimeError("Require strict-loaded evaluation model")
        if any(parameter.requires_grad or parameter.device.type != "cpu" for parameter in self.parameters()):
            raise RuntimeError("Require frozen CPU parameters")
        audio_embed = self.model.forward_audio_frontend(audio)
        visual_embed = self.model.forward_visual_frontend(visual)
        expected_embed = (1, frames, 128)
        if audio_embed.shape != expected_embed or visual_embed.shape != expected_embed:
            raise ValueError("Encoder time axes failed to align")
        shapes = {"audio_frontend": list(audio_embed.shape), "visual_frontend": list(visual_embed.shape)}
        audio_embed, visual_embed = self.model.forward_cross_attention(audio_embed, visual_embed)
        if audio_embed.shape != expected_embed or visual_embed.shape != expected_embed:
            raise ValueError("Cross-attention changed expected time axes")
        shapes.update(audio_cross=list(audio_embed.shape), visual_cross=list(visual_embed.shape))
        features = self.model.forward_audio_visual_backend(audio_embed, visual_embed)
        if features.shape != (frames, 256) or not bool(torch.isfinite(features).all()):
            raise ValueError("Invalid audiovisual backend output")
        shapes["av_backend"] = list(features.shape)
        captured = []
        handle = self.lossAV.FC.register_forward_hook(lambda module, inputs, output: captured.append(output.detach().clone()))
        try:
            # Execute the original no-label path, including its numpy return.
            raw_score = self.lossAV(features, labels=None)
        finally:
            handle.remove()
        if len(captured) != 1:
            raise ValueError("Expected exactly one official AV classification head call")
        logits = captured[0]
        score = torch.from_numpy(raw_score.copy())
        if logits.shape != (frames, 2) or score.shape != (frames,) or not bool(torch.isfinite(logits).all()):
            raise ValueError("Invalid raw logits")
        if logits.dtype != torch.float32 or score.dtype != torch.float32 or not torch.equal(score, logits[:, 1]):
            raise ValueError("Official labels=None score disagrees with raw class-1 logit")
        times = start_seconds + torch.arange(frames, dtype=torch.float64) / VIDEO_FPS
        return TalkNetOutput(logits, score, times, shapes)


def load_frozen_probe(checkpoint_path: str | Path, source_directory: str | Path) -> TalkNetProbe:
    """Restore all 487 local state tensors; no downloads or permissive pickle."""
    checkpoint_path = Path(checkpoint_path).resolve()
    if checkpoint_path.stat().st_size != CHECKPOINT_BYTES or _sha256(checkpoint_path) != CHECKPOINT_SHA256:
        raise ValueError("Pinned local TalkSet checkpoint size/SHA mismatch")
    model = TalkNetProbe(source_directory)
    state = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    if not isinstance(state, dict):
        raise ValueError("Expected tensor state dictionary")
    expected = model.state_dict()
    if set(state) != set(expected) or len(state) != 487:
        raise ValueError("Incomplete or unexpected TalkNet state key set")
    counts = dict.fromkeys(EXPECTED_STATE_COUNTS, 0)
    for key, value in state.items():
        if not isinstance(value, torch.Tensor) or value.shape != expected[key].shape or value.dtype != expected[key].dtype:
            raise ValueError(f"State tensor shape/dtype mismatch: {key}")
        if value.device.type != "cpu" or not bool(torch.isfinite(value).all()):
            raise ValueError(f"Nonfinite/non-CPU state tensor: {key}")
        counts[key.split(".", 1)[0]] += 1
    if counts != EXPECTED_STATE_COUNTS:
        raise ValueError("Incomplete encoder/head state groups")
    model.load_state_dict(state, strict=True)
    del state, expected
    model.eval().requires_grad_(False)
    model._fully_loaded = True
    return model
