"""Run current instrumentation against one explicitly selected disposable device."""
from pathlib import Path
import argparse
import json
import re
import subprocess
import sys
import time
from qa_config import APK, TEST_APK, PACKAGE, adb_command, evidence_dir


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install-app", action="store_true", help="Install current debug APK before the test APK, keeping app data.")
    parser.add_argument("--timeout", type=int, default=600, help="Instrumentation wall-clock timeout in seconds.")
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    run = evidence_dir()
    adb = adb_command()
    for apk in ([APK] if args.install_app else []) + [TEST_APK]:
        if not apk.is_file():
            raise RuntimeError(f"Build the APK first: {apk}")
        result = subprocess.run([*adb, "install", "-r", str(apk)], capture_output=True, timeout=90)
        (run / ("install-" + apk.name + ".log")).write_bytes(result.stdout + result.stderr)
        result.check_returncode()
    started = time.monotonic()
    code = None
    try:
        result = subprocess.run([*adb, "shell", "am", "instrument", "-w", "-r",
                                 PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"],
                                capture_output=True, timeout=args.timeout)
        raw = (result.stdout + result.stderr).decode("utf-8", "replace")
        code = result.returncode
    except subprocess.TimeoutExpired as error:
        raw = ((error.stdout or b"") + (error.stderr or b"")).decode("utf-8", "replace") + "\nHARNESS TIMEOUT\n"
        # Stop only the owned target/test processes before another scenario can start.
        for owned in [PACKAGE, PACKAGE + ".test"]:
            stopped = subprocess.run([*adb, "shell", "am", "force-stop", owned], capture_output=True, timeout=20)
            raw += "Timeout cleanup " + owned + ": " + str(stopped.returncode) + "\n"
    (run / "instrumentation.log").write_text(raw, encoding="utf-8")
    match = re.search(r"OK \((\d+) tests?\)", raw)
    passed = code == 0 and bool(match) and not any(text in raw for text in ["FAILURES", "INSTRUMENTATION_FAILED", "Process crashed", "HARNESS TIMEOUT"])
    verdict = {"status": "pass" if passed else "fail", "tests": int(match.group(1)) if match else None,
               "seconds": round(time.monotonic() - started, 2), "exit": code}
    (run / "instrumentation-result.json").write_text(json.dumps(verdict, indent=2), encoding="utf-8")
    print(json.dumps(verdict)); print(raw[-1600:])
    return 0 if passed else 1

if __name__ == "__main__":
    sys.exit(main())
