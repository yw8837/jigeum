# Two-axis code review

Baseline `f5fb296c38bbcd66d990151e8105705b94b1ea9c`, working diff plus new files. Spec: [UX-MEDIA-0.5.5](../../UX-MEDIA-0.5.5.md). Standards: root AGENTS, HANDOFF and RUNBOOK. Two independent read-only reviewers; build/ADB remained owned by the integrator.

## Standards

Documented hard violations:0. On-device processing, free existing outputs, current entitlement checks and private FileProvider boundaries were maintained. No meaningful Fowler smell was raised.

Three P2 lifecycle/consistency findings:

1. Render completes but cancellation wins while returning the file → caller never owns the path, output remains. Fixed with outer tracked-file cleanup covering the return handoff.
2. Orphan cleanup uses a snapshot outside the DB mutex → a concurrent new media record can lose its file. Moved fresh row lookup and orphan cleanup into `ObservationStore.cleanupMedia()` under the same per-database mutex.
3. File deletion fails after row deletion committed → old record remains on screen and file remains without retry state. Deletion intent now commits in SQLite with row deletion, retries on snapshot/restart, and exposes a specific warning while the UI reflects the actual rows. Same-ID import/save cannot reuse pending media.

Follow-up found one P2: startup render-cache cleanup could remove a completed output waiting for DB adoption. Cleanup now runs once per process, under the render mutex before its first render. The late-range test calls cleanup between render/adoption and requires the file to survive.

## Spec

Initial two P2 implementation findings were the cancellation handoff and deletion inconsistency above. Reviewer confirmed both fixes. Missing QA evidence for full frame preservation, actual SAF, playback and activity changes was explicitly identified; these require the execution report, not reviewer assertions.

Follow-up found one conditional P2: a SAF provider can refuse deleting a partial document. Previously the return value/error was ignored. The implementation now checks it and shows a message to inspect/remove the incomplete file at the selected location. The spec explicitly states that an external provider's deletion refusal cannot be overridden. It does not promise universal remote-document cleanup.

## Disposition

All5 distinct implementation findings were addressed; two were reported on both axes. Static review is not execution evidence. The final report links real codec/SQLite tests, signed-app UI checks and remaining device/provider coverage limits. No infant intent accuracy was measured by these reviews.
