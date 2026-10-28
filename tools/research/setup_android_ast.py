"""Stage the already exported, pinned INT8 AST into debug research assets. No downloads.

Usage: python tools/research/setup_android_ast.py --model work/ast-portability/int8-03/ast-dynamic-int8.onnx
The source and destination are streamed, never read into a 91MB byte array.
These assets are included only with -PagattokAstProbe=true; that property blocks
release/beta/preview variants and does not change the production decision gate.
"""
import argparse
import hashlib
import os
from pathlib import Path
import tempfile

SIZE = 91_134_123
SHA256 = "3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e"


def stage(source: Path, destination: Path):
    if not source.is_file() or source.stat().st_size != SIZE:
        raise ValueError("AST source absent or pinned size mismatch")
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix="ast-", suffix=".partial", dir=destination.parent)
    try:
        digest, count = hashlib.sha256(), 0
        with os.fdopen(descriptor, "wb") as output, source.open("rb") as input_file:
            while chunk := input_file.read(64 * 1024):
                count += len(chunk)
                if count > SIZE:
                    raise ValueError("AST source exceeds pinned size")
                digest.update(chunk)
                output.write(chunk)
            if count != SIZE or digest.hexdigest() != SHA256:
                raise ValueError("AST source SHA256 mismatch")
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    target = root / "outputs/MoaAndroid/app/src/astProbe/assets/ast-dynamic-int8.onnx"
    stage(args.model.resolve(), target)
    print(f"Staged debug-only AST probe: {SIZE} bytes, SHA256 {SHA256}")


if __name__ == "__main__":
    main()
