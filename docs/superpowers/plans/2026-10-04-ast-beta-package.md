# Explicit AST Beta Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local signed 0.5.11/code25 beta with explicitly enabled fixed AST babble support, while ordinary builds retain AST exclusion.

**Architecture:** `agattokAstBeta=true` selects the existing verified AST sources/assets/runtime and enables a beta-only BuildConfig flag. A task-graph guard rejects non-beta variants before execution, including mixed invocations. The signing wrapper forwards the opt-in only for closed-test purpose.

**Tech Stack:** Android Gradle Kotlin DSL, Kotlin, PowerShell, ONNX Runtime Android 1.24.3.

**Spec:** Parent task instructions for explicit AST beta packaging; `docs/HANDOFF.md` supplies existing product boundaries. S25 UI/full-AV results are maintained by the parent in its QA documentation.

## Global Constraints

- Local signed candidate only; no Play publication is authorized for this partial model scope.
- Fixed AST model/source/notices; no threshold, fact, access-limit, runtime-internal, entitlement or offline-boundary changes.
- `BabbleSupportPolicy.ENABLED` stays false; beta `AST_INTEGRATED_CANDIDATE` stays false.
- Default version is code25/0.5.11; preserve older artifacts and reject existing destinations.
- This worker owns only this plan, app `build.gradle.kts`, `ClipEvidence.kt`, and `tools/release/build-play-bundle.ps1`; other workers' edits must be preserved.
- Parent exclusively runs Gradle, ADB, signing and commits, and owns verification code/results.

## Review Focus

- Absent/false opt-in must preserve no-AST builds and all research isolation guards.
- Malformed property values and true research flags mixed with true beta opt-in must fail configuration.
- Mixed beta and debug/release/preview graphs must fail before any task executes.
- Missing/substituted AST weights must fail verification before beta asset merge.
- Wrapper non-closed-test purpose with `-AstBabbleSupport` must fail before signing-secret access.

---

### Task 1: Gradle isolation and runtime route

**Files:** Modify `outputs/MoaAndroid/app/build.gradle.kts` and `outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt`.

**Interfaces:** Consume existing `astProbeEnabled`, AST source directories and `verifyAstModel`; produce `agattokAstBeta`, beta-only `BuildConfig.AST_BABBLE_SUPPORT`, and version25/0.5.11.

- [x] Parent establishes RED from an existing ordinary beta: no AST model/runtime and false runtime flags.
- [x] Validate explicit true/false opt-in; reject enabled research flags in combination; select real AST sources/assets/ORT only for probe or beta opt-in.
- [x] Set the new flag default false and beta true only for opt-in; extend only the current analyzer route condition.
- [x] Reject debug/release/preview tasks in the realized graph before execution and add beta asset-merge model verification.
- [x] Parent runs GREEN on opted-in beta, default/release controls and negative property/task combinations; confirms model/notices/dex/flags and signed actual UI/upgrade.

Expected commands from `outputs/MoaAndroid`: `./gradlew.bat :app:bundleBeta :app:assembleBeta -PagattokAstBeta=true`; default `:app:assembleRelease` excludes AST. Opted-in `:app:assembleBeta :app:assembleDebug` fails before task execution. Invalid `-PagattokAstBeta=1` and opt-in plus `-PagattokAstProbe=true` fail configuration. Opted-in beta with absent/wrong model fails `verifyAstModel`.

### Task 2: Explicit signing wrapper input

**Files:** Modify `tools/release/build-play-bundle.ps1`.

**Interfaces:** Consume `-Purpose closed-test`; produce optional `-AstBabbleSupport` switch forwarding literal `-PagattokAstBeta=true` in a Gradle argument array.

- [x] Parent pins wrapper input/forwarding failures in its verifier.
- [x] Reject `-AstBabbleSupport` with other purposes before environment/key handling; preserve ordinary argument list, existing parameter positions and destination guards.
- [x] Parent runs wrapper/static checks and the local signed beta build to a fresh `*-beta-code25.aab` destination.
- [x] Parent records artifact SHA, signing/dex checks, UI/upgrade gates and commits owned files together with QA.

Expected forbidden cases: `-Purpose internal -AstBabbleSupport` and `-Purpose paid-candidate -AstBabbleSupport` fail without reading signing secrets. Ordinary wrapper calls keep current behavior.

## Execution Ledger

- Ruling: Parent task explicitly reserves test execution/signing/commits and authorizes autonomous implementation. This worker records code completion separately from parent validation; no skill-generated workspace or extra owned files are created.
- Ruling: Research mixing means enabled (`true`) research flags; explicit `false` flags remain valid because they select no research behavior.
- Pre-flight: Task 2 forwards the exact `agattokAstBeta=true` interface produced by Task 1; the analyzer consumes the generated `AST_BABBLE_SUPPORT` field. No mismatches found.
- Parent validation and publication status: pending; this plan claims no executed Android/signing gates.
- Implementation delivered: all three owned source files updated; source diff whitespace check and PowerShell parser passed. Gradle/ADB/signing/commits were not run by this worker.
- Parent RED: opted-in beta build failed before execution (`work/ast-beta-package-01/build-beta.log`): substring guard falsely classified `:app:stripBetaDebugSymbols` and `:app:mergeBetaNativeDebugMetadata` as debug variant work.
- Ruling: Exempt only those two exact AGP beta native-metadata task names. They operate on beta symbols; actual debug/release/preview task objects continue to be rejected before execution, including mixed graphs. Unknown future task names still fail closed and require review.
- Narrow fix delivered in Gradle guard only; parent rerun of the same beta build and negative mixed graphs is pending. This worker did not run Gradle/ADB.

## Parent completion — 2026-10-04

The explicit beta, default/release exclusion, malformed/mixed-property guards, model absence/corruption,
signatures, native alignment, compiled flags, signed23→25 update, final signed UI and known-case route passed.
Final authoritative files are in ignored `work/ast-beta-package-03/`; the QA report records exact hashes.
The user's later warning-remediation request expanded the original fixed-runtime scope: fixed-delay watchdog,
compatible dependency updates, Kotlin/KTX cleanup and track-preserving Media3 migration were reviewed and tested.
Fixed AST weights, v2 decision thresholds, offline boundary and access limits remain unchanged.
Earlier failed build and intermediate candidate evidence were retained; they are not final delivery files.
Temporary AVD/state cleanup is confirmed. Play publication and the full AV-generation goal remain incomplete.
