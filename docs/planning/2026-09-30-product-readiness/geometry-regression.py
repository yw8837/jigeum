#!/usr/bin/env python3
"""Reproduce a mouth-ratio arithmetic issue using only synthetic coordinates.

This checks a proposed geometry formula, NOT the current Android/Kotlin code,
MediaPipe inference, a device camera, or infant-analysis accuracy. No model,
media, dataset, network access, or third-party dependency is used.

Coordinate contract:
* normalized x/y refer to the actual upright/scaled bitmap passed to MPImage
* width/height below are that bitmap's dimensions, not original video metadata,
  PreviewView dimensions, or a MediaPipe internal tensor size
* the crop, rotation, mirror and letterbox cases explicitly transform points
  into the same coordinate system as their stated input bitmap dimensions
* letterbox uses uniform scale plus translation; external non-uniform resizing
  must be avoided or inverted before measuring in a common pixel coordinate system
* MediaPipe's internal crop/projection must not be inverted a second time

Usage: python geometry-regression.py
Port the regression cases to the actual shared Kotlin function before claiming
that the application has been fixed. Threshold 0.12 is copied from the reviewed
baseline solely to expose a changed decision; it is not validated for infants.
"""

from math import hypot, isclose, isfinite


# upper lip, lower lip, left corner, right corner; continuous pixel coordinates
POINTS = ((800.0, 440.0), (800.0, 450.0), (750.0, 445.0), (850.0, 445.0))
EXPECTED_RATIO = 0.1
BASELINE_THRESHOLD = 0.12


def normalized(points, width, height):
    if width <= 0 or height <= 0:
        raise ValueError("Input bitmap dimensions must be positive")
    return tuple((x / width, y / height) for x, y in points)


def legacy_ratio(points_normalized):
    """Arithmetic equivalent of using normalized x/y as one Euclidean space."""
    def distance(i, j):
        return hypot(
            points_normalized[i][0] - points_normalized[j][0],
            points_normalized[i][1] - points_normalized[j][1],
        )
    return distance(0, 1) / distance(2, 3)


def proposed_ratio(points_normalized, bitmap_width, bitmap_height):
    """Proposed arithmetic, not an imported or executed application function."""
    if bitmap_width <= 0 or bitmap_height <= 0:
        raise ValueError("Input bitmap dimensions must be positive")
    if not all(isfinite(v) for point in points_normalized for v in point):
        raise ValueError("Landmarks must be finite")
    def distance(i, j):
        return hypot(
            (points_normalized[i][0] - points_normalized[j][0]) * bitmap_width,
            (points_normalized[i][1] - points_normalized[j][1]) * bitmap_height,
        )
    mouth_width = distance(2, 3)
    if mouth_width <= 0:
        raise ValueError("A zero-width mouth must not produce a decision")
    return distance(0, 1) / mouth_width


def main():
    landscape = normalized(POINTS, 1600, 900)
    before = legacy_ratio(landscape)
    after = proposed_ratio(landscape, 1600, 900)
    assert not isclose(before, EXPECTED_RATIO, abs_tol=1e-12)
    assert before > BASELINE_THRESHOLD
    assert isclose(after, EXPECTED_RATIO, abs_tol=1e-12)
    assert not after > BASELINE_THRESHOLD
    print(f"16:9 legacy arithmetic: {before:.8f}, open=True, EXPECTED BASELINE FAILURE")
    print(f"16:9 proposed arithmetic: {after:.8f}, open=False, PASS")

    cases = [
        ("upright_rotation_0", POINTS, 1600, 900),
        ("upright_rotation_90", tuple((900-y, x) for x, y in POINTS), 900, 1600),
        ("upright_rotation_180", tuple((1600-x, 900-y) for x, y in POINTS), 1600, 900),
        ("upright_rotation_270", tuple((y, 1600-x) for x, y in POINTS), 900, 1600),
        ("uniform_scaled", tuple((x*.32, y*.32) for x, y in POINTS), 512, 288),
        ("letterbox_512", tuple((x*.32, y*.32+112) for x, y in POINTS), 512, 512),
        ("mirror", tuple((1600-x, y) for x, y in POINTS), 1600, 900),
        ("crop", tuple((x-700, y-400) for x, y in POINTS), 200, 100),
    ]
    for name, points, width, height in cases:
        result = proposed_ratio(normalized(points, width, height), width, height)
        assert isclose(result, EXPECTED_RATIO, abs_tol=1e-12), (name, result)
        print(f"{name}: {result:.8f} PASS")
    print("8 synthetic invariance cases passed; application/model/device not tested")


if __name__ == "__main__":
    main()
