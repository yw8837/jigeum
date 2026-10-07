# Caption beta update QA — signed runtime passed after host reboot

2026-10-07 KST. Goal: physically reviewed frozen pair enabled only for closed-test
beta0.5.19/code33, signed32→33 record/media preservation, analysis/navigation,
free exports and production relock, package pins and actual Play review submission.
No user media upload/training; only dedicated MoaQA35 emulator mutations.
Main owns Git/Gradle/ADB. Preserve unrelated files, originals and shared AVD.

Preparation started before scoped lifecycle; planning/qa state began afterward.
First signed build failed on missing generated-assets→lint dependency. A producer
FileCollection did not propagate through AGP lint; explicit lint dependencies fixed
it. Signed beta build03 and comparison release build01 both exited0 with no job
descendants and no truncation. The two failed beta logs remain preserved.
Maximum5 QA cycles / same failure3times stop; bounded process logs and real terminal
results required. No success-only output or synthetic proof authorizes packaging.

**Initial scope: ULTRAQA STOPPED: Max cycles.** Cycle5 reached an Android System UI ANR before
the remaining scenarios. The signed candidate is built, but final media/PDF/relock
QA and Play submission remain incomplete. Do not upload this candidate until the
remaining required checks pass on a stable environment. This is not a complete
goal or a general accuracy claim. The subsequent successful resumption is recorded
below; this initial failure has not been rewritten as a pass.

### User-reboot resumption (new environment, same acceptance)

The user rebooted the PC; Windows LastBootUpTime changed and no previous emulator
or Gradle process survived. Old cycle5 was explicitly stopped/cleared/absent;
its failures remain. `work/caption-beta-resume-20261007/host-before.json` captures
the new host state. A fresh scoped run reuses the exact four final artifacts and
the original four-scenario access proof, without rebuilding or downloading models.
Its remaining gates are the unchanged U2 media/PDF/restart/relock checks and D1/R1.
Emulator1536MiB/two cores with Vulkan disabled reduces renderer resource use.
Maximum5 cycles still applies to this resumed scope; no pass is assumed from reboot.

**Actual resumption result: PASS, exit0/404.67s.** Same four artifact pins verified.
`signed-remaining01/closed-test-ui-result.json` explicitly inherits the first four
scenarios from retry02 (proof SHA `bb74ceb8914e244d05900cae46f5cef47b2724a19b3fbac0c3872cb32b4bb7d2`)
and executes the remaining three scenario groups. The actual expanded evidence
reported no face across20observations, and beta created a real10.000000s MP4 with
audio+video streams (932978B). Its beta/release exports have the identical SHA
`c8599236be8c9e5747cb39df1020919937fecd4daaaa2b405176a550febb2080`.
PDF share recorded an actual app content URI and Android chooser. Cold restart
retained full beta access and records. Comparison release restored60/10/100 and
visual/PDF gates while preserving the existing record and free movie export.

Harness cleanup removed external and local size/media fixtures and restored
display/font settings. `cleanup-result.json` separately proves the exact signed
code33 QA install was uninstalled, the owned UI XML removed, emulator disconnected,
and shared MoaQA35 AVD retained. Drive free96724426752B at cleanup is a system-wide
snapshot, not exact reclaimed bytes. Phone production installation was not touched.

