# 0.4 access policy implementation candidate

Base: `cc8403508dfb311e57c12d4b5d9f1226618ad851`. Work remains unbuilt and unverified on Android. This is not a release, actual Play purchase result, or finished device QA.

## Changes

- Explicit `AccessFeature` / `AccessPolicy` classifies free actions and checks the existing billing manager for camera, stored video and PDF at execution time.
- Root camera/video branches are conditional on billing display state; runtime checks also guard permission results, sensor/model start, frame inference, video URI results, decode stages and completed observations. Display state alone never starts a protected operation.
- Expiration/revocation sends paid media screens to the pass explanation and disposes CameraX/model resources and the video coroutine scope. Each screen owns an irreversible media generation; disposed or denied callbacks cannot create a draft or redirect a newer screen.
- Requested feature, paywall origin and media origin survive recreation. Restore success offers an explicit continue button; it never starts capture automatically.
- Draft editor and generated candidates are saved per expression session; revisiting a denied recapture preserves edits and candidates. Editing/saving existing drafts and records remains free.
- PDF checks current entitlement immediately before generation. Once generation starts, entitlement expiry alone does not cancel it.
- Camera/video buttons and pass/guide screens disclose the unified paid bundle and retained free functions. Actual Play prices remain the only purchase-button prices. The read-only terms draft is explicitly not final or in force.
- PNG/PDF capture a generation before coroutine dispatch and pass it through render/share. Deletion invalidates synchronously; root database mutations block new PNG and PDF exports (`!busy` for PNG; `exporting = sharing || busy` for PDF). This does not claim a new mutation gate for JSON/CSV. These calls require the companion `MemoryArtifacts` generation-safety change.

## Verification performed

`AccessPolicyTest` contains five instrumented tests covering free actions bypassing billing, all three paid actions rechecking current access, expiration, disposal, denial remaining invalid after later restoration, and stale callbacks not redirecting a new screen. Those Android tests have not run.

The equivalent host runner `outputs/QA/access_policy_check.kt` compiled the actual production `AccessPolicy.kt` with Kotlin 2.2.10 on OpenJDK 21.0.12.1 and passed **5/5 checks**. Two intentionally broken local copies (unconditional paid access and removed closed-generation guards) both failed expected assertions; the final unmodified implementation rerun passed **5/5**. These mutation failures are expected negative controls, not unresolved failures. Full output: [host test log](access-policy-host-tests.log).

The cloud runtime initially lacked a compiler. After explicit user approval, the official Maven Kotlin compiler was downloaded and run for the host checks above. The Android SDK and emulator remain unavailable, and the Gradle test invocation was blocked before execution. No Android/Compose compilation, instrumentation tests, APK build, lint, UI screenshot or real Play transaction is reported as passed. Host tests and static source review do not verify those stages.

Host reproduction with the approved compiler and its dependencies on `TOOLS`:

```sh
java -cp "$TOOLS/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
  -classpath "$TOOLS/kotlin-stdlib-2.2.10.jar" -d access-policy.jar \
  outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/AccessPolicy.kt \
  outputs/QA/access_policy_check.kt
java -cp "access-policy.jar:$TOOLS/kotlin-stdlib-2.2.10.jar" \
  com.moa.babylanguage.Access_policy_checkKt
```

Compiler jars and generated test artifacts are not source deliverables and must not be committed.

## Required device regression

1. Free recording/manual input → edit/save → PNG/JSON/CSV without a pass screen.
2. Non-entitled camera/video entry: explanation before permission or picker; restore locked route without starting model/sensor.
3. Expire/revoke during permission, CameraX provider initialization, active camera, picker, frame/audio decode; late callbacks cannot navigate or produce new draft.
4. Edited paid-source draft → recapture → denied paywall → close/back → same edits and candidates; save remains available. Repeat after process recreation.
5. HOME, TRANSLATE, ALBUM and GUIDE return destinations survive policy/terms round trips. Successful purchase/restore requires explicit continue.
6. Start PDF while valid → expiry → complete; subsequent PDF blocked. Delete during PNG/PDF generation → stale file cannot open share chooser.
7. Small/wide screens, 200% text, back/forward/cancel, repeated taps, permission denial and background transitions.

Native inference already in progress cannot be synchronously interrupted by entitlement expiry; subsequent stages are canceled/guarded and no late result is accepted. Verify cleanup and timing on real devices.
