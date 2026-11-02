"""Local cadence experiment using the app's existing FaceLandmarker asset.

No media download, upload, training, or baby/speaker/intent classification.
Desktop results are not Android performance or infant accuracy measurements.
Per-frame observations remain in the caller's ignored local output directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import time


class CadenceSampler:
    def __init__(self, step_ms: int):
        if step_ms <= 0:
            raise ValueError("Positive cadence required")
        self.step_ms, self.target, self.previous = step_ms, 0, -1

    def accepts(self, pts: int) -> bool:
        if pts < 0 or pts < self.previous:
            raise ValueError("Nonnegative monotonic PTS required")
        self.previous = pts
        if pts < self.target:
            return False
        self.target = (pts // self.step_ms + 1) * self.step_ms
        return True


def select_times(times: list[int], step_ms: int) -> list[int]:
    sampler = CadenceSampler(step_ms)
    return [index for index, pts in enumerate(times) if sampler.accepts(pts)]


def changes(rows: list[dict]) -> list[dict]:
    """Same hysteresis and gap policy as VisualTimeline, without claiming truth."""
    result = []
    previous = stable = opened = None
    for row in rows:
        at, mouth = row["pts_ms"], row["mouth"]
        if row["faces"] != 1 or mouth is None or not math.isfinite(mouth) or mouth < 0:
            previous = stable = opened = None
            continue
        if previous is not None and at - previous > 750:
            stable = opened = None
        next_open = False if mouth <= .08 else True if mouth >= .18 else None
        if next_open is not None:
            if opened is not None and opened != next_open:
                result.append({"start_ms": stable, "end_ms": at, "opening": next_open})
            stable, opened = at, next_open
        previous = at
    return result


def summarize(rows: list[dict]) -> dict:
    mouths = [r["mouth"] for r in rows if r["mouth"] is not None]
    events = changes(rows)
    return {"observations": len(rows), "single_face": sum(r["faces"] == 1 for r in rows),
            "usable_mouth": len(mouths), "multiple_face": sum(r["faces"] > 1 for r in rows),
            "mouth_transitions": len(events), "opening_transitions": sum(e["opening"] for e in events),
            "mouth_min": min(mouths, default=None), "mouth_max": max(mouths, default=None),
            "first_pts_ms": rows[0]["pts_ms"] if rows else None,
            "last_pts_ms": rows[-1]["pts_ms"] if rows else None}


def prepared_frames(directory: Path):
    """Validate the previous local extraction before using its original PTS map."""
    import cv2
    import numpy as np
    manifest = json.loads((directory / "media-report.json").read_text("utf-8"))
    mapping = json.loads((directory / "source-pts-map-private.json").read_text("utf-8"))
    paths = sorted((directory / "frames").glob("*.jpg"))
    hashes = manifest["frame_pixel_hashes"]  # historical name; these are JPEG byte hashes
    if len(paths) != len(mapping) or len(paths) != len(hashes) or len(paths) != manifest["frames"]:
        raise ValueError("Incomplete prepared frame set")
    previous = -1
    for index, (path, row, digest) in enumerate(zip(paths, mapping, hashes)):
        encoded = path.read_bytes()
        if hashlib.sha256(encoded).hexdigest() != digest or row["sample_frame"] != index or row["ambiguity"] != 1:
            raise ValueError("Prepared frame provenance mismatch")
        pts = round(row["source_pts"] * 1000)
        if pts <= previous:
            raise ValueError("Invalid prepared source PTS")
        previous = pts
        pixels = cv2.imdecode(np.frombuffer(encoded, np.uint8), cv2.IMREAD_COLOR)
        if pixels is None:
            raise ValueError("Invalid prepared JPEG")
        yield pts, pixels


def video_frames(source: Path):
    import cv2
    cap = cv2.VideoCapture(str(source))
    if not cap.isOpened():
        raise ValueError("Cannot open local video")
    try:
        while True:
            ok, bgr = cap.read()
            if not ok:
                return
            yield round(cap.get(cv2.CAP_PROP_POS_MSEC)), bgr
    finally:
        cap.release()


def measure(source: Path, model: Path, step: int, seconds: int) -> tuple[list[dict], dict]:
    import cv2
    import mediapipe as mp
    options = mp.tasks.vision.FaceLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_buffer=model.read_bytes()),
        running_mode=mp.tasks.vision.RunningMode.VIDEO, num_faces=2,
        output_face_blendshapes=True)
    source_frames = prepared_frames(source) if source.is_dir() else video_frames(source)
    rows, decoded = [], 0
    sampler = CadenceSampler(step)
    started = time.perf_counter()
    try:
        with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
            for pts, bgr in source_frames:
                decoded += 1
                if pts >= seconds * 1000:
                    break
                if not sampler.accepts(pts):
                    continue
                h, w = bgr.shape[:2]
                ratio = min(1.0, 512 / max(w, h))
                bgr = cv2.resize(bgr, (round(w * ratio), round(h * ratio)))
                h, w = bgr.shape[:2]
                rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                result = detector.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), pts)
                faces, mouth, smile = result.face_landmarks, None, None
                if len(faces) == 1:
                    points = faces[0]
                    used = [points[i] for i in [13, 14, 61, 291]]
                    if all(math.isfinite(p.x) and math.isfinite(p.y) and 0 <= p.x <= 1 and 0 <= p.y <= 1 for p in used):
                        def distance(a, b):
                            return math.hypot((a.x - b.x) * w, (a.y - b.y) * h)
                        width = distance(used[2], used[3])
                        if width >= 2:
                            mouth = distance(used[0], used[1]) / width
                    categories = {c.category_name: c.score for c in result.face_blendshapes[0]}
                    pair = [categories.get(k) for k in ["mouthSmileLeft", "mouthSmileRight"]]
                    if all(p is not None and math.isfinite(p) and 0 <= p <= 1 for p in pair):
                        smile = sum(pair) / 2
                rows.append({"pts_ms": pts, "faces": len(faces), "mouth": mouth, "corner_lift": smile})
    finally:
        source_frames.close()
    if not rows:
        raise ValueError("No observations")
    return rows, {"elapsed_seconds": time.perf_counter() - started, "decoded_frames": decoded,
                  "mediapipe_version": mp.__version__, **summarize(rows)}


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    inputs = p.add_mutually_exclusive_group(required=True)
    inputs.add_argument("--video", type=Path)
    inputs.add_argument("--prepared-frames", type=Path)
    p.add_argument("--model", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--seconds", type=int, choices=[10, 20], default=10)
    args = p.parse_args()
    source = args.video or args.prepared_frames
    if not args.model.is_file() or (args.video is not None and not source.is_file()) or (
            args.prepared_frames is not None and not source.is_dir()):
        raise ValueError("A local input is absent")
    if args.video is not None and args.video.stat().st_size > 200 * 1024**2:
        raise ValueError("Video exceeds 200 MiB")
    args.output.mkdir(parents=True, exist_ok=False)
    report = {"desktop_only": True, "infant_accuracy_evaluated": False,
              "audio_evaluated": False, "requested_seconds": args.seconds,
              "prepared_jpeg_replay": args.prepared_frames is not None,
              "model_sha256": hashlib.sha256(args.model.read_bytes()).hexdigest(), "runs": {}}
    for step in (500, 200, 100):
        rows, stats = measure(source, args.model, step, args.seconds)
        (args.output / f"observations-{step}.json").write_text(json.dumps(rows), encoding="utf-8")
        report["runs"][str(step)] = stats
        if step == 100:
            times = [r["pts_ms"] for r in rows]
            report["same_dense_track_subsample"] = {
                str(s): summarize([rows[i] for i in select_times(times, s)]) for s in (500, 200, 100)}
    (args.output / "summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
