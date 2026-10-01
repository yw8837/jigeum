# UltraQA — 0.5.4 UX and duration

2026-10-02. Work in progress; no passing result is implied by this plan.

## Goal and bounds

Free capture 60s / analysis 10s; full capture 120s / analysis 20s. Structural decoder ceiling 30s is retained for controlled comparison, not offered by the paid UI. Confirm title, range control, evidence-conditioned expression, lifecycle, access, old records and packaging. Android native UI is exercised through Compose instrumentation and ADB; gstack's browser-specific console/DOM score does not certify a native app. Browser checks apply to the separate public policies and Play setup. Existing in-progress implementation is authorized and preserved, then committed before QA fixes. Updating obsolete cap assertions is required by the approved feature change.

No real baby input or production payment. All generated audio/video is synthetic. Root is sole Gradle/ADB operator. Test device emulator-5554 / MoaQA35. Max five diagnose/fix cycles; stop on the same failure three times. No reruns just to obtain green. Artifact identity and complete per-test events required. No external Play release mutation in the hostile QA harness.

| ID | Intent / model | Setup / harness | Expected signal | Result / fixes / evidence | Cleanup |
|---|---|---|---|---|---|
| Q01 | Normal capture; distracted parent | Native camera and mic 60s/120s auto-stop | Finalized playable file, audio track and correct cap | pending | Owned captures removed |
| Q02 | Duration/size abuse | Real 120s synthetic MP4; missing/corrupt/oversized input | Free rejects >60s, full accepts120s; outside max rejected | pending | Test cache deleted |
| Q03 | Last part of a long source | Decode100–120s and internal90–120s, actual YAMNet | Late samples retained, no old15s truncate/error | pending | Zero PCM, cache deleted |
| Q04 | Difficult finger control / accessibility | Handles, whole-window drag, 0.5s buttons, zoom, TalkBack actions | Length and source bounds preserved; minimum1s; usable controls | pending | Composition disposed |
| Q05 | Entitlement changes / stale screen | Remove access before/during/after long/visual analysis | Cancel paid work, free10s retry, keep existing result and source | pending | Test doubles confined to androidTest |
| Q06 | Repeated cancellation / missing preview | Begin/cancel/retry, missing media, background | No late result, no crash, failure does not block editing | pending | Jobs/decoders closed |
| Q07 | Decorative startup | Cold title / tap / automatic exit / large font / reduced motion | One exit, no extra required tap, readable layout | pending | Restore settings |
| Q08 | Unsupported long language / padding | 10/20/30s fixed evidence, silence padding, mixed events | Short complete phrases; expand only supported activity; no fabricated needs/transitions | pending | Synthetic test inputs only |
| Q09 | Release vs preview | Signed builds / package gates | Preview open and not bundleable; release checks actual entitlement | pending | Keep distinct named artifacts |
| Q10 | Old-flow regression / misleading green | Full instrumentation + host harness parser tests + lint | Explicit pass/fail/skip counts and installed hash | pending | Preserve logs; clear isolated OMX state |
| Q11 | UX and store truth | Signed app screenshots, policy browser, listing vs current code | Matching limits/app name; no old removed controls | pending | Only owned browser tabs |

Prompt injection is not an app command surface here: note/import text is data, not code. Existing hostile JSON/import tests cover that boundary. OMX uses a dedicated work/ux-quality-054 cwd with OMX_ROOT and OMX_STATE_ROOT removed from its child environment; repository state is untouched.
