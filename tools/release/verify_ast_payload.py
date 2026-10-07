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
CANDIDATE_MODEL = 'ast-infant-presence-candidate.onnx'
CANDIDATE_SIZE = 91_138_597
CANDIDATE_SHA = '4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a'
CAPTION_HEAD = 'ast-caption-presence.f32'
CAPTION_HEAD_SHA = '979e79448a5b27293c02642eb8a72ccc6fbf322fd16cc9ee43a467e7434c2df1'


def verify(path: Path, expected: str, model_variant: str = 'base') -> dict:
    if model_variant not in {'base', 'infant-candidate', 'infant-caption'}:
        raise ValueError('Unknown model variant')
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
        model_name, model_size, model_sha = ((CANDIDATE_MODEL, CANDIDATE_SIZE, CANDIDATE_SHA)
            if model_variant in {'infant-candidate', 'infant-caption'} else (MODEL, MODEL_SIZE, MODEL_SHA))
        model = prefix + 'assets/' + model_name
        models = {name for name in relevant if name.endswith('.onnx')}
        if models != {model}:
            raise ValueError('Require exactly the selected AST graph, without duplicate or substituted model')
        if model not in names:
            raise ValueError('Pinned AST model is missing')
        if archive.getinfo(model).file_size != model_size:
            raise ValueError('Pinned AST model size mismatch')
        with archive.open(model) as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != model_sha:
                raise ValueError('Pinned AST model hash mismatch')
        caption = prefix + 'assets/' + CAPTION_HEAD
        caption_notice = prefix + 'assets/AST_CAPTION_NOTICE.txt'
        if model_variant == 'infant-caption':
            if caption not in names or archive.getinfo(caption).file_size != 3076:
                raise ValueError('Frozen caption head missing or wrong size')
            if hashlib.sha256(archive.read(caption)).hexdigest() != CAPTION_HEAD_SHA:
                raise ValueError('Frozen caption head SHA mismatch')
            if caption_notice not in names or not archive.read(caption_notice).strip():
                raise ValueError('Caption source notice missing')
        elif caption in names or caption_notice in names:
            raise ValueError('Unexpected caption payload for the selected model variant')
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
            'model_sha256': model_sha, 'model_variant': model_variant, 'abis': sorted(ABIS), 'payload_entries': relevant}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--expect', choices=['present', 'absent'], required=True)
    parser.add_argument('--model-variant', choices=['base', 'infant-candidate', 'infant-caption'], default='base')
    args = parser.parse_args()
    try:
        report = verify(args.archive, args.expect, args.model_variant)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print(json.dumps({'status': 'fail', 'error': str(error)}))
        raise SystemExit(1)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
