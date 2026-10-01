"""Collect validated store files without modifying any screenshot's visible pixels."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    dest = args.destination.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    receipts = []
    for i, name in enumerate(("home", "expression", "record", "album"), 1):
        source = args.evidence / f"store-{i:02d}-{name}.png"
        target = dest / f"screenshot-{i:02d}-{name}.png"
        with Image.open(source) as raw:
            width, height = raw.size
            assert min(width, height) >= 320 and max(width, height) <= 3840
            assert max(width, height) <= 2 * min(width, height), raw.size
            if "A" in raw.getbands():
                assert raw.getchannel("A").getextrema() == (255, 255), "Transparent capture"
            rgb = raw.convert("RGB")
            pixels = hashlib.sha256(rgb.tobytes()).hexdigest()
            rgb.save(target, format="PNG", optimize=True)
        with Image.open(target) as submitted:
            assert submitted.mode == "RGB" and submitted.size == (width, height)
            assert hashlib.sha256(submitted.tobytes()).hexdigest() == pixels
        receipts.append({"file": target.name, "source": str(source.resolve()),
                         "source_sha256": sha(source), "sha256": sha(target),
                         "width": width, "height": height, "mode": "RGB",
                         "visible_pixels_unchanged": True})
    for name, size, mode in (("play-icon-512x512.png", (512, 512), "RGBA"),
                              ("feature-graphic-1024x500.png", (1024, 500), "RGB")):
        source = ROOT / "outputs/StoreListing" / name
        with Image.open(source) as image:
            assert image.size == size and image.mode in ("RGB", "RGBA"), (name, image.size, image.mode)
            original_pixels = image.convert("RGB").tobytes()
            converted = image.convert(mode)
        if "icon" in name:
            assert source.stat().st_size <= 1024 * 1024
        target = dest / name
        converted.save(target, format="PNG", optimize=True)
        with Image.open(target) as output:
            assert output.mode == mode and output.convert("RGB").tobytes() == original_pixels
        receipts.append({"file": name, "source": str(source.resolve()), "sha256": sha(target),
                         "width": size[0], "height": size[1], "mode": mode})
    for name, maximum in (("title-ko.txt", 30), ("short-description-ko.txt", 80),
                           ("full-description-ko.txt", 4000)):
        path = dest / name
        length = len(path.read_text(encoding="utf-8").strip())
        assert 0 < length <= maximum, (name, length, maximum)
        receipts.append({"file": name, "characters": length, "maximum": maximum, "sha256": sha(path)})
    result = {"status": "pass", "files": receipts, "screenshot_source": "actual signed release on API35 x86_64",
              "demo_data": "synthetic phrases, no real baby media",
              "billing": "disabled in captured build; no actual purchase shown"}
    (dest / "assets-manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "pass", "validated_files": len(receipts)}))


if __name__ == "__main__":
    main()