|ID|Intent / input|Harness|Expected|Actual / evidence|Cleanup|
|---|---|---|---|---|---|
|B1|Signed beta and comparison release|official builder|Gradle/signing pass, code33|PASS beta03/release01; both clean exit0|Keep final only|
|P1|APK+AAB pins, flags, ABI, signatures|verify_final.py|caption beta only, release absent|PASS final-package-result.json; compiled DEX and official bundletool|Small logs|
|A1|Malformed/missing parents/mixed graph/no proof/bad proof|flag_qa.py + gate units|Actual refusal, no package|PASS4initial + targeted provenance refusal;11gate units +8builder option cases|Owned fixture removed; original proof unchanged|
|A2|Missing/corrupt head + misleading archive|caption payload units|reject synthetic bad archives|3unique units pass in preparation|Temp dirs removed|
|U1|Signed32→33 update|signed_media_upgrade.py|record + MP4 hash unchanged|PASS signed-upgrade-retry02,271.47s; text + real10s MP4 exact|Owned runtime copies removed|
|U2|Beta full access, 120s/20s/200MB, PDF, export and release relock|closed_test_ui.py|normal+limits pass, existing free records kept|PASS original4+post-reboot3groups; actual media same SHA, PDF, restart, release gates|Full harness cleanup + owned QA install/emulator removed|
|U3|Local actual AV and navigation/cancel|known_ui.py|selected10s analysis, result/back/picker/capture cancellation|PASS signed-known-final; observed broad AST cue; UI wait25.384s on2-core emulator|No save, local copy removed, original SHA unchanged|
|R1|Independent Spec/Standards review|code-review|No mandatory remaining issue|PASS source and final resumed evidence; mandatory0each|Read-only|
|D1|Disk/process state|scoped lifecycle snapshots|Owned disposable copies removed, AVD preserved|PASS cleanup-result.json + new complete/clear/absent|Old scope stopped/clear/absent; shared AVD retained|
|X1|Only final beta AAB to Alpha|Play Console UI|Actual submitted review state|SUBMISSION ACCEPTED: code33 in review section; quick checks still running, not yet observed available|Screenshot/state saved; existing track/settings preserved|

Physical proof and research behavior results: [ARM](caption-arm-20261007.md),
[Android](caption-android-20261007.md). Synthetic performance and caption support
are not infant intent accuracy, event/speaker ground truth or all-device proof.

## Preserved harness/environment failures

- First mismatched-proof fixture was moved to a different folder and rejected by
  the storage-scope guard before reaching the intended provenance guard. The
  expected error assertion failed. A fresh small same-folder fixture changed only
  the APK hash; actual Gradle then rejected the exact provenance. Fixture removed,
  real proof unchanged; failed log preserved. This was a harness setup failure.
- First signed upgrade failed before installing the new APK: the emulator vanished
  while testing code32 saving. ADB reported offline and cleanup could not run.
  Host free physical memory was348620KiB while release compilation also ran.
  This is an environment failure, not evidence of a code33 crash. Recover the same
  AVD after builds, remove exact stranded fixtures and retry in a fresh folder.
  Preserve shared AVD; cap emulator memory and leave unrelated processes alone.
- The first closed-policy run failed at CAMERA permission setup when ADB's local
  server was unavailable. No policy scenario ran; installs matched code33. Cleanup
  completed (local400MiB fixtures removed, external copies absent, display restored).
  Both SDK aliases have identical ADB binaries. After the server recovered, actual
  get-state and CAMERA/RECORD_AUDIO grants passed; retry is scoped to a new folder.
- `signed-closed-retry02` passed small screen/200% font, a real1:59.4 capture,
  actual20s quiet analysis with honest abstention and exact200MiB/+1byte bounds.
  UI completion125.39s includes processing delay, not a125s movie. It then failed
  because the expanded no-face evidence was not in the current viewport. Source
  places evidence below the toggle. A bounded12-scroll assertion was added while
  retaining the exact no-face requirement; that fix is still awaiting execution.
- `signed-closed-remaining03` reused the exact beta APK's first4scenario proof,
  but the emulator disappeared during the initial app launch. Exit1/42.82s and
  the failing log remain. Free host RAM was331208KiB. Failure screenshots and
  device cleanup also failed; `remaining03-recovery.json` records subsequent
  removal of exact external fixtures and419430401B local size fixtures, plus
  display/font restoration. Shared AVD and user originals were preserved.
- The harness now streams APK hashes, creates only the required size fixture on
  resume and delays its guest copy until release rejection. Failure capture can
  no longer mask the original error, and device cleanup errors no longer prevent
  local large-file cleanup and writing a failed result. These are harness changes,
  not changes to the already built app.
