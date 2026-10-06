# UltraQA Report — frozen-pair research Android

2026-10-07. Scope: [debug contract](../research/20261007-caption-android-contract.md) and
[fresh ARM proof contract](../research/20261007-caption-arm-contract.md). Main owns Gradle/ADB/Git.
Product0.5.18/code32 is unchanged; new beta upload0. Physical ARM execution remains the next gate before beta. This limited QA covers
research-runtime behavior and preparation/refusal checks of the new physical proof,
not a passing real-phone measurement.

## Goal and safety bounds

Exercise the frozen3076B caption head in the actual existing Android native/policy/AV
path, preserve previous accepted observations and phrases, retain no new control
support, test cancellation/recovery and records/exports. Maximum5cycles, same failure3
stop. No user-media training/calibration/upload, additional encoder/session, threshold
retuning or second PC raw copy. Scoped Android cycle2 follows the actual cycle1 regression.

## Scenario matrix

|ID|Intent/setup|Command/harness|Expected|Actual/fix|Evidence/cleanup|
|---|---|---|---|---|---|
|A1|Normal explicit debug opt-in|Bounded Gradle research build|Old graph+3076B head only|Compilation successful; lingering owned JVM cleanup discussed below|build01..05; exact installed SHA|
|A2|Malformed asset/vector/cancellation|Native Android head+prior native suites|SHA/shape/finite/cancel and NumPy golden parity|Fixed-build native61pass/0fail/0skip|native-run02; Score input/tensor cleanup|
|A3|Base/caption/quiet/conflict/old approvals|Actual support-policy tests|Same-window guards; old ranges/phrase preserved|Passed, including base range priority fix|native-run02|
|A4|Public/retained/control PCM|Actual53-condition paired run|Existing outputs exact; no new controls support|Prior presence18→20; prior product phase14 preserved exactly;2new outputs|paired-run03; runtime UUID removed; originals unchanged|
|A5|Real decoder/audio+ordered video|Actual known MP4 at10/20s|Complete selected interval and usable output|Both completed,20/40ordered frames and6.646/11.550s emulator elapsed|av-run01; runtime copy removed, original unchanged|
|A6|UI cancel/recovery|Actual synthetic integrated UI|Cancel terminates; next run succeeds; no save|Passed; main heartbeat1ms, input preserved|ui-run01; runtime report removed|
|A7|Range/access/records/media/gallery/timeline|Actual Android regression suite|No record/export/access regressions|80pass/0fail/0skip|regression-run01|
|A8|Invalid flags/parent/beta/bundle/corrupt head|Five real Gradle refusals|Refuse incompatible packaging/asset|5refusals confirmed; frozen asset restored exactSHA|flag-qa01; owned job descendants0|
|A9|Dirty tree/install/storage/lifecycle|Scoped CLI/state, exact APK snapshots|Unrelated data unchanged; own cleanup|Research/test removed; production install snapshot unchanged; complete/clear/absent recorded below|qa/ and final cleanup|
|A10|Changed-runtime ARM proof preparation|Separate explicit caption benchmark mode|Reject old proof and emulator; actual caption output in every research window|Explicit new mode native7windows valid; wrong mode fails; oldAPK and emulator gate refusals verified; physical phone absent|New proof is required before beta|

## Measured result and limits

Fixed53conditions: public33+retained3+controls7+extra10. This is not53 independent
infant examples or an accuracy denominator. Captions, age and event claims are weak
source metadata; no new human event/speaker ground truth was created.

The previous presence research candidate produced18 outputs; fixed caption pair20.
All18prior observation dictionaries and phrases are exact matches. All14accepted
prior product-phase observations/phrases also match. New outputs are public
`extra_416677` and `extra_416635`. The latter has8960actual samples/.56s, padded7040
samples to1s like the host frontend; padding is not real audio. Retained `clip_b_0_base`,
`clip_b_1_base`, `clip_c_0_base` remain supported. Added quiet/DC/click/tone/adult-tail/
music-tail7controls and extra_128479 grandpa remain unsupported and unchanged.
No generic support was renamed as babbling, emotion, hunger, sleepiness or intent.

The full AV test uses existing original `KakaoTalk_20261002_213909230.mp4`, not the
unavailable pharmacy MP4. Pharmacy support evidence is retained PCM. Emulator
measurements cannot approve real-phone speed/thermal or prove intended meaning.

