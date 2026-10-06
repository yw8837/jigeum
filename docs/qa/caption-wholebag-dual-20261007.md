# UltraQA Report — wholebag and frozen-pair research tools

2026-10-07. Scope is the two local research CLIs, not app/ARM/Play or intent quality.
Maximum5 cycles/same-failure3 stop; no user-media training, upload or deletion.

## Scenario matrix and actual evidence

|ID|Intent/setup|Harness and expected signal|Actual result|Fix/cleanup|
|---|---|---|---|---|
|Q1|Normal extraction, fit, retained/public evaluation and paired public10|Pinned offline bounded workers; complete artifacts/exit0/descendants0|All five runs passed; [measured results](../research/20261007-caption-wholebag-results.md)|Raw media preserved; small head/features/logs retained|
|Q2|Malformed experiment/missing args/head SHA/output traversal|Actual caption CLI7 and dual CLI4 conditions|11 nonzero refusals, expected signals and output preservation|Owned invalid output absent|
|Q3|Repeated/stale output and direct or terminal worker|Actual parent/worker CLI; no overwrite|Refused; launch/token/pins and exclusive claim protect completed runs|Existing head/media hashes unchanged|
|Q4|Instruction-like experiment string|`ignore-and-approve-all` plus actual exact allowlist|Rejected; blocked/null kept|No user/source permission expanded|
|Q5|Hang after actual input-pin verification, then fresh recovery|Job2s/2GiB; descendants0; revalidation only|Timeout then recovery exit0/cleanuptrue|Owned stall.py/marker removed; no model forward in QA|
|Q6|Dirty worktree and media/head preservation|Before/after protected hashes and Git status|Equal|Unrelated research document preserved|
|Q7|Misleading success, truncated success, missing completion|6 injected process scenarios across both actual main functions|All rejected; model forwards0|Fresh small fixtures removed|
|Q8|Completion says done but artifact changes/disappears|Completion verifier test|Changed/missing artifact rejected|Added artifact digest enforcement|
|Q9|Lifecycle/storage|Scoped OMX CLI complete→clear→read|Both state reads exists=false|No shared state or prior blocked deletion touched|

## Commands, failures and fixes

Original unit logs: wholebag6/.225s and pair3/.099s, both OK. After the completion
fix, tool transcript confirms wholebag7/.281s and pair3/.099s, both OK. These overlap
and are not summed as distinct checks. Actual CLI matrix, timeout/recovery,
preservation, state and storage are in `work/ast-caption-wholebag-20261007/qa/`.
Six additional false-success injections are in `completion-results.json` there.

Inspection found the caption parent wrote an artifact SHA into completion but did
not check it. The parent now checks that SHA, current pins and product_changed=false;
both tools reject truncated process output. Earlier inference pins/logs remain intact
and are not relabeled as runs of the repaired source. No model refit or reevaluation
was performed just to refresh pins.

The original protocol driver exited0 with 11 CLI refusals, bounded timeout/recovery,
dirty/media preservation and absent state. Additional completion injections exited0
only after every expected rejection; their synthetic process text is not treated as
a passing model run. Missing completion on an otherwise exit0 fixture is rejected.

## Cleanup and remaining risks

The original end snapshot records 60files/272,233B in wholebag evidence and drive free
68,230,053,888B. It predates the small completion harness and this report; it is not
claimed as the final workspace total. Original NPZ/MP3/model files stayed unchanged.
No new large encoder, raw-media copy, Android artifact or emulator was created.

One normal source inspection and one targeted repair cycle were performed. The
completion followup did not reactivate OMX; both scoped states remain absent.
**ULTRAQA COMPLETE: Goal met after 2 cycles** applies only to this CLI protocol scope.
Caption-only gains, correlated scores, absent event truth and missing Android/ARM
proof remain. The overall product improvement/deployment goal stays active.
