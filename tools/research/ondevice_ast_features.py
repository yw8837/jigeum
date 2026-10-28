"""Portable AST input specification, independent of torch/transformers.

Matches transformers 5.14.1 ASTFeatureExtractor's NumPy path, not a
generic log-mel frontend. PCM is float32 mono /32768 at exactly 16kHz.
Android implementation must preserve frame-local DC/preemphasis, symmetric
Hann, Kaldi mel-space triangles, natural log, then padding BEFORE normalize.
No automatic resampling, cleanup, amplitude normalization, or dither.
"""
import numpy as np

SAMPLE_RATE = 16000
FRAME_LENGTH = 400
HOP_LENGTH = 160
FFT_LENGTH = 512
MEL_BINS = 128
MAX_FRAMES = 1024
MEAN = -4.2677393
STD = 4.5689974
MEL_FLOOR = 1.192092955078125e-7


def mel_bank():
    """257 FFT bins; 20Hz..8kHz; 1127*ln(1+f/700); no area norm."""
    edges = np.linspace(1127.0 * np.log1p(20 / 700),
                        1127.0 * np.log1p(8000 / 700), MEL_BINS + 2)
    bins = 1127.0 * np.log1p(np.arange(257) * (SAMPLE_RATE / FFT_LENGTH) / 700)
    slopes = edges[None, :] - bins[:, None]
    differences = np.diff(edges)
    return np.maximum(0, np.minimum(-slopes[:, :-2] / differences[:-1],
                                    slopes[:, 2:] / differences[1:]))


def extract(samples):
    """Return C-contiguous float32 [1024,128], frame-major then mel-major.

    1s -> 98 real frames; 10s -> 998. Remaining log-mel rows are zero,
    so normalized padding equals -MEAN/(STD*2), rather than zero.
    FFT promoted to double, spectrum rounded complex64 before power, and
    log-mel rounded float32 before normalization to match the pinned source.
    """
    samples = np.asarray(samples)
    if samples.ndim != 1 or not 16000 <= samples.size <= 160000:
        raise ValueError("One AST window requires 1..10s of mono 16kHz PCM")
    if not np.issubdtype(samples.dtype, np.floating) or not np.isfinite(samples).all():
        raise ValueError("PCM must contain finite floating samples")
    frames = np.lib.stride_tricks.sliding_window_view(samples.astype(np.float64), FRAME_LENGTH)[::HOP_LENGTH].copy()
    frames -= frames.mean(axis=1, keepdims=True)
    frames[:, 1:] -= 0.97 * frames[:, :-1].copy()
    frames[:, 0] *= 0.03
    frames *= np.hanning(FRAME_LENGTH)
    spectrum = np.fft.rfft(frames, n=FFT_LENGTH, axis=1).astype(np.complex64)
    power = np.abs(spectrum, dtype=np.float64) ** 2
    features = np.log(np.maximum(MEL_FLOOR, power @ mel_bank())).astype(np.float32)
    padded = np.zeros((MAX_FRAMES, MEL_BINS), dtype=np.float32)
    padded[:len(features)] = features[:MAX_FRAMES]
    return np.ascontiguousarray((padded - MEAN) / (STD * 2), dtype=np.float32)


def synthetic_pcm(duration_ms, kind="tone"):
    """Deterministic public test signals; never derived from owner media."""
    if type(duration_ms) is not int or not 1000 <= duration_ms <= 20000:
        raise ValueError("Synthetic interval requires integer 1000..20000ms")
    t = np.arange(duration_ms * 16, dtype=np.float64) / SAMPLE_RATE
    if kind == "silence":
        values = np.zeros(len(t))
    elif kind == "tone":
        values = 0.2 * np.sin(2 * np.pi * 440 * t)
    elif kind == "sweep":
        values = 0.15 * np.sin(2 * np.pi * (90 * t + 70 * t * t)) + 0.025
    elif kind == "impulse":
        values = np.zeros(len(t))
        values[::8001] = 0.5
    else:
        raise ValueError("Unknown synthetic signal")
    # Same quantization as PCM16 WAV decoding; not amplitude normalized.
    return (np.rint(values * 32767).astype(np.int16).astype(np.float32) / 32768)
