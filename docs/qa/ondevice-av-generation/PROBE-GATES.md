# Gates: local audiovisual model feasibility

OWNS: tools/research/ondevice_av_probe.py, tools/research/test_ondevice_av_probe.py, tools/research/ondevice-av-model.json, docs/research/20261003-ondevice-av-probe.md

Scope: Run a pinned local audiovisual candidate and report execution and quality limits; this is not Android rollout completion.

- [x] P1: Input boundaries, pinned model verification and response schema reject known invalid cases.
  CHECK: python -m unittest discover -s tools/research -p test_ondevice_av_probe.py
  EXPECT: OK
  EVIDENCE: 2026-10-04 19 tests passed. Includes fresh-review fixes for malformed bundles, ambiguous audio streams and private output boundaries. Further cases may be added during recovery work.

- [x] P2: The exact pinned file executes both audio and timestamped images locally.
  EVIDENCE: Pinned SHA/bytes verified; actual 10s/20s audio+10/20-image host runs completed. Synthetic tone and temporal semantics failed; this is execution feasibility only, not a quality pass. See docs/research/20261003-ondevice-av-probe.md.

- [x] P3: Existing failure inputs are evaluated without uploading private media or claiming unsupported accuracy.
  EVIDENCE: Seven local owner-case runs, anonymized execution summary in evidence/owner-execution-summary.json. Responses frozen before owner labels. Owner3 was later identified as prior-app result video and excluded from normal perception quality denominator. Parent review pending; no accuracy claim.

- [ ] P4: Final review and report distinguish host feasibility, Android readiness and parent quality approval.
  EVIDENCE: Sol read-only review completed; three important harness findings fixed with RED→GREEN tests. User then requested Astra/high core analysis and fixes. That stage and its follow-up QA are in progress; no Android/quality completion claim.
