# On-device audiovisual feasibility implementation plan

> **For agentic workers:** Execute this phase inline with superpowers:executing-plans. Do not dispatch implementers or touch the Play release. Phase A establishes whether the model path actually works before Android integration.

**Goal:** Produce a repeatable, local-only Gemma4 E2B probe and truthful evidence for choosing whether to build the Android path.

**Architecture:** Decode only the selected 1–20s interval into continuous WAV and timestamped frames, submit both to pinned LiteRT-LM locally, and write private responses only to ignored local storage. Keep a model-independent input/response validator and a subprocess deadline around native inference.

**Tech Stack:** Python stdlib, FFmpeg/FFprobe, litert-lm-api 0.17.1, pinned Gemma4 E2B .litertlm.

**Spec:** ../specs/2026-10-03-ondevice-av-generation-design.md

## Global constraints

- No external baby media, extracted frames/audio, prompts, embeddings or records.
- Models/downloads/runtimes/private outputs live under ignored work/; no API inference or credentials.
- First model: revision b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1, 2,588,147,712 bytes, SHA256 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c.
- Source app remains0.5.9/code23. No Android dependency, version, server or release changes in Phase A.
- User accepts size only if quality is satisfactory. A successful model load or JSON schema is not a quality pass.

## Review focus

1. File input with rotation, a delayed audio track or nonzero start: preserve timestamps and interval.
2. Blank/synthetic/no-baby input: no invented infant intent; refusal is not a model-load failure.
3. Frames reordered or mismatched with sound: measure whether the output actually uses temporal/AV evidence.
4. Native hangs or unsupported accelerator: bounded child process, report failure, no cloud/template fallback.
5. Private model output: local file only; public summary contains anonymous case IDs and counts, not images/transcripts.

### Task 1: Pin dependencies and build input/response contracts

**Files:** create tools/research/ondevice_av_probe.py; tools/research/test_ondevice_av_probe.py; tools/research/ondevice-av-model.json.

**Interfaces:** validate_interval(start_ms: int, end_ms: int) -> int; validate_result(value: dict, duration_ms: int) -> list[str]; verify_model(path: Path, manifest: dict) -> None.

- [ ] Write tests for 1/20s boundaries, invalid intervals, out-of-range timestamps, refusal without a phrase, generated output requiring referenced observations, malformed schema and model hash mismatch.
- [ ] Run unittest and preserve the expected failure before implementation.
- [ ] Implement validation and pinned manifest; test until these fail/pass cases are correct.

### Task 2: Actual model execution with public/synthetic inputs

**Files:** extend tools/research/ondevice_av_probe.py; create ignored work/ondevice-av/ runtime, media and outputs.

**Interfaces:** prepare_clip(source: Path, start_ms: int, end_ms: int, fps: int, target: Path) -> dict; run_local(model: Path, bundle: dict, target: Path, backend: str) -> dict.

- [ ] Install pinned runtime in a dedicated venv and download only the pinned model file.
- [ ] Check file SHA/size and inspect installed API before execution.
- [ ] Generate an unambiguous synthetic sequence; verify extracted PTS are within the selected interval.
- [ ] Run text-only loading sanity check, then combined audio/multiple image input. Start with low image count and1fps; do not claim20s success from a short smoke test.
- [ ] Use a600s per-process host ceiling,30s polling and local stdout/stderr files. Record exit code/load/inference elapsed, backend and OS.
- [ ] If failures are due to unsupported runtime/backend, diagnose the exact layer and try documented alternatives locally. Do not widen to remote inference.

### Task 3: Existing failure clips and result report

**Files:** tools/research/ondevice_av_probe.py; docs/research/20261003-ondevice-av-probe.md; docs/qa/ondevice-av-generation/PROBE-GATES.md.

- [ ] Run authorized private clips only after synthetic multimodal execution works. Use anonymous IDs; save full responses locally.
- [ ] Compare original order, reversed frame sequence, audio-only and audio+visual inputs where executable.
- [ ] Record malformed/withheld/generated/error states separately. No automatically asserted “correct baby intention.”
- [ ] Provide local parent review material and public aggregate limitations.
- [ ] Run a fresh read-only review of the research code/evidence; fix consequential defects before commit.
- [ ] Commit/push source harness and sanitized evidence only. Android implementation is a subsequent phase and requires successful native model execution plus a realistic device/quality path.

## Execution ledger

- 2026-10-03: user confirmed strictly on-device and size conditional on quality. Phase A is necessary to judge that condition.
- Ruling: isolate research in managed worktree codex/ondevice-av-probe; execute inline in the current conversation. Existing user authorization covers reversible implementation/probing; no repeated approval for each local test.
- Pre-flight: Task2 consumes the validated interval/hash/schema contract from Task1. Task3 consumes local result files from Task2. No production code or private input is committed.
