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

DISTRIBUTIONS = {'release': (False, False, ''), 'preview': (True, False, '-test'), 'beta': (True, True, '-beta')}


def assert_markers_version(full_test: bool, closed_test: bool, version: str, distribution: str) -> None:
    if distribution not in DISTRIBUTIONS:
        raise ValueError('Unknown distribution')
    suffix = re.search(r'-(test|beta)$', version)
    if (full_test, closed_test, suffix[0] if suffix else '') != DISTRIBUTIONS[distribution]:
        raise ValueError('Compiled distribution marker/version does not match intended delivery')


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


def assert_distribution(manifest: str, badging: str, distribution: str) -> None:
    markers = re.findall(r'com\.moa\.babylanguage\.TEST_DISTRIBUTION[^\n]*\n\s*A: (?:http://schemas.android.com/apk/res/)?android:value[^\n]*=(true|false|0xffffffff|0x0)\s*$', manifest, re.M)
    if len(markers) != 1:
        raise ValueError('Missing or ambiguous compiled test distribution marker')
    full_test = markers[0] in ('true', '0xffffffff')
    closed_markers = re.findall(r'com\.moa\.babylanguage\.CLOSED_TEST_DISTRIBUTION[^\n]*\n\s*A: (?:http://schemas.android.com/apk/res/)?android:value[^\n]*=(true|false|0xffffffff|0x0)\s*$', manifest, re.M)
    if len(closed_markers) > 1 or (distribution == 'beta' and len(closed_markers) != 1):
        raise ValueError('Missing or ambiguous compiled closed-test distribution marker')
    closed_test = bool(closed_markers and closed_markers[0] in ('true', '0xffffffff'))
    version = re.search(r"\bversionName='([^']+)'", badging)
    if version is None:
        raise ValueError('Missing compiled version')
    assert_markers_version(full_test, closed_test, version[1], distribution)


