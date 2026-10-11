# Alpha38 review request

- Requested: **2026-10-12 04:38 KST** (2026-10-11T19:38Z).
- Track: existing closed-test **Alpha**, release ID **18**.
- Exact name: **0.5.24-beta (38) - saved media fix**.
- UI: **검토 중인 변경사항**; preliminary quick checks running, up to14minutes shown.
- Managed publishing: **disabled**. Approval should publish automatically; actual availability must still be observed.
- Uploaded betaAAB: `work/saved-media-20261012/final/Agattok-0.5.24-beta-code38.aab`.
- SHA256: `6c3e3eaebb8897c5cf586d3d723432d96c771a227c3ac1009bfe1e20899f9217`.
- Local verification/evidence commit: `de9e6f427e3377534851bdb88ad965ff78f46043`; remoteSHA matched and clean before upload.
- Play validation: blocking errors0; two existing recommendations for deobfuscation/native symbols.
- Device support removed0/added0 across reported form factors. Phones11,321, tablets6,439.
- New-install size112MB, +7.14KB vs prior release. Update-size number was not shown; do not infer it.
- Only one submitted change: Alpha38 rollout. Existing tester lists/groups, other tracks, production, permissions and products untouched.
- **Tester availability of38 is not yet confirmed.** Last confirmed available version is37.
- Automation33 updated through tool to **ACTIVE**, every15minutes, exact38 only; quiet unchanged, notify approval/rejection/error/action.
- Actual per-tester installation and12people/14continuous days were not checked by this submission.

Evidence: [submission screenshot](play-review-submitted.png), [UI text](play-review-submitted.txt).
Local actual20-second export/relock evidence: [README](README.md).

## Release notes

갤러리에서 불러온 일부 영상에 아기말 자막을 넣어 저장할 때 발생하던 오류를 수정했어요.
분석 결과가 없거나 분석에 실패해도 선택 구간을 원래 소리와 함께 영상으로 저장할 수 있어요.
다른 영상을 고르면 이전 결과와 편집 중인 문장이 남지 않도록 확인했어요.
짧은 구간에서 확인된 발성이 잠시 쉬었다 다시 이어지는 흐름을 표현에 반영했어요.
일부 소리 구간에서 확인된 발성 단서를 보존하도록 보완했어요.

## Remaining confirmation

Read exact38 in Alpha as **선택한 테스터에게 제공됩니다**. Save small proof,
update HANDOFF/NEXT/QA/summary, commit and push with SHA+clean check, notify user with
existing links, then pause automation33. No repeated upload/cancel/new build is needed.
Known software-renderer emulator timeouts remain documented; no product-wide performance
or emotion/need accuracy claim follows from this release.