- `signed-closed-remaining04` used a1280MiB/two-core emulator. Installed code33's
  hash was verified, but Android showed `System UI isn't responding` before the
  remaining checks. Exit1/107.78s; full cleanup passed. The first4scenarios in this
  report are explicitly inherited from retry02, not re-executed passes. This
  establishes an environment failure; it does not identify a product crash or
  prove memory pressure is the sole root cause. No further same-environment retry.
- Cycle3's initial adversarial event preceded its qa event. The initial log is
  preserved as `3-adversarial-e2e-first.log`; qa and adversarial events were then
  recorded in order with four actual artifact hashes checked. Do not describe
  the repaired lifecycle as if the original event order had been correct.

## Remaining acceptance

All requested signed runtime gates are now covered by the original and resumed
evidence. Both independent axes accepted the final evidence with0mandatory findings.
The fresh scoped lifecycle is complete/clear/read-absent: **ULTRAQA COMPLETE:
Goal met after1resumed cycle**, following the separately preserved initial5cycle
stop. This completion covers local signed QA, not external Play deployment.
Only the beta AAB has now been submitted to Alpha. Do not repeat passed checks without cause.
The comparison release AAB is never a Play deliverable.

## Actual Play submission

Source/QA commit `7dae68a7204ce7e62f507ebe213d5dcc7b772577` was pushed to
`origin/codex/ondevice-av-probe`; exact local/remote SHA and clean worktree verified.
Using the existing Yeolmae app/Alpha track, uploaded only the pinned160193654B
beta AAB, release13, `0.5.19-beta (33) - infant vocal support`. Saved the Korean
release notes and sent the single Alpha change for review. The Console then
displayed `검토 중인 변경사항` and the exact33release row. Quick checks were still
running (up to14minutes); this does not yet prove review completion or tester
availability. Managed publishing was already disabled; no setting was changed.

Actual review screen: phone11301/tablet6408 supported devices, lost0/new0;
new install112MB, previous-release delta14KB. Two advisory warnings remain:
no deobfuscation mapping and native debug symbols. No blocking error was displayed.
The earlier notification about Fragment1.1.0 explicitly named old code27; it was
not described as a newly resolved code33 issue. Production, products, tester lists
and account permissions were not changed.

Evidence: `work/caption-beta-resume-20261007/play-code33-review.jpg` and
`play-code33-state.txt`. Existing opt-in URL remains
https://play.google.com/apps/testing/com.moa.babylanguage .
Actual Play split installation/new33availability is pending external review.

## Package identity

Beta APK255163379B/SHA93bdf6de8ca211b4779ea131c2ee4e2cdca564e3acfb36dfedbc73849a4341af.
Beta AAB160193654B/SHA6a1fd4ef01f1d82035f77d4ea5388997cf00af9d096e6dee0b56d93f98641f1a.
Comparison release APK75364843B/SHA2610adaca1023ccdaaa4f6a3b9a30644cedc5384b4d1263699b773a7989ef200.
Comparison release AAB46716618B/SHAbaacbe951686b26df375080c9e1335195cdc1bc78a4e7cfa3836fa42b3ece28f.
Only beta may be submitted. Four CPU ABIs,16KiB native alignment, APK/AAB signatures,
backup exclusion, notices and beta/release compiled flags pass. Upstream.so/.tflite/
.task bytes match code32 (26beta /18release files). Self-signed Android upload cert
trust-chain/time-stamp warnings remain expected; SDK XML/path-check and existing
decoder deprecated-field notices remain. There is no claim of zero build warnings.

15unique host unit tests pass (3payload,1builder unit covering8actual PowerShell
refusals,11proof units). Fake-success/NaN/duplicate/oversized/stale/emulator/mismatched
provenance cases do not authorize a package. Data-only model inputs have no prompt
execution path; no exfiltration or unsafe destructive injection is exercised.