## Failures found and fixes

1. paired-run01 setup failed because416635 was shorter than1s. Runtime copies were
removed and originals unchanged. The old harness result remains `running`; it is
a preserved harness setup failure, not a product failure. Preparation now records
actual samples/padding separately and follows the host1s frontend.
2. paired-run02 had53measured conditions but changed already accepted extra_259609
range/phrase because caption windows were added to base support. Policy now keeps
the entire original supported range set when base support exists. Native regression
and full paired-run03 confirm0prior changes. No threshold tuning was performed.
3. build01..05 each have Gradleexit0/BUILD SUCCESSFUL then bounded `orphan_cleanup`,
cleanup_complete=true/remaining descendants0, so wrappers return1. Kotlin in-process
did not remove this behavior. Rebuilds were later source/test changes; these are
compilation success with owned-process cleanup, never clean-runner claims. Build05
adds the caption-specific benchmark evidence, not another retry of that symptom.

## Artifacts and independent review

AppSHA069badfcd44fe7fff4e52b654dc4b019c7f8006dc2f2d5bc6197be05620b387a and
testSHA44e725b997be096359a80d94733700cebfffbd9299ec7c616f1d5fcddce2a10d
are install-run02 pins for native61/paired53/AV/UI/regression80. Build05 preserves the exact same appSHA/265,359,219B and separately freezes
testSHAdc05f37363e2f3a68b8dcef4e502b7eb7ee0b851cf4e7433cd50470d1bfc1922/2,770,840B
in `work/caption-android-20261007/final/`. Original run pins remain unchanged.

Initial independent Spec and Standards review of9b5b0cb…7ffe2d8 each found0new
mandatory source findings. Standards suggested outer scratch-wipe finally on asset
read interruption; the scratch contains bounded public frozen weights, not private
PCM, and this optional uniform-cleanup suggestion was not adopted in this scope.
Final [independent followup review](caption-android-code-review-20261007.md) of
9b5b0cb…d5b6cdd found0mandatory findings on both Spec and Standards axes.
The frozen APK archive also confirms exactly one unchangedgraph and3076B head.

## Cleanup and residual risk

Runtime PCM/MP4/report copies removed by their own harnesses; original hashes intact.
Production was absent before/after on this shared QA AVD, so this does not prove real
production record preservation. Research/test apps were uninstalled and their process absence was confirmed.
Owned MoaQA35 execution received its normal shutdown; shared AVD folder stays. Prior automatic deletion rejections
were not retried or bypassed. Scoped cycle2 recorded cleanup/complete/clear/read-absent through the CLI;
the actual physical proof has not been run.

## Commands and preparation evidence

All runtime Python harnesses returned0 after validated per-test terminal events:
`device.py native run02`61tests; `device.py paired run03`1test/53conditions;
`integration.py av run01`1test/2selections; `ui run01`1test; `regression run01`80tests.
`flags.py`returned0 for the five expected real Gradle failures (each exit1).
`benchmark.py`returned0: old-mode instrumentation correctly fails1test before
native work; explicit caption mode passes1test and validates1+3+3native windows.
Its emulator-only elapsed3275/5698/4231ms and sampledPSS513779/526105/522378KiB
are diagnostic, not a real-phone performance or thermal approval.

Host verifier/runner suites have16+11unique unit tests, all pass in final
`proof-host-unit03.json` and `.verified.log`; fixtures are fictional metadata/mock
device calls and cannot produce a real ARM proof. Actual old-runtime APK refusal
ends at apk_preflight; actual emulator refusal ends at device_preflight, both exit1,
with frozen temporary APK copies removed and no installation.

Final owned folder/drive storage is `qa/2-cleanup-storage.json`; final ADB inventory
contains no owned emulator and no physical phone. One current caption research pair
is kept for the next actual ARM step; the pinned previous pair remains for provenance.
Prior rejected cleanup targets remain unchanged; no further large model/source downloads.

ULTRAQA COMPLETE: limited research-runtime and ARM-proof-preparation goal met after2cycles.
Final independent review is complete for this scope. Actual physical ARM, beta flag/payload
connection, signing/update regression and Play submission remain uncompleted;
overall improvement/apply/beta goal is active.
