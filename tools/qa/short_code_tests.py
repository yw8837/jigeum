"""Run access-code/billing regression on one owned emulator with exact APK provenance."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'outputs/QA'))
from instrument import installed_provenance, local_provenance, parse_result

CLASSES = ['AccessCodeTest', 'ShortAccessCodeTest', 'AccessCodeUiTest',
           'ReviewAccessVerifierTest', 'ReviewAccessLifecycleTest', 'BillingManagerLifecycleTest']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'): parser.error('Use only an owned emulator')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [str(args.adb), '-s', args.serial]
    provenance = local_provenance(args.apk, args.test_apk, ROOT)
    for role, package in [('app', 'com.moa.babylanguage'), ('test', 'com.moa.babylanguage.test')]:
        observed = installed_provenance(adb, package, provenance[role + '_apk']['sha256'], role == 'app')
        provenance[role + '_installed'] = observed
        if not observed['verified']: raise RuntimeError('Installed QA artifact mismatch')
    started = time.monotonic()
    try:
        result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            ','.join('com.moa.babylanguage.' + name for name in CLASSES),
            'com.moa.babylanguage.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, timeout=240)
        raw = (result.stdout + result.stderr).decode('utf-8', 'replace')
        code = result.returncode
    except subprocess.TimeoutExpired as error:
        raw = ((error.stdout or b'') + (error.stderr or b'')).decode('utf-8', 'replace') + '\nHARNESS TIMEOUT\n'
        code = None
    verdict = parse_result(raw, code, time.monotonic() - started)
    verdict.update(completed_at=datetime.now(timezone.utc).isoformat(), provenance=provenance,
                   actual_play_purchase_tested=False)
    (args.output / 'raw.txt').write_text(raw, encoding='utf-8')
    (args.output / 'result.json').write_text(json.dumps(verdict, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: verdict[k] for k in ('status', 'tests', 'failed_tests', 'ignored_tests', 'validation_errors')}))
    return 0 if verdict['status'] == 'pass' else 1

if __name__ == '__main__': raise SystemExit(main())
