"""Generate small synthetic fixtures locally; optionally push owned files to a test device."""
from __future__ import annotations
import argparse
import json
import shutil
import subprocess
from qa_config import FIXTURES, PROJECT, adb_command, tool_path

NAMES = ["jigeum-qa-fuss.mp4", "jigeum-qa-no-audio.mp4", "jigeum-qa-silence.mp4",
         "jigeum-qa-long.mp4", "jigeum-qa-big.mp4", "jigeum-qa-broken.mp4",
         "jigeum-qa-broken.json", "jigeum-qa-future.json", "jigeum-qa-backup.json"]


def generate():
    FIXTURES.mkdir(parents=True, exist_ok=True)
    assets = PROJECT / "app/src/androidTest/assets"
    for dest, source in [("fuss", "generated-fuss.mp4"), ("no-audio", "no-audio.mp4"), ("silence", "silence.mp4")]:
        origin = assets / source
        if not origin.is_file():
            raise RuntimeError(f"Missing checked-in synthetic Android test asset: {origin}")
        shutil.copyfile(origin, FIXTURES / f"jigeum-qa-{dest}.mp4")
    ffmpeg = tool_path("ffmpeg")
    subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                    "-f", "lavfi", "-i", "color=c=0xb5c9b1:s=160x120:r=5", "-t", "31",
                    "-an", "-c:v", "mpeg4", "-q:v", "20", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", str(FIXTURES / "jigeum-qa-long.mp4")],
                   check=True, timeout=60)
    # App checks size before decode. Keep a playable MP4 header, pad to a bounded 51 MiB.
    large = FIXTURES / "jigeum-qa-big.mp4"
    shutil.copyfile(FIXTURES / "jigeum-qa-fuss.mp4", large)
    with large.open("r+b") as target:
        target.truncate(51 * 1024 * 1024)
    (FIXTURES / "jigeum-qa-broken.mp4").write_bytes(b"Synthetic QA fixture: invalid MP4 data.")
    (FIXTURES / "jigeum-qa-broken.json").write_text("{ broken \U0001f603", encoding="utf-8")
    future = {"format": "jigeum-observations", "version": 1, "ageBand": "", "records": [
        {"id": 253402300799999, "sound": "옹알이", "context": "놀이 중", "note": "QA future boundary",
         "phrase": "먼 미래의 백업 테스트", "source": "AUDIO", "modelCue": ""}]}
    (FIXTURES / "jigeum-qa-future.json").write_text(json.dumps(future, ensure_ascii=False), encoding="utf-8")
    return [FIXTURES / name for name in NAMES if name != "jigeum-qa-backup.json"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--push", action="store_true", help="Generate and push to ANDROID_SERIAL.")
    action.add_argument("--clean-device", action="store_true", help="Remove only the named QA fixture/backup files from device Downloads.")
    options = parser.parse_args()
    if options.clean_device:
        subprocess.run([*adb_command(), "shell", "rm", "-f", *["/sdcard/Download/" + name for name in NAMES]],
                       check=True, timeout=30)
        print("Removed owned QA Downloads files; app records were not changed.")
        return
    generated = generate()
    if options.push:
        # A prior export with this name would add a DocumentsUI overwrite dialog to the scenario.
        subprocess.run([*adb_command(), "shell", "rm", "-f", "/sdcard/Download/jigeum-qa-backup.json"], check=True, timeout=30)
        for path in generated:
            subprocess.run([*adb_command(), "push", str(path), "/sdcard/Download/" + path.name], check=True, timeout=90)
    print(f"Generated {len(generated)} synthetic fixtures in {FIXTURES}; pushed={options.push}")

if __name__ == "__main__":
    main()
