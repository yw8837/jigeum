# Research Android frozen-pair QA — in progress

2026-10-07. Scope: [debug research contract](../research/20261007-caption-android-contract.md).
Main owns Gradle/ADB/Git. Production code32 and access/records/export rules stay fixed.
This is not physical ARM, general accuracy, intent generation or Play update approval.

## Scenario matrix

|ID|Intent/setup|Command/harness|Expected signal|Actual/fix|Cleanup|
|---|---|---|---|---|---|
|A1|Normal explicit debug opt-in|Bounded Gradle research build; asset inspection|Single old graph +3076B pinned head; beta/release excluded|Compilation successful; build processes discussed below|Owned compiler descendants stopped|
|A2|Malformed head/vector/cancellation|InfantCaptionHeadTest actual Android3 tests|Pins/shape/finite/cancel; fixed NumPy golden scores within5e-5|Pending|Temporary tensors zeroed|
|A3|Both heads/quiet/missing/conflict/old approvals|InfantVoiceSupportPolicyTest plus prior policy/native suite|Same-window additive support; previous approvals preserved|Pending|No stored entitlement/record changes|
|A4|Real PCM pipeline/public/retained/controls|53-condition paired Android harness|Actual10/20 windows, both scores, final observation; old support/phrases unchanged and no new controls support|Pending|Fresh UUID runtime copies removed; originals hashed|
|A5|Actual decoder/AV/UI cancel/save|Existing integrated known/ui harness on new research package|Full path/PTS/cancel/recovery and record preservation|Pending|Owned runtime fixtures removed|
|A6|Forbidden flags/missing or changed head/bundle|Actual Gradle refusals and archive checks|No silent packaging or substituted asset|Pending|Small owned fixtures only|
|A7|Dirty state/install/storage|Scoped lifecycle and production snapshot|Original install unchanged/stateclear/measured space|Research app/test installed; production snapshot equal|Uninstall research package/test after QA|

Maximum5cycles/same-failure3 stop, no user training/upload/extra raw PC copy. Unexpected
loss/new negative support stops adoption rather than threshold tuning on these inputs.

## Build evidence and known process behavior

`work/caption-android-20261007/build01..03.log/.json` record actual Gradle exit0 and
BUILD SUCCESSFUL. The bounded runner found lingering owned build JVM descendants and
reported `termination=orphan_cleanup`, then cleanup_complete=true/descendants0 each
time. The wrapper therefore returned1. This is recorded as compilation success with
build-process cleanup, not a clean runner exit or a passing app test. Switching Kotlin
to in-process did not remove this behavior. Rebuilds02/03 compiled later test changes;
there is no fourth retry of this process symptom. Runtime QA uses the exact installed
APK/test hashes in `install-run01/provenance.json`, with production install unchanged.

The first build began before the scoped Android QA lifecycle; it is preparation
evidence and is not relabeled as a lifecycle-covered run. The current QA scope records
planning/qa before runtime tests. No final COMPLETE declaration is made here yet.
