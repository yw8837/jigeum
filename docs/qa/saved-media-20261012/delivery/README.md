# Signed38 delivery gate follow-up

## Scope and result

Same signed beta38 APK (106507f17a717cbf2cde5339df7faee9bb94b7669013837a927ca3b496cf5dae),
same public20 file (9d57307e1fbd0ff3329e9ed5eba90d6713e619c9ac5d7b65cc6118a5876d8877).
No product code/model/threshold/coverage/45-second deadline changes. No new build.

| Scenario | Actual | Evidence |
|---|---|---|
| Software GPU, no analysis UI polling | App timeout | delivery-diagnosis/diagnosis.json row0 |
| Software GPU, normal UI polling | App timeout | delivery-diagnosis/diagnosis.json row1 |
| Host GPU, two separate process-first analyses | Both result screens | delivery-gpu/diagnosis.json |
| New beta20 analysis → scene save → audio/video MP4 | Pass | signed-smoke-gpu/result.json |
| Same-key release relock and same exported file hash | Pass | signed-smoke-gpu/result.json |

First environment reached68MiB/1.55MiB free host RAM. Aborted redundant third analysis
before start, stopped owned runner/device and removed owned external fixture. See supervisor.json.
Host GPU selected NVIDIA RTX3050. Second environment also had memory pressure; GPU,
CPU scheduling and memory effects are not independently established. Do not label this a
product performance fix or claim exact causal attribution. 46-second quiet waits are
observation scheduling, not app elapsed-time measurements; the app retained its45s limit.
The input is1fps and its earlier S25 evidence is20/PARTIAL visual observations, not100.

## Commands and meaningful bounds

- diagnose_delivery.py: same actual gallery-import/20-second UI path; first software
  run was supervisor-stopped after2 completed failures. Startup ADB environment miss is
  harness-only; explicit path supplied on restart, before any product verdict.
- diagnose_delivery.py delivery-gpu quiet quiet: exit0; each row explicitly pass.
- signed_smoke_gpu.py: exit0 only after analysis, export and release checks pass.
- Existing package/upgrade checks were not needlessly rebuilt or replayed.

Independent Spec review confirmed saved-media changes do not alter20-second inference,
45s timeout or observation cadence; added pause-expression path exits immediately above10s.
User original gallery save successes and S25 direct6.145/5.516sec analysis remain separate
evidence, not signed-beta physical UI performance or emotion/need accuracy.

## Cleanup / publication

External fixtures are removed by drivers; owned emulator app/shutdown and lifecycle
cleanup are recorded separately before upload. User S25 research records remain intact.
Old policy-blocked file deletions remain blocked. Only the existing Alpha beta is authorized.
Upload/review submission is not tester availability; exact version38 available state is required.

Final local checks: two signed smoke scenarios passed, actual MP4 duration20.000s; beta/release SHA2566a60652453ed30639434c3a76a8e514b772e905d06a5b6d4696158e11d6efba8 identical. Independent Spec final mandatory blockers0; existing Standards review unchanged because no product source changed. Owned app uninstall succeeded; external fixtures/XML removed; emulator absent. Followup local lifecycle complete→clear→read absent verified. Publication remains separate and pending.