def assert_bundle_distribution(manifest_xml: str, apk_badging: str, distribution: str) -> dict:
    """Read the final AAB through official bundletool, not a neighboring APK."""
    android = '{http://schemas.android.com/apk/res/android}'
    tree = ET.fromstring(manifest_xml)
    package = tree.get('package')
    version_code = tree.get(android + 'versionCode')
    version_name = tree.get(android + 'versionName')
    apk = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", apk_badging)
    if not apk or (package, version_code, version_name) != apk.groups():
        raise ValueError('AAB package/version differs from the inspected APK')
    application = tree.find('application')
    if application is None:
        raise ValueError('AAB application missing')
    def marker(name):
        values = [node.get(android + 'value') for node in application.findall('meta-data')
                  if node.get(android + 'name') == 'com.moa.babylanguage.' + name]
        if len(values) != 1 or values[0] not in ('true', 'false'):
            raise ValueError('Missing, ambiguous or invalid AAB marker: ' + name)
        return values[0] == 'true'
    full_test = marker('TEST_DISTRIBUTION')
    closed_test = marker('CLOSED_TEST_DISTRIBUTION')
    assert_markers_version(full_test, closed_test, version_name, distribution)
    return {'package': package, 'version_code': int(version_code), 'version_name': version_name,
            'test_distribution': full_test, 'closed_test_distribution': closed_test}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=APK)
    parser.add_argument("--aab", type=Path, default=AAB)
    parser.add_argument("--skip-aab", action="store_true", help="Record AAB inspection as skipped.")
    parser.add_argument("--baseline", type=Path, help="Optional known-good local APK; compare its native libraries/model assets byte for byte.")
    parser.add_argument("--distribution", choices=['release','preview','beta'], help="Assert compiled release, sideload preview or Play closed-test beta markers and version suffix.")
    parser.add_argument("--expect-unsigned-aab", action="store_true", help="Require an unsigned bundle when upload credentials were intentionally omitted.")
    parser.add_argument("--bundletool", type=Path, help="Official local bundletool JAR to inspect the final AAB manifest; required for beta.")
    parser.add_argument("--admob-app-id", help="Require the approved compiled AdMob application ID and SDK manifest configuration.")
    parser.add_argument("--build-tools", default=os.environ.get("JIGEUM_QA_BUILD_TOOLS", "36.0.0"))
    args = parser.parse_args()
    if args.distribution == 'preview' and not args.skip_aab:
        parser.error('Preview is sideload-only; use --skip-aab and never deliver its AAB.')
    if args.distribution == 'beta' and (args.skip_aab or not args.bundletool):
        parser.error('Beta requires its exact final AAB and --bundletool manifest verification.')
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
    if args.admob_app_id:
        assert re.fullmatch(r"ca-app-pub-\d{16}~\d{10}", args.admob_app_id), "Invalid expected AdMob app ID"
        assert "android.permission.ACCESS_NETWORK_STATE" in manifest
        assert "com.google.android.gms.permission.AD_ID" in manifest
        assert "com.google.android.gms.ads.APPLICATION_ID" in manifest and args.admob_app_id in manifest
        assert "com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT" in manifest
    else:
        assert "android.permission.ACCESS_NETWORK_STATE" not in manifest, "Ads build requires explicit --admob-app-id check"
    assert "android:allowBackup" in manifest and "android:fullBackupContent" in manifest
    for line in manifest.splitlines():
        if "android:allowBackup" in line or "android:fullBackupContent" in line:
            assert line.strip().endswith(("=false", "0x0")), line
    assert "com.moa.babylanguage.files" in manifest
    paths = run("share-paths-apk.txt", [aapt, "dump", "xmltree", apk, "--file", xml_resource_path("share_paths")])
    assert "exports/" in paths and "root-path" not in paths and "external-path" not in paths
    badging = run("apk-badging.txt", [aapt, "dump", "badging", apk])
    if args.distribution:
        assert_distribution(manifest, badging, args.distribution)
    run("apk-signature.txt", [tool_path("apksigner", sdk), "verify", "--verbose", "--print-certs", apk])
    run("zipalign.txt", [tool_path("zipalign", sdk), "-c", "-P", "16", "-v", "4", apk])
    run("elf-apk.txt", [sys.executable, HERE / "release_check.py", apk])
    aab_unsigned = None
    aab_distribution = None
    if not args.skip_aab:
        signature = run("aab-signature.txt", [tool_path("jarsigner", java), "-J-Duser.language=en", "-verify", aab])
        aab_unsigned = aab_signature_state(signature, args.expect_unsigned_aab)
        run("elf-aab.txt", [sys.executable, HERE / "release_check.py", aab])
        if args.bundletool:
            if not args.distribution or not args.bundletool.is_file():
                raise ValueError('AAB manifest verification needs a valid --bundletool and --distribution.')
            bundle_manifest = run('manifest-aab.xml', [tool_path('java', java), '-jar', args.bundletool.resolve(),
                                                       'dump', 'manifest', '--bundle=' + str(aab), '--module=base'])
            aab_distribution = assert_bundle_distribution(bundle_manifest, badging, args.distribution)
            if args.admob_app_id:
                android = '{http://schemas.android.com/apk/res/android}'
                tree = ET.fromstring(bundle_manifest)
                metadata = {node.get(android + 'name'): node.get(android + 'value')
                            for node in tree.findall('application/meta-data')}
                assert metadata.get('com.google.android.gms.ads.APPLICATION_ID') == args.admob_app_id
                assert metadata.get('com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT') == 'true'
                permissions = {node.get(android + 'name') for node in tree.findall('uses-permission')}
                assert {'android.permission.ACCESS_NETWORK_STATE', 'com.google.android.gms.permission.AD_ID'} <= permissions
    baseline_result = {"status": "skipped", "reason": "No --baseline supplied"}
    if args.baseline:
        with zipfile.ZipFile(args.baseline.resolve()) as old, zipfile.ZipFile(apk) as new:
            names = [name for name in old.namelist() if name.endswith((".so", ".tflite", ".task"))]
            assert names, "Baseline has no native/model files to compare"
            for name in names:
                assert old.read(name) == new.read(name), name
        baseline_result = {"status": "pass", "files_compared": len(names), "baseline": str(args.baseline.resolve())}
    verdict = {"status": "pass", "distribution_checked": args.distribution, "backup_domains_excluded_in_both_modes": sorted(domains),
               "compiled_apk_has_internet_permission": True, "allowBackup": False, "fullBackupContent": False,
               "baseline_comparison": baseline_result, "aab_checked": not args.skip_aab, "aab_unsigned": aab_unsigned,
               "physical_OEM_transfer_tested": False, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
               "aab_distribution": aab_distribution, "admob_app_id_checked": args.admob_app_id,
               "aab_sha256": hashlib.sha256(aab.read_bytes()).hexdigest() if not args.skip_aab else None}
    (evidence / "package-verdict.json").write_text(json.dumps(verdict, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(verdict, indent=2))

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        verdict = {"status": "fail", "error": str(error)}
        (evidence_dir() / "package-verdict.json").write_text(json.dumps(verdict, indent=2) + "\n", encoding="utf-8")
        raise
