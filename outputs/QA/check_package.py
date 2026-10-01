"""Inspect a built APK/AAB without using an Android device or publishing anything."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from qa_config import ROOT, PROJECT, HERE, APK, AAB, evidence_dir, sdk_root, tool_path


def aab_signature_state(output: str, expect_unsigned: bool = False) -> bool:
    """jarsigner may exit zero for an unsigned archive: inspect its verdict too."""
    unsigned = "jar is unsigned" in output.lower()
    verified = bool(re.search(r"(?im)^jar verified\.\s*$", output))
    if expect_unsigned:
        if not unsigned or verified:
            raise ValueError("Expected an explicitly unsigned AAB")
    elif unsigned or not verified or "unsigned entries" in output.lower():
        raise ValueError("AAB must be fully signed and verified; unsigned bundles require --expect-unsigned-aab")
    return unsigned


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=APK)
    parser.add_argument("--aab", type=Path, default=AAB)
    parser.add_argument("--skip-aab", action="store_true", help="Record AAB inspection as skipped.")
    parser.add_argument("--baseline", type=Path, help="Optional known-good local APK; compare its native libraries/model assets byte for byte.")
    parser.add_argument("--expect-unsigned-aab", action="store_true", help="Require an unsigned bundle when upload credentials were intentionally omitted.")
    parser.add_argument("--build-tools", default=os.environ.get("JIGEUM_QA_BUILD_TOOLS", "36.0.0"))
    args = parser.parse_args()
    evidence = evidence_dir()
    sdk = sdk_root() / "build-tools" / args.build_tools
    java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
    apk = args.apk.resolve()
    aab = args.aab.resolve()
    if not apk.is_file() or (not args.skip_aab and not aab.is_file()):
        raise RuntimeError("Build the requested APK/AAB first, or use --skip-aab explicitly.")

    def run(name, command):
        result = subprocess.run([str(item) for item in command], cwd=ROOT,
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        output = result.stdout + result.stderr
        (evidence / name).write_text(output, encoding="utf-8")
        if result.returncode:
            raise RuntimeError(f"{name} failed ({result.returncode}): {output[-1000:]}")
        return output

    aapt = tool_path("aapt2", sdk)
    domains = {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"}
    rules = ET.parse(PROJECT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
    for mode in ["cloud-backup", "device-transfer"]:
        assert {node.attrib["domain"] for node in rules.find(mode).findall("exclude") if node.attrib["path"] == "."} == domains
    # Release resource optimization can rename paths (e.g. res/4j.xml) even
    # without resource shrinking. Resolve the compiled resource by its name.
    resources = run("resource-table-apk.txt", [aapt, "dump", "resources", apk])
    def xml_resource_path(name):
        blocks = re.split(r"(?m)^\s*resource ", resources)
        candidates = []
        for block in blocks:
            if re.match(r"0x[0-9a-fA-F]+\s+xml/" + re.escape(name) + r"\s*(?:\n|$)", block):
                candidates.extend(re.findall(r"\(file\)\s+(\S+)\s+type=XML", block))
        paths = set(candidates)
        assert len(paths) == 1, f"Expected one compiled XML for {name}, got {paths}"
        return paths.pop()
    compiled = run("backup-rules-apk.txt", [aapt, "dump", "xmltree", apk, "--file", xml_resource_path("data_extraction_rules")])
    for domain in domains:
        assert compiled.count(f'domain="{domain}"') == 2, domain
    manifest = run("manifest-apk.txt", [aapt, "dump", "xmltree", apk, "--file", "AndroidManifest.xml"])
    assert "android.permission.INTERNET" in manifest
    assert "android.permission.ACCESS_NETWORK_STATE" not in manifest
    assert "android:allowBackup" in manifest and "android:fullBackupContent" in manifest
    for line in manifest.splitlines():
        if "android:allowBackup" in line or "android:fullBackupContent" in line:
            assert line.strip().endswith(("=false", "0x0")), line
    assert "com.moa.babylanguage.files" in manifest
    paths = run("share-paths-apk.txt", [aapt, "dump", "xmltree", apk, "--file", xml_resource_path("share_paths")])
    assert "exports/" in paths and "root-path" not in paths and "external-path" not in paths
    run("apk-badging.txt", [aapt, "dump", "badging", apk])
    run("apk-signature.txt", [tool_path("apksigner", sdk), "verify", "--verbose", "--print-certs", apk])
    run("zipalign.txt", [tool_path("zipalign", sdk), "-c", "-P", "16", "-v", "4", apk])
    run("elf-apk.txt", [sys.executable, HERE / "release_check.py", apk])
    aab_unsigned = None
    if not args.skip_aab:
        signature = run("aab-signature.txt", [tool_path("jarsigner", java), "-J-Duser.language=en", "-verify", aab])
        aab_unsigned = aab_signature_state(signature, args.expect_unsigned_aab)
        run("elf-aab.txt", [sys.executable, HERE / "release_check.py", aab])
    baseline_result = {"status": "skipped", "reason": "No --baseline supplied"}
    if args.baseline:
        with zipfile.ZipFile(args.baseline.resolve()) as old, zipfile.ZipFile(apk) as new:
            names = [name for name in old.namelist() if name.endswith((".so", ".tflite", ".task"))]
            assert names, "Baseline has no native/model files to compare"
            for name in names:
                assert old.read(name) == new.read(name), name
        baseline_result = {"status": "pass", "files_compared": len(names), "baseline": str(args.baseline.resolve())}
    verdict = {"status": "pass", "backup_domains_excluded_in_both_modes": sorted(domains),
               "compiled_apk_has_internet_permission": True, "allowBackup": False, "fullBackupContent": False,
               "baseline_comparison": baseline_result, "aab_checked": not args.skip_aab, "aab_unsigned": aab_unsigned,
               "physical_OEM_transfer_tested": False, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}
    (evidence / "package-verdict.json").write_text(json.dumps(verdict, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(verdict, indent=2))

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        verdict = {"status": "fail", "error": str(error)}
        (evidence_dir() / "package-verdict.json").write_text(json.dumps(verdict, indent=2) + "\n", encoding="utf-8")
        raise
