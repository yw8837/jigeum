from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


from qa_config import APK, PACKAGE, adb_command, evidence_dir

RUN = evidence_dir()
ENV = dict(os.environ)
ENV.pop("OMX_ROOT", None)
ENV.pop("OMX_STATE_ROOT", None)


def cmd(args: list[str], timeout=25, check=True) -> subprocess.CompletedProcess:
    t0 = time.monotonic()
    result = subprocess.run(args, capture_output=True, timeout=timeout, env=ENV)
    evidence = {"at": time.time(), "seconds": round(time.monotonic()-t0, 2),
                "command": args, "exit": result.returncode,
                "stdout": result.stdout.decode("utf-8", "replace")[-3000:],
                "stderr": result.stderr.decode("utf-8", "replace")[-3000:]}
    with (RUN / "commands.jsonl").open("a", encoding="utf-8") as log:
        log.write(json.dumps(evidence, ensure_ascii=False) + "\n")
    if check and result.returncode != 0:
        raise RuntimeError(f"Command failed {args}: {evidence['stderr'] or evidence['stdout']}")
    return result


def adb(*args: str, timeout=25, check=True):
    return cmd([*adb_command(), *args], timeout=timeout, check=check)


def shell(*args: str, timeout=25, check=True):
    return adb("shell", *args, timeout=timeout, check=check)


def wait_ui(expect: str, seconds=15):
    deadline = time.monotonic() + seconds
    last = []
    while time.monotonic() < deadline:
        last = nodes()
        if any(expect in item["text"] for item in last):
            return last
        time.sleep(.35)
    raise AssertionError(f"UI missing {expect}; current: {[n['text'] for n in last if n['text']][:25]}")


def nodes():
    # UiAutomator can report a null root during the first activity frame while
    # still returning exit 0. Treat that as transient harness state.
    for attempt in range(3):
        dumped = shell("uiautomator", "dump", "/sdcard/moa-ultraqa.xml", timeout=20)
        raw = adb("exec-out", "cat", "/sdcard/moa-ultraqa.xml", timeout=12).stdout
        if b"<hierarchy" in raw and b"null root" not in dumped.stderr:
            root = ET.fromstring(raw)
            break
        time.sleep(.5)
    else:
        raise RuntimeError("UiAutomator did not provide a hierarchy after 3 attempts")
    out = []
    for n in root.iter("node"):
        bounds = n.attrib.get("bounds", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1,y1,x2,y2 = map(int, match.groups())
            out.append({"text":n.attrib.get("text", ""), "desc":n.attrib.get("content-desc", ""),
                        "enabled":n.attrib.get("enabled")=="true", "clickable":n.attrib.get("clickable")=="true",
                        "center":((x1+x2)//2,(y1+y2)//2), "bounds":(x1,y1,x2,y2)})
    return out


def tap(x: int, y: int):
    shell("input", "tap", str(x), str(y))
    time.sleep(.24)


def swipe_up():
    size = shell("wm", "size").stdout.decode("utf-8", "replace")
    dimensions = re.findall(r"(\d+)x(\d+)", size)
    width, height = map(int, dimensions[-1]) if dimensions else (1080, 2400)
    shell("input", "swipe", str(width//2), str(int(height*.65)),
          str(width//2), str(int(height*.20)), "360")
    time.sleep(.25)


def tap_text(value: str, exact=True, scroll=True, attempts=6):
    display = shell("wm", "size").stdout.decode("utf-8", "replace")
    dimensions = re.findall(r"(\d+)x(\d+)", display)
    height = int(dimensions[-1][1]) if dimensions else 2400
    for i in range(attempts):
        entries = nodes()
        matched = [n for n in entries if (n["text"] == value if exact else value in n["text"])
                   and 30 < n["center"][1] < height - 20]
        if matched:
            x,y=matched[0]["center"]
            tap(x,y)
            return matched[0]
        if not scroll:
            break
        swipe_up()
    raise AssertionError(f"No tappable text {value}; {[n['text'] for n in nodes() if n['text']][:30]}")


def screenshot(name: str):
    (RUN / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p").stdout)


def display_settings():
    return {key: shell("wm", key).stdout.decode("utf-8", "replace") for key in ("size", "density")}


def restore_display(settings):
    for key, text in settings.items():
        match = re.search(r"Override (?:size|density):\s*(\S+)", text)
        shell("wm", key, match.group(1) if match else "reset")


def trace(label: str, extra=None):
    entries = nodes()
    line = {"step":label, "time":time.time(), "texts":[n["text"] for n in entries if n["text"]], "extra":extra}
    with (RUN/"ui-steps.jsonl").open("a",encoding="utf-8") as file:
        file.write(json.dumps(line,ensure_ascii=False)+"\n")
    return entries


def launch():
    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait_ui("지금 아기는")


def kill_background_process():
    """After onStop, terminate only this owned debug app while retaining task state."""
    time.sleep(.8)
    shell("am", "kill", PACKAGE)
    time.sleep(1)
    pid = shell("pidof", PACKAGE, check=False).stdout.decode().strip()
    if pid:
        # Android may keep a recently visible process despite advisory am kill.
        # run-as confines the fallback to the debug app UID; force-stop would
        # change saved-task semantics and is deliberately not used here.
        if not pid.isdecimal():
            raise AssertionError(f"Unexpected target process list: {pid}")
        shell("run-as", PACKAGE, "kill", "-9", pid)
    for _ in range(5):
        if not shell("pidof", PACKAGE, check=False).stdout.strip():
            return
        time.sleep(.2)
    raise AssertionError("Owned background app process did not terminate")


def background():
    launch()
    tap_text("소리로 번역하기")
    tap_text("녹음 시작")
    wait_ui("여기서 마치기")
    shell("input", "keyevent", "KEYCODE_HOME")
    time.sleep(1)
    shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait_ui("앱을 벗어나 녹음을 중단했어요")
    trace("record-background-stopped")
    return {"status":"pass"}


def audio10():
    launch()
    tap_text("소리로 번역하기")
    tap_text("녹음 시작")
    wait_ui("여기서 마치기")
    wait_ui("들은 것", seconds=35)
    result = trace("ten-second-audio-analysis")
    if not any("보호자가 들은 유형" in n["text"] for n in result):
        raise AssertionError("Audio analysis did not reach review form")
    return {"status":"pass"}
