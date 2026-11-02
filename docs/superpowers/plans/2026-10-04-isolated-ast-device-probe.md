# Isolated AST Device Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Measure the fixed AST candidate on an ARM phone without replacing the user's app or reading baby media.

**Architecture:** Compile the existing AST debug source with an explicit standalone application ID and a restrictive manifest overlay. A synthetic instrumentation benchmark records complete 10/20-second calls, sampled PSS and thermal status. A host runner verifies APK identity, installed hashes, fresh run evidence and cleanup.

**Tech Stack:** Kotlin, Android instrumentation, existing ONNX Runtime, Python stdlib, ADB.

**Spec:** Existing on-device perception plan `2026-10-04-perception-recovery.md`; fixed AST runtime/quality boundaries in `../../qa/ondevice-av-generation/ANDROID-AST-QA.md`. This is a research measurement tool; no product activation or Play submission.

## Global Constraints

- Existing `com.moa.babylanguage` must not be installed, removed, force-stopped or modified by the runner.
- Research package is exactly `com.moa.babylanguage.astprobe`; test package adds `.test`.
- Standalone property requires `agattokAstProbe=true`, is debug only, and removes internet/camera/microphone/storage/billing permissions.
- Use existing pinned 91,134,123-byte AST model; no model or SDK upgrade.
- Only on-device synthetic PCM. No input-media transfer or reading user records.
- Never infer audio meaning or whole-product performance from this benchmark.
- Serial is explicit; no device fallback or model-name allow list. Device absent means no execution.

## Review Focus

- Wrong APK/test target: reject before installation.
- Old probe package: refuse to overwrite; retain existing evidence.
- Timeout or invalid result: fail and clean up only the two exact research packages.
- Model error/short coverage/old run ID: never count as a completed measurement.
- Cleanup or production-package snapshot mismatch: fail even if instrumentation passed.

## Task 1 — Package isolation and measurement

Files: `outputs/MoaAndroid/app/build.gradle.kts`, `app/src/astStandalone/AndroidManifest.xml`,
`app/src/astProbeAndroidTest/java/com/moa/babylanguage/AstDeviceBenchmarkTest.kt`.

- [x] Add the explicit standalone property, validate its dependency on the research property, and keep defaults unchanged.
- [x] Overlay a distinct label, disabled launcher and removed permissions; build debug/test APKs.
- [x] Require opt-in and fresh 32-hex run ID. Measure synthetic 10s first call, 20s and 20s repeat through the existing analyzer; store actual coverage, elapsed time, PSS samples and thermal states. OS file cache is not reset.
- [x] Reuse existing real-native cancellation/recovery tests. Keep the benchmark opt-in in full suites.
- [x] Inspect final manifest/package/test target; verify default build still has original app ID and no AST dependency.

## Task 2 — Bounded host execution and device QA

Files: `tools/research/ast_device_benchmark.py`, `tools/research/test_ast_device_benchmark.py`.

- [x] Reject wrong identities, permissions, stale installs/results and malformed metrics before accepting evidence; unit-test these failure modes.
- [x] Save local/source/installed provenance. Run only selected instrumentation tests on the requested serial.
- [x] Retrieve fresh benchmark JSON; sample statistics do not equal absolute hardware peaks.
- [x] Stop/remove only newly installed probe packages and verify absence, plus unchanged production install metadata. In this emulator boot production was absent before/after; no record-preservation claim.
- [x] Execute on the connected phone when available; otherwise record build/host verification only, leaving ARM results unverified. S25 USB currently absent; emulator execution completed and reconnect requested.
- [x] Write actual QA results and review. Commit/push this plan with the verified changes; compare local/remote SHA before reporting delivery.

## Progress and rulings

2026-10-04: [QA evidence](../../qa/ondevice-av-generation/AST-DEVICE-PROBE-QA.md).
Host cleanup/APK race P2 findings were reproduced RED and fixed; 12 tests passed and Astra read-only re-review cleared both.
Actual JUnit initialization failed on non-void inferred Kotlin return; `runBlocking<Unit>` fixed it and the emulator completed
one benchmark (three measurements) plus two native cancellation/recovery checks. An intervening SDK-path environment failure
was corrected without changing the SDK. Default APK build and two forbidden-build gates passed.
Final Sol review found one completeness P2 in the separate delta audit; expected source×mode coverage now rejects missing
paired records, RED→GREEN10 tests and actual audit run-03 passed. Combined host/delta tests:22 passed.

Ruling: first-call timing means first analyzer call in the process, not a cleared OS cache or whole-product launch measurement.
This avoids claiming hardware cold-start/phone performance from an emulator. Phone absence does not block independent paired audio evaluation.
