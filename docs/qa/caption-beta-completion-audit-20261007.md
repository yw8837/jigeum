# Beta33 objective audit — beta and tester registration published

2026-10-07 KST. This is a current-evidence audit, not another test execution.
The objective remains on-device vocalization/context improvement, fixed comparison
and regression preservation, application/QA, Git push and actual closed-beta update.
No accuracy percentage or infant intent truth is inferred from caption metadata.

| Requirement | Inspected authoritative evidence | Finding |
|---|---|---|
| Candidate/source research and cause analysis | `20261007-caption-wholebag-results.md`, `20261007-next-small-infant-engine-followup.md`, Android QA/source | Existing AST features and frozen small support head; replacement that lost pharmacy support rejected. Search limitations recorded. |
| Fixed comparison with added support and preserved controls | Actual `work/caption-android-20261007/paired-run03/{result,summary-public}.json` | 53 development conditions,18→20 outputs, prior output changes0, controls unchanged, prior product14 preserved. Limited support gain, not independent accuracy. |
| Whole selected interval and actual AV path | `work/caption-android-20261007/av-run01/result.json`; runtime source and recorded AV report | Actual10/20s AV passed with ordered20/40visual observations; AST windows cover the selected interval. No claim that sparse observations recover every event. |
| No loosening of old approvals/guards | Current `InfantVoiceSupportPolicy.kt` and native61 result | Existing sufficient base range set wins; audible same-window and contrary guards remain. Native61 passed with no fail/skip. |
| On-device operation and preserved user media | Research app manifest/proof, unchanged-source and runtime-copy removal fields, package checks | Research manifest has no INTERNET; actual source hashes unchanged. Product INTERNET remains for its existing separate billing path. No new baby upload/training route introduced. |
| Physical speed, memory, cancellation/recovery | Actual `work/caption-arm-20261007-run01/result.json` | S25/API36: synthetic AST10/20/20s2.486/6.300/6.157s; thermal0→0; benchmark1 and recovery2 pass, no skip; production install unchanged, cleanup complete. Not all-device or full-AV latency evidence. |
| Frozen payload and beta-only flags | Actual `work/caption-beta-20261007/final/final-package-result.json` | Pinned graph/head,4ABIs, signed beta33. Caption/presence/AST flags true only in beta; comparison release flags false and payload absent. |
| Existing records/media retained on update | Actual `signed-upgrade-retry02/signed-media-upgrade-result.json` | Signed32→33 text and10s audio+video MP4 bytes/hash preserved; no app-data clear. This is sideload QA, not Play split installation. |
| Access, actual save/share, restart, production gates | Actual `work/caption-beta-resume-20261007/signed-remaining01/{process-result,closed-test-ui-result}.json` | PASS exit0/404.67s. First4groups explicitly inherited with proof hash; remaining3 executed. Beta/release free exported MP4 same SHA; PDF chooser; full beta restart; release60/10/100/visual/PDF gates preserved. |
| Required regressions and review | Actual native61/regression80 result files and `caption-beta-code-review-20261007.md` | Native61 and regression80 pass/0skip. Independent Spec and Standards final mandatory findings0 each. Initial5cycle stop and later1cycle resumption remain distinct. |
| Disposable storage/process cleanup | Actual `work/caption-beta-resume-20261007/cleanup-result.json` | Owned QA app/XML removed; emulator disconnected; large fixtures absent; shared AVD retained. Free-space snapshot is not exact reclaimed bytes. Prior rejected deletion not retried. |
| Git delivery | Actual `git rev-parse HEAD`, previous remote verification, porcelain | Baseline `190d5ed68424c40f6db3fe8abd84a22ec2a94ca6`, clean before this approval documentation. This documentation commit must also be pushed and its local/remote SHA compared before completion. |
| Closed-beta submission | Actual Play publishing DOM/screenshot | Only code33 beta submitted to Alpha; Google approved and published it. |
| Actual new-beta availability | Actual Alpha DOM/screenshot, `play-code33-available.{jpg,txt}` | **PROVEN.** Exact33 latest release is available to selected testers; Console publication2026-10-07 19:08 KST. |
| Subsequent user request: Zardu testers | Actual publishing, submission13 detail and Alpha tester DOM/screenshots; `zardu-testers-20261007.md` | **PROVEN.** Submission13 tester-only change released2026-10-07 19:28 KST. Three lists remain selected with33/4/2members. Counts overlap; vendor-account participation and installation are not claimed. |

Google review and availability evidence is retained in
`work/caption-beta-resume-20261007/play-code33-{in-review,available}.{jpg,txt}`.
The original beta-delivery gate and the additional tester-setup publication gate
are now satisfied. Fresh approval evidence is retained in
`work/zardu-testers-20261007/{publishing-approved,submission13-released,testers-approved}.{jpg,txt}`.
At19:38 KST the exact tester-only submission and all preserved selections were
checked. The remaining completion actions are this documentation commit/push,
local/remote SHA comparison, pausing existing heartbeat33 and handing the links
to the user. These do not establish vendor login/install,12continuous testers
for14days, production approval or independent baby-intent accuracy.
No re-upload, approval bypass, production release or fresh model download is needed.
