# Frozen caption head in research Android

2026-10-07. This follows the [measured pair](20261007-caption-wholebag-results.md).
Only an explicit debug research switch `agattokInfantCaptionCandidate=true` enables
the new head, requiring existing AST probe/candidate/infant-presence switches.
Beta/release/preview/bundles stay prohibited for this switch. Product code32 stays unchanged.

Reuse the existing 91,138,597-byte graph, unchanged original527 logits and original
infant_presence score. Request its existing768-vector in the same native run;
no second encoder, inference session, model download or new graph. Export the frozen
new NPZ into3076 little-endian bytes (768 FP32 weights then bias), pinned SHA
`979e79448a5b27293c02642eb8a72ccc6fbf322fd16cc9ee43a467e7434c2df1`.
L2 normalization and dot/bias/sigmoid are checked against the frozen NumPy path.
Missing/corrupt extra asset cannot remove base support; no caption score is allowed
in that fallback. Cancellation remains observable and tensor buffers are cleared.

Keep caption score separate and uncalibrated. Existing approvals, original model
labels and emotion/TV guards win; .8 threshold and .003 same-window cleaned1s RMS
stay unchanged. A supported head is a broad window clue, not exact infant onset,
speaker identity, babbling, emotion, hunger, sleepiness or intent. Full20s uses the
existing0–10/5–15/10–20s path. Do not convert window count into speech duration.
If the original head already has any valid support window, preserve its complete
range set and resulting phrase; additional caption windows must not change it.
Use caption windows only when the original head has no supported range.

QA: export provenance/shape/hash/no overwrite; native embedding parity and logits;
policy base-only/new-only/neither/invalid/quiet/guard and old approvals; real Android
public10+retained3+existing public/negative controls; actual decode/AV and native
cancel/recovery. Preserve media and original production install, scoped OMX lifecycle,
measured time/PSS and temporary copy cleanup. Main owns Gradle/ADB/Git.

Stop on old approval loss or new controls support; report failures rather than
retuning on the same examples. Two-axis independent review is required. PC/emulator
cannot approve physical ARM performance. Old APK proof must not approve the changed
runtime; beta needs a fresh reviewed APK/test and updated proof contract before
versionCode>32, packaging and actual closed-test update. Overall goal remains active.
