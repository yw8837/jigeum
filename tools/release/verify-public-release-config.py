"""Verify public runtime endpoints and public key in each final APK/AAB DEX.

No private key, invitation code, or purchase token is read or logged.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

VERIFY = 'https://jigeum-verifier-th7ce7roma-du.a.run.app/verify'
COMMUNITY = 'https://baby.yeolmaeapps.kr/'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--aab', type=Path, required=True)
    p.add_argument('--public-key', type=Path, required=True)
    args = p.parse_args()
    key = args.public_key.read_text().strip()
    assert len(key) > 50
    results = []
    for file in (args.apk, args.aab):
        with zipfile.ZipFile(file) as archive:
            dex = b'\0'.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
        found = {name: value.encode()+b'\0' in dex
                 for name, value in [('verification_endpoint', VERIFY), ('community_endpoint', COMMUNITY), ('public_key', key)]}
        results.append({'file': str(file), 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'checks': found})
    okay = all(all(r['checks'].values()) for r in results)
    print(json.dumps({'status': 'pass' if okay else 'fail', 'artifacts': results}, indent=2))
    raise SystemExit(0 if okay else 1)


if __name__ == '__main__':
    main()
