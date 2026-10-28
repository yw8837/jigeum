# AST integrated local candidate — implementation plan

2026-10-04 · baseline `17ca360` · implementation owner Astra; execution owner parent

## Scope

- Explicit `agattokAstCandidate=true` together with `agattokAstProbe=true` selects debug-only `com.moa.babylanguage.astcandidate`. Reject standalone + candidate together.
- Candidate keeps a usable launcher and a distinct app/activity label; full closed-test access is 120s capture / 20s analysis. No permanent entitlement and no network permission.
- Default debug/beta/release and existing standalone behavior remain unchanged. Block candidate bundles and all research beta/release/preview variants.
- Keep fixed AST weight, ORT and `BabbleSupportPolicy.kt` unchanged. Add a default-false BuildConfig opt-in at the existing DeviceClipAnalyzer branch only. AST failure retains baseline, cancellation propagates, existing PCM clearing and 45s UI timeout remain.
- No original media in assets; known-case measurement reads only an explicitly provided local manifest. No deployment or quality/AV-generation completion claim.

## Steps and verification

1. Write androidTest integration checks first: actual DeviceClipAnalyzer reaches the AST model-store path on a synthetic fixture; an AST-path cancellation propagates; a model-store failure falls back to the baseline; bundled model hash/config are correct. Parent owns RED/GREEN execution.
2. Add the minimal property/debug BuildConfig/package/manifest gates and actual analyzer opt-in.
3. Add opt-in local-manifest known-case 10s/20s integration measurement. Fixed cases and source hash; fresh run; actual DeviceClipAnalyzer only; no manually attached policy. Write local results with honest assertions and no private logging.
4. Parent verifies candidate build/test APK identities, model/ORT presence and SHA; default absence; mixed-property/forbidden-variant/bundle rejection. Reuse existing CaptureFlow cancellation/recovery and record/media preservation regressions on the candidate package.

## Execution handoff

- Candidate: `assembleDebug assembleDebugAndroidTest -PagattokAstProbe=true -PagattokAstCandidate=true`.
- Tests: `AstIntegratedCandidateTest` with `-e ast_integrated_probe true`; optional known case additionally needs `-e ast_integrated_known true -e ast_integrated_run_id <32 lowercase hex>`.
- Known manifest: target filesDir `qa-ast-integrated/<run-id>/manifest.json`, schema recorded in the test source. No source bundled or selected by a user-facing flow.
- Actual UI: `AstIntegratedUiTest#actualTwentySecondAnalysisCancelsAndNextAnalysisCompletesWithoutChangingInput`, args `ast_integrated_ui=true` and `ast_integrated_ui_run_id=<32 lowercase hex>`. An androidTest observer delegates all results unchanged to DeviceClipAnalyzer. Actual 20s AST-path entry, main-thread heartbeat, UI cancellation, next analysis completion, no tone phrase/save and input SHA preservation are checked. Evidence is `files/qa-ast-integrated-ui/<run-id>.json`. Tone abstention is explicitly not proof of native AST success.
- Forbidden: candidate without research property, candidate + standalone, candidate beta/release/preview, `bundleDebug`/`packageDebugBundle` under candidate.

## Ledger

- Ruling: parent exclusively owns Gradle/ADB/model execution and commits; implementer writes tests first and sends RED instructions, without running these tools or claiming test success. Parent-requested file ownership overrides skill scratch/commit conventions; this plan is the local progress ledger.
- Pre-flight: BuildConfig opt-in consumes the debug-only candidate property; androidTest uses the debug target and shared AST source sets. No new build type is needed.
- Parent ran the two route tests on the disabled standalone build: both failed specifically `expected:<1> but was:<0>` (AST model-store call count). Build succeeded; no fixture/setup failure. Evidence `work/ast-integrated-candidate-01/disabled`. This is a disabled/enabled integration counterfactual, not a claim that a pre-implementation TDD RED was observed.
- Implementation prepared: candidate debug property/config/package/manifest, one analyzer condition change, bundled-hash check, actual-route cancellation/fallback checks and explicit known 10s/20s full AV path. Candidate GREEN/build gates, actual UI and ARM evidence pending with parent.
- Parent subsequently reported all 7 selected forbidden-property/variant/bundle gates rejected as expected. Candidate build, enabled integration checks and actual UI execution are parent-owned and pending at this handoff. No implementer Gradle/ADB/model execution or commit occurred.
- Parent execution completed: [local integration QA](../../qa/ondevice-av-generation/AST-INTEGRATED-CANDIDATE-QA.md). Initial JUnit return-type and silent-fixture selection errors were repaired; 4 integration + 1 actual UI tests passed on retry. The unchanged app APK already passed 81 native/access/media/timeline regressions. Default APK/dex isolation and 7 forbidden configuration/build gates passed. Known development case recovered through the real entry point at10/20s; ARM and product adoption remain pending. No deployment or general AV-generation completion is claimed.
