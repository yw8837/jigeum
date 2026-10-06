# Whole-recording caption head and additive comparison

2026-10-07. Contracts: [wholebag](20261007-caption-wholebag-contract.md),
[frozen pair](20261007-caption-dual-contract.md). Product remains 0.5.18-beta/code32.

## What was run

Exact public CC0 IDs 9437 and 244237 were decoded in full: 21,456 and 1,560,295
actual 16kHz samples, respectively. All 11 nonoverlapping spans cover the recordings,
including the final tail. Original 527 logits were equal on every span. No new raw,
WAV or PCM copy was created. The extra feature archive is 31,772 bytes.

The existing train21/18uploader set plus the two whole-recording bags gave
23 training clips/20 source groups; validation14 stayed unchanged. The 769-parameter
head used train-only source CV over the prescribed three L2 values, selecting .0001.
Selection and head were frozen before validation/private access. Threshold .8 and
margin .4 stayed fixed. Canonical blocked/null/unreviewed fields remained intact;
this was the exact caption-only experimental exception, not approved event training.

Frozen new NPZ SHA: `4929cee8c7c3d11cc98c351a73148837f9c312250c50e7ef9a41a5420c3e4ad5`.
Base NPZ SHA: `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`.

|Run|Seconds|Peak Job committed bytes|Exit/cleanup/descendants|
|---|---:|---:|---|
|features01|20.279521|2,214,187,008|0 / true / 0|
|fit01|10.474720|813,338,624|0 / true / 0|
|retained01|5.769179|2,195,451,904|0 / true / 0|
|public01|87.113098|2,198,065,152|0 / true / 0|
|dual extra01|12.515305|1,430,757,376|0 / true / 0|

All logs are untruncated. These are PC process measurements, not phone PSS or latency.

## Results and rejection of a replacement

- Validation infant-caption support increased 2/4 → 3/4. ID18277 moved .79400 → .83374.
  Validation adult8/other2 gained no support.
- The first pharmacy retained window fell to .79788, below .8. The overlapping second
  remained supported at .90828. The retained cry input remained unsupported at .66169.
- Public33 had no support changes. **The standalone head was rejected as a replacement.**
  An early commentary claiming both pharmacy windows were preserved was corrected
  immediately after reading the actual results.

The frozen pair is an additive diagnostic: either unchanged head can support the same
window, with the existing RMS floor. Matching IDs, PCM/window hashes, actual
start/length/padding/RMS, model and thresholds were checked before pairing reports.
The pair preserved both pharmacy windows and all old public35/public33 support.
The paired-report replay performs no model forward.

An actual one-session comparison reused the existing public10 MP3 set, without
download, copying or fitting. First≤20s was evaluated; a caption does not establish
which event is in that crop. The old native score and NumPy calculation were compared.

|Caption source ID|Base support|New-head support|Pair support|
|---|---|---|---|
|416677 babble/gibberish|no (.6655)|yes (.8120)|yes|
|416681 babble/gibberish|no|no|no|
|416635 grunt|no (.6504)|yes (.8352)|yes|
|416702 laugh|yes|yes|yes|
|259609 babble/crinkle|yes|yes|yes|
|254454 babble/gibberish|no|no|no|
|254455 babble/gibberish|no (.7538)|yes (.8223)|yes|
|254465 dada|yes|yes|yes|
|254471 dada|yes|yes|yes|
|128479 cartoon grandpa|no (.0731)|no (.1979)|no|

Caption infant inputs supported 4/9 → 7/9, with three additions across two uploader
groups and no loss. **This is not an accuracy rate.** Age, family, event timing,
speaker identity and emotion are unverified; these are repeatedly exposed development
data and correlated heads. No appetite/sleep/intent or translation truth is established.

## Artifacts and next gate

Ignored evidence: `work/ast-caption-wholebag-20261007/{features01,fit01,retained01,public01,paired-reports.json}`
and `work/ast-caption-dual-20261007/extra01/`. Existing encoder graph is unchanged:
`4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.

Next is research Android integration on the same existing encoder, followed by actual
decoder/audio/visual/phrase regression, controls and physical ARM time/PSS/thermal/
cancel recovery. Preserve existing emotion guards and approved results. A new pair
must not reuse the old APK's ARM proof. No Android benefit, new APK/AAB or Play
update occurred in this experiment. Overall goal remains active.
