"""Run current instrumentation against one explicitly selected disposable device."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
from qa_config import APK, TEST_APK, PACKAGE, ROOT, adb_command, evidence_dir


def local_provenance(app_apk: Path, test_apk: Path, root: Path) -> dict:
    """Record intended input bytes and source checkout before touching a device."""
    artifacts = {}
    for name, path in (("app_apk", app_apk), ("test_apk", test_apk)):
        if not path.is_file():
            raise RuntimeError(f"Build the intended APK first: {path}")
        digest = hashlib.sha256()
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        artifacts[name] = {"path": str(path.resolve()), "sha256": digest.hexdigest(), "bytes": path.stat().st_size}
    def git(*args):
        result = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=15)
        result.check_returncode()
        return result.stdout
    sha = git("rev-parse", "HEAD").strip()
    if not re.fullmatch(r"[a-fA-F0-9]{40,64}", sha):
        raise RuntimeError("Git did not return a valid commit identity")
    status = git("status", "--porcelain")
    artifacts["git"] = {"sha": sha, "dirty": bool(status.strip()), "porcelain": status.splitlines()}
    return artifacts


def installed_provenance(adb: list[str], package: str, expected_sha256: str, require_version=True) -> dict:
    """Compare one locally built APK with the exact installed base APK.

    This harness intentionally supports a single debug APK, not Play split APKs.
    Every read stays on the explicitly selected device; there is no fallback.
    """
    def shell(*args):
        result = subprocess.run([*adb, "shell", *args], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=20)
        result.check_returncode()
        return result.stdout
    paths = [line.removeprefix("package:").strip() for line in shell("pm", "path", package).splitlines()
             if line.startswith("package:")]
    if len(paths) != 1 or not re.fullmatch(r"/data/app/[A-Za-z0-9_./+=~-]+/base\.apk", paths[0]):
        raise RuntimeError(f"Expected one installed base APK for {package}; missing/split/unsupported install")
    digest_line = shell("sha256sum", paths[0]).strip()
    digest = re.fullmatch(r"([a-fA-F0-9]{64})\s+\*?" + re.escape(paths[0]), digest_line)
    observed_hash = digest[1].lower() if digest else None
    metadata = shell("dumpsys", "package", package)
    code = re.search(r"\bversionCode=(\d+)\b", metadata)
    name = re.search(r"\bversionName=([^\r\n]+)", metadata)
    errors = []
    if observed_hash != expected_sha256.lower():
        errors.append("Installed APK hash differs from the intended local APK or cannot be read")
    if require_version and (code is None or name is None):
        errors.append("Installed target version metadata is missing")
    return {"package": package, "base_apk": paths[0], "sha256": observed_hash,
            "expected_sha256": expected_sha256.lower(), "version_code": int(code[1]) if code else None,
            "version_name": name[1].strip() if name else None, "verified": not errors, "errors": errors}


def parse_result(raw: str, code: int | None, seconds: float, allowed_skips=()) -> dict:
    """Require a complete per-test event stream, not just Android's OK summary.

    Android counts assumption skips in OK(N). Only named, intentionally skipped
    tests may pass this gate, and at least one test must actually pass.
    """
    errors = []
    summaries = re.findall(r"(?m)^OK \((\d+) tests?\)\s*$", raw)
    total = int(summaries[0]) if len(summaries) == 1 else None
    if total is None or total < 1:
        errors.append("Expected exactly one nonempty OK summary")
    runner_codes = re.findall(r"(?m)^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", raw)
    if runner_codes != ["-1"]:
        errors.append("Missing or abnormal instrumentation completion")
    if code != 0:
        errors.append("ADB command did not exit successfully")
    if any(text in raw for text in ["FAILURES", "INSTRUMENTATION_FAILED", "Process crashed", "HARNESS TIMEOUT"]):
        errors.append("Runner reported a failure or harness timeout")

    fields, starts, terminals, statuses, discovered = {}, set(), {}, [], set()
    for line in raw.splitlines():
        field = re.match(r"^INSTRUMENTATION_STATUS: (\w+)=(.*)$", line)
        if field:
            fields[field[1]] = field[2]
        status = re.match(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", line)
        if not status:
            continue
        value = int(status[1])
        statuses.append(value)
        name = fields.get("class", "") + "#" + fields.get("test", "")
        if not fields.get("class") or not fields.get("test"):
            errors.append("Status event is missing its test identity")
        try:
            count, current = int(fields["numtests"]), int(fields["current"])
            discovered.add(count)
            if not 1 <= current <= count:
                errors.append(f"Invalid test ordinal: {name}")
        except (KeyError, ValueError):
            errors.append(f"Missing/invalid discovery metadata: {name}")
        if value == 1:
            if name in starts or name in terminals:
                errors.append(f"Duplicate/out-of-order start: {name}")
            starts.add(name)
        elif value in (0, -1, -2, -3, -4):
            if name in terminals:
                errors.append(f"Duplicate result: {name}")
            # JUnit @Ignore may emit only the terminal ignored event.
            if name not in starts and value != -3:
                errors.append(f"Result has no start event: {name}")
            terminals[name] = value
        else:
            errors.append(f"Unknown instrumentation status: {value}")
        fields = {}
    if total is None or discovered != {total} or len(terminals) != total or starts - terminals.keys():
        errors.append("Discovered, started, completed and summary counts do not agree")
    skipped = sorted(name for name, value in terminals.items() if value in (-3, -4))
    unexpected_skips = sorted(set(skipped) - set(allowed_skips))
    if unexpected_skips:
        errors.append("Unexpected skipped tests: " + ", ".join(unexpected_skips))
    failed_tests = sum(status in (-1, -2) for status in statuses)
    if failed_tests:
        errors.append("One or more tests failed")
    if statuses.count(0) == 0:
        errors.append("No executed passing tests")
    return {"status": "fail" if errors else "pass", "tests": total,
            "passed_tests": statuses.count(0), "failed_tests": failed_tests,
            "ignored_tests": statuses.count(-3), "assumption_skipped_tests": statuses.count(-4),
            "skipped_test_ids": skipped, "unexpected_skips": unexpected_skips,
            "allowed_skips": sorted(set(allowed_skips)), "validation_errors": errors,
            "seconds": round(seconds, 2), "exit": code}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install-app", action="store_true", help="Install current debug APK before the test APK, keeping app data.")
    parser.add_argument("--timeout", type=int, default=600, help="Instrumentation wall-clock timeout in seconds.")
    parser.add_argument("--allow-skip", action="append", default=[], metavar="CLASS#METHOD",
                        help="Explicitly allow one expected skip; repeat for each test. All-skipped runs still fail.")
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    run = evidence_dir()
    adb = adb_command()
    provenance = {"adb": adb, "install_app_requested": args.install_app,
                  "scope": "single debug APK on an exclusively owned device"}
    try:
        provenance.update(local_provenance(APK, TEST_APK, ROOT))
        for apk in ([APK] if args.install_app else []) + [TEST_APK]:
            result = subprocess.run([*adb, "install", "-r", str(apk)], capture_output=True, timeout=90)
            (run / ("install-" + apk.name + ".log")).write_bytes(result.stdout + result.stderr)
            result.check_returncode()
        for role, package in (("app", PACKAGE), ("test", PACKAGE + ".test")):
            installed = installed_provenance(adb, package, provenance[role + "_apk"]["sha256"], require_version=role == "app")
            provenance["installed_" + role] = installed
            if not installed["verified"]:
                raise RuntimeError(f"{package}: " + "; ".join(installed["errors"]))
    except Exception as error:
        verdict = {"status": "fail", "stage": "provenance", "tests": None,
                   "instrumentation_started": False, "error": str(error), "provenance": provenance}
        (run / "instrumentation-result.json").write_text(json.dumps(verdict, indent=2), encoding="utf-8")
        print(json.dumps(verdict))
        return 1
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
    verdict = parse_result(raw, code, time.monotonic() - started, allowed_skips=args.allow_skip)
    verdict["instrumentation_started"] = True
    verdict["provenance"] = provenance
    (run / "instrumentation-result.json").write_text(json.dumps(verdict, indent=2), encoding="utf-8")
    print(json.dumps(verdict)); print(raw[-1600:])
    return 0 if verdict["status"] == "pass" else 1

if __name__ == "__main__":
    sys.exit(main())
