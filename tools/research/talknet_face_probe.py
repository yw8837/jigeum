"""Bounded CPU S3FD reference for TalkNet research; no media decoder/download.

Uses hash-pinned official nets.py and box_utils.py without editing their files.
The original detect_faces method is extracted without importing its download,
torchvision or CUDA initialization. Its only body patch bounds the top-k loop.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import socket
import sys
import threading
import time
from types import ModuleType, MethodType

import cv2
import numpy as np
import torch
from torch import nn


SOURCE_COMMIT = "6d6821479af485e251c4991487e40573b42181b4"
SOURCE_HASHES = {
    "__init__.py": "7ff5971b3a11af0d801760d02a317089a8b83697efdda3983ddf09232c438134",
    "box_utils.py": "e4864183274435767739e6521eef41b80bc743f6bef8962d21bbb0030979eefc",
    "nets.py": "3e9dad22a2f1234014b46e4f96f46d5ff578d524eb332d063a69a476924f0ee5",
}
CHECKPOINT_BYTES = 89_844_381
CHECKPOINT_SHA256 = "d54a87c2b7543b64729c9a25eafd188da15fd3f6e02f0ecec76ae1b30d86c491"
STATE_TENSORS = 65
DETECTION_SCALE = 0.25
DETECTION_CONFIDENCE = 0.9
MAX_PIXELS = 3840 * 2160
MAX_SIDE = 4096
MIN_SIDE = 128
_IMPORT_LOCK = threading.RLock()


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class _LegacyNumpyNames:
    """Only box_utils' local numpy reference gets the removed np.int alias.

    np.int historically meant built-in int. No process-global numpy mutation
    or NMS math changes are made. Other attributes delegate to actual NumPy.
    """

    def __getattr__(self, name):
        return int if name == "int" else getattr(np, name)


def _official_components(source_directory: str | Path):
    source_directory = Path(source_directory).resolve()
    for relative, expected in SOURCE_HASHES.items():
        path = (source_directory / relative).resolve()
        if not path.is_relative_to(source_directory) or _sha256(path) != expected:
            raise ValueError(f"S3FD source integrity mismatch: {relative}")
    package_name = "_talknet_probe_s3fd_official"
    with _IMPORT_LOCK:
        if any(name == package_name or name.startswith(package_name + ".") for name in sys.modules):
            raise RuntimeError("Use a fresh research subprocess; S3FD import collision")
        package = ModuleType(package_name)
        package.__path__ = []
        sys.modules[package_name] = package
        inserted = [package_name]
        modules = {}
        try:
            for relative in ("box_utils.py", "nets.py"):
                name = package_name + "." + relative.removesuffix(".py")
                spec = importlib.util.spec_from_file_location(name, source_directory / relative)
                if spec is None or spec.loader is None:
                    raise RuntimeError(f"Cannot import official S3FD file: {relative}")
                module = importlib.util.module_from_spec(spec)
                sys.modules[name] = module
                inserted.append(name)
                spec.loader.exec_module(module)
                modules[relative] = module
            box = modules["box_utils.py"]
            box.np = _LegacyNumpyNames()
            # Extract a single method; never execute __init__.py top-level code.
            source_path = source_directory / "__init__.py"
            tree = ast.parse(source_path.read_text(encoding="utf-8"))
            klass = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == "S3FD")
            method = next(node for node in klass.body if isinstance(node, ast.FunctionDef) and node.name == "detect_faces")
            loops = [node for node in ast.walk(method) if isinstance(node, ast.While)]
            if len(loops) != 1 or ast.unparse(loops[0].test) != "detections[0, i, j, 0] > conf_th":
                raise ValueError("Official detection loop changed")
            # Original while overruns at j==750 if every available score passes.
            # This bound changes no retained score/box on the defined index set.
            loops[0].test = ast.BoolOp(op=ast.And(), values=[
                ast.parse("j < detections.size(2)", mode="eval").body, loops[0].test,
            ])
            extracted = ast.fix_missing_locations(ast.Module(body=[method], type_ignores=[]))
            scope = {"np": np, "cv2": cv2, "torch": torch, "nms_": box.nms_,
                     "img_mean": np.array([104., 117., 123.], dtype=np.float32)[:, None, None]}
            exec(compile(extracted, str(source_path), "exec"), scope)
            return modules["nets.py"].S3FDNet, scope["detect_faces"], box
        finally:
            for name in reversed(inserted):
                sys.modules.pop(name, None)


def validate_rgb_frame(frame: np.ndarray) -> None:
    """RGB uint8 frame as passed by the official demo after BGR→RGB conversion."""
    if not isinstance(frame, np.ndarray) or frame.ndim != 3 or frame.shape[2] != 3:
        raise ValueError("Expected one HWC RGB frame")
    if frame.dtype != np.uint8 or not frame.flags.c_contiguous:
        raise ValueError("Expected contiguous RGB uint8; no implicit channel/dtype conversion")
    height, width = frame.shape[:2]
    if min(height, width) < MIN_SIDE or max(height, width) > MAX_SIDE or height * width > MAX_PIXELS:
        raise ValueError("Frame outside bounded 128-min-side / 4096-max-side / 4K-pixel contract")


class S3FDFaceProbe(nn.Module):
    def __init__(self, source_directory: str | Path):
        super().__init__()
        network, detect_method, box = _official_components(source_directory)
        self.net = network(device="cpu")
        self.device = "cpu"
        self._official_detect = MethodType(detect_method, self)
        self._box_utils = box
        self._fully_loaded = False

    @torch.inference_mode()
    def detect_faces(self, rgb_frame: np.ndarray) -> np.ndarray:
        validate_rgb_frame(rgb_frame)
        if not self._fully_loaded or any(module.training for module in self.modules()):
            raise RuntimeError("Require strict-loaded evaluation detector")
        if any(parameter.requires_grad or parameter.device.type != "cpu" for parameter in self.parameters()):
            raise RuntimeError("Require frozen CPU detector")
        raw_shapes = []

        def check_detector_output(module, inputs, output):
            if output.shape != (1, 2, 750, 5) or output.dtype != torch.float32 or not bool(torch.isfinite(output).all()):
                raise ValueError("Unexpected/nonfinite official detection tensor")
            raw_shapes.append(tuple(output.shape))

        handle = self.net.register_forward_hook(check_detector_output)
        try:
            boxes = self._official_detect(rgb_frame, conf_th=DETECTION_CONFIDENCE, scales=[DETECTION_SCALE])
        finally:
            handle.remove()
        if raw_shapes != [(1, 2, 750, 5)]:
            raise ValueError("Require exactly one fixed-scale detector forward")
        if boxes.ndim != 2 or boxes.shape[1] != 5 or len(boxes) > 750 or not np.isfinite(boxes).all():
            raise ValueError("Invalid finite [N,5] face boxes")
        if len(boxes) and (not (boxes[:, 4] > DETECTION_CONFIDENCE).all() or (boxes[:, 4] > 1).any()):
            raise ValueError("Detector confidence outside fixed official selection range")
        # Keep original coordinates, including any out-of-frame border extent.
        return boxes


def load_frozen_face_probe(checkpoint_path: str | Path, source_directory: str | Path) -> S3FDFaceProbe:
    checkpoint_path = Path(checkpoint_path).resolve()
    if checkpoint_path.stat().st_size != CHECKPOINT_BYTES or _sha256(checkpoint_path) != CHECKPOINT_SHA256:
        raise ValueError("Pinned S3FD checkpoint size/SHA mismatch")
    model = S3FDFaceProbe(source_directory)
    state = torch.load(checkpoint_path, weights_only=True, map_location="cpu")
    if not isinstance(state, dict):
        raise ValueError("Expected tensor state dictionary")
    expected = model.net.state_dict()
    if set(state) != set(expected) or len(state) != STATE_TENSORS:
        raise ValueError("S3FD state key set/count mismatch")
    for key, value in state.items():
        if not isinstance(value, torch.Tensor) or value.shape != expected[key].shape or value.dtype != expected[key].dtype:
            raise ValueError(f"S3FD state shape/dtype mismatch: {key}")
        if value.device.type != "cpu" or not bool(torch.isfinite(value).all()):
            raise ValueError(f"Nonfinite/non-CPU S3FD state: {key}")
    model.net.load_state_dict(state, strict=True)
    del state, expected
    model.eval().requires_grad_(False)
    model._fully_loaded = True
    return model


def _blocked(*args, **kwargs):
    raise RuntimeError("Network prohibited during S3FD synthetic probe")


def main():
    """One blank-frame synthetic forward. Invoke only under bounded supervisor."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--sources", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    marker = output.with_suffix(".started.json")
    if output.exists() or marker.exists():
        raise FileExistsError("Existing S3FD run evidence; no implicit repeat")
    output.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(json.dumps({"started": True, "synthetic_only": True}), encoding="utf-8")
    os.environ["TORCH_FORCE_WEIGHTS_ONLY_LOAD"] = "1"
    socket.socket.connect = socket.socket.connect_ex = socket.create_connection = socket.getaddrinfo = _blocked
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    torch.manual_seed(2025)
    blank = np.zeros((720, 1280, 3), dtype=np.uint8)
    invalid = [np.zeros((128, 128)), np.zeros((128, 128, 3), dtype=np.float32),
               np.zeros((127, 128, 3), dtype=np.uint8), np.full((128, 128, 3), np.nan)]
    rejected = 0
    for item in invalid:
        try:
            validate_rgb_frame(item)
        except ValueError:
            rejected += 1
    if rejected != len(invalid):
        raise AssertionError("Invalid RGB input accepted")
    started = time.monotonic()
    model = load_frozen_face_probe(args.checkpoint, args.sources)
    load_seconds = time.monotonic() - started
    # Official tensor NMS already returns (keep,0) for empty input. No fix needed.
    empty_keep, empty_count = model._box_utils.nms(torch.empty(0, 4), torch.empty(0))
    assert empty_count == 0 and empty_keep.shape == (0,)
    numpy_keep = model._box_utils.nms_(np.empty((0, 5)), .1)
    assert numpy_keep.shape == (0,) and np.issubdtype(numpy_keep.dtype, np.integer)
    # Exercise the bounded extraction loop with no network execution.
    saved_net = model.net
    class AllTopK(nn.Module):
        def forward(self, tensor):
            rows = torch.zeros(1, 2, 750, 5)
            rows[0, 1, :, 0] = .95
            rows[0, 1, :, 1:] = torch.tensor([.1, .1, .2, .2])
            return rows
    model.net = AllTopK().eval()
    try:
        edge_boxes = model._official_detect(blank, conf_th=DETECTION_CONFIDENCE, scales=[DETECTION_SCALE])
        assert edge_boxes.shape == (1, 5) and np.isfinite(edge_boxes).all()
    finally:
        model.net = saved_net
    started = time.monotonic()
    boxes = model.detect_faces(blank)
    forward_seconds = time.monotonic() - started
    report = dict(status="passed", source_commit=SOURCE_COMMIT, official_source_hashes=SOURCE_HASHES,
                  torch=torch.__version__, numpy=np.__version__, opencv=cv2.__version__,
                  checkpoint_sha256=CHECKPOINT_SHA256, strict_state_tensors=len(model.net.state_dict()),
                  frame_shape=list(blank.shape), frame_kind="synthetic_blank_RGB_uint8", scale=DETECTION_SCALE,
                  confidence=DETECTION_CONFIDENCE, output_shape=list(boxes.shape), finite=True,
                  empty_tensor_nms_verified=True, empty_numpy_nms_verified=True, full_topk_guard_verified=True,
                  compatibility_changes=["box_utils-local np.int aliases builtin int", "bound original score extraction by top_k"],
                  invalid_inputs_rejected=rejected, load_seconds=load_seconds, forward_seconds=forward_seconds,
                  model_inference_calls=1, blank_detection_count=len(boxes), private_media_read=False,
                  face_detection_quality_validated=False, infant_domain_validated=False,
                  source_runtime_numerical_parity=False, android_validated=False,
                  probe_sha256=_sha256(Path(__file__)))
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
