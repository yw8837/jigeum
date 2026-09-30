"""Portable QA paths. Device use always requires an explicit serial."""
from __future__ import annotations
import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "outputs" / "MoaAndroid"
HERE = Path(__file__).resolve().parent
EVIDENCE = Path(os.environ.get("JIGEUM_QA_EVIDENCE", str(HERE / "evidence"))).resolve()
FIXTURES = HERE / "fixtures"
APK = PROJECT / "app/build/outputs/apk/debug/app-debug.apk"
TEST_APK = PROJECT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
AAB = PROJECT / "app/build/outputs/bundle/release/app-release.aab"
PACKAGE = "com.moa.babylanguage"


def sdk_root() -> Path:
    value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not value:
        raise RuntimeError("Set ANDROID_HOME to the installed Android SDK directory.")
    return Path(value).expanduser().resolve()


def tool_path(name: str, folder: Path | None = None) -> str:
    names = [name + suffix for suffix in ([".exe", ".bat", ""] if os.name == "nt" else [""])]
    if folder:
        for candidate in names:
            path = folder / candidate
            if path.is_file():
                return str(path)
    for candidate in names:
        found = shutil.which(candidate)
        if found:
            return found
    raise RuntimeError(f"Required tool not found: {name}; configure SDK/JAVA_HOME/PATH.")


def adb_command() -> list[str]:
    serial = os.environ.get("JIGEUM_QA_SERIAL") or os.environ.get("ANDROID_SERIAL")
    if not serial:
        raise RuntimeError("Set ANDROID_SERIAL (or JIGEUM_QA_SERIAL) to an exclusively owned test emulator/device.")
    override = os.environ.get("JIGEUM_QA_ADB")
    if override:
        executable = str(Path(override).expanduser().resolve())
    elif os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"):
        executable = tool_path("adb", sdk_root() / "platform-tools")
    else:
        executable = tool_path("adb")
    return [executable, "-s", serial]


def evidence_dir() -> Path:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    return EVIDENCE
