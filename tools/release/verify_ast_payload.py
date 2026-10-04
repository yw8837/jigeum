"""Verify pinned AST/ORT payload presence or exclusion in a final APK/AAB.

This checks archive contents only. Signature, distribution, compiled flags and
actual execution need their separate package/runtime checks.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

MODEL = 'ast-dynamic-int8.onnx'
MODEL_SIZE = 91_134_123
MODEL_SHA = '3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e'
NOTICES = {
    'ONNX_RUNTIME_LICENSE.txt': '2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c',
    'ONNX_RUNTIME_THIRD_PARTY_NOTICES.txt': '0e07b95f3a8d6230037707c5c4a2b554d12c4cb67369669ac255635528ffcee2',
}
ABIS = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}


def verify(path: Path, expected: str) -> dict:
    if expected not in {'present', 'absent'} or path.suffix.lower() not in {'.apk', '.aab'}:
        raise ValueError('Require present/absent and an APK/AAB')
    prefix = 'base/' if path.suffix.lower() == '.aab' else ''
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive paths')
        relevant = sorted(n for n in names if n.startswith(prefix) and (
            n[len(prefix):].startswith('assets/ast-') or
            n[len(prefix):].startswith('assets/AST_') or
            n[len(prefix):].startswith('assets/ONNX_RUNTIME_') or
            (n[len(prefix):].startswith('lib/') and 'onnxruntime' in n)))
        if expected == 'absent':
            if relevant:
                raise ValueError('Unexpected AST/ORT payload: ' + ', '.join(relevant))
            return {'status': 'pass', 'expected': expected, 'archive': str(path), 'payload_entries': []}
        model = prefix + 'assets/' + MODEL
        if model not in names:
            raise ValueError('Pinned AST model is missing')
        if archive.getinfo(model).file_size != MODEL_SIZE:
            raise ValueError('Pinned AST model size mismatch')
        with archive.open(model) as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != MODEL_SHA:
                raise ValueError('Pinned AST model hash mismatch')
        for name, wanted in NOTICES.items():
            if hashlib.sha256(archive.read(prefix + 'assets/' + name)).hexdigest() != wanted:
                raise ValueError('Upstream notice differs: ' + name)
        for name in ('AST_MODEL_NOTICE.txt', 'ONNX_RUNTIME_NOTICE.txt'):
            if not archive.read(prefix + 'assets/' + name).strip():
                raise ValueError('Missing source notice: ' + name)
        wanted_native = {f'{prefix}lib/{abi}/{library}' for abi in ABIS
                         for library in ('libonnxruntime.so', 'libonnxruntime4j_jni.so')}
        found_native = {n for n in names if n.startswith(prefix + 'lib/') and 'onnxruntime' in n}
        if found_native != wanted_native:
            raise ValueError('ORT ABI payload differs from the pinned full Android AAR')
    return {'status': 'pass', 'expected': expected, 'archive': str(path),
            'model_sha256': MODEL_SHA, 'abis': sorted(ABIS), 'payload_entries': relevant}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--expect', choices=['present', 'absent'], required=True)
    args = parser.parse_args()
    try:
        report = verify(args.archive, args.expect)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print(json.dumps({'status': 'fail', 'error': str(error)}))
        raise SystemExit(1)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
