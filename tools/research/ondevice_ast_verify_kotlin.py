"""Compare the staged Kotlin frontend against pinned FP32 NumPy features.

Java/Kotlin compiler are externally supplied, no Gradle/ADB or downloads.
PCM and full features stay in a fresh Git-ignored work/ output directory.
"""
import argparse
import json
from pathlib import Path
import subprocess
import wave

import numpy as np
from ondevice_ast_features import extract, synthetic_pcm
from ondevice_audio_candidate import window_ranges
from ondevice_av_probe import validate_bundle, validate_output_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--cases")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    target = Path(args.output).resolve()
    validate_output_path(target)
    signals = {f"synthetic_{kind}_{ms}": synthetic_pcm(ms, kind)
               for ms in (1000, 2847, 10000) for kind in ("tone", "sweep", "silence", "impulse")}
    if args.cases:
        import re
        mapping = json.loads(Path(args.cases).read_text(encoding="utf-8"))
        if not isinstance(mapping, dict) or not mapping:
            raise ValueError("Require an anonymous case mapping")
        for name, path in mapping.items():
            if not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z0-9_-]+", name):
                raise ValueError("Invalid case ID")
            bundle = json.loads(Path(path).read_text(encoding="utf-8"))
            validate_bundle(bundle, audio_only=True)
            with wave.open(bundle["audio"], "rb") as wav:
                signals[name] = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32) / 32768
    target.mkdir(parents=True, exist_ok=False)
    report = dict(android_validated=False, feature_tolerance=2e-5, windows=[])
    for name, samples in signals.items():
        for a, b in window_ranges(len(samples)):
            pcm, features = target / f"{name}-{a // 16}-pcm.f32", target / f"{name}-{a // 16}-features.f32"
            samples[a:b].tofile(pcm)
            subprocess.run([args.java, "-Xmx128m", "-cp", args.classpath,
                            "research.ast.AstFrontendProbe", str(pcm), str(features)], timeout=30, check=True)
            actual = np.fromfile(features, dtype="<f4")
            if actual.size != 1024 * 128 or not np.isfinite(actual).all():
                raise RuntimeError("Kotlin produced invalid features")
            difference = float(np.max(np.abs(extract(samples[a:b]).ravel() - actual)))
            report["windows"].append(dict(case_id=name, start_ms=a // 16, end_ms=b // 16,
                                          max_abs=difference, passed=difference <= 2e-5))
    report["passed"] = all(w["passed"] for w in report["windows"])
    (target / "parity.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(dict(windows=len(report["windows"]), passed=report["passed"],
                          feature_max_abs=max(w["max_abs"] for w in report["windows"]))))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
