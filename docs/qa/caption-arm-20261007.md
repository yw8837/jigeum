# Frozen caption pair — actual S25 ARM measurement

2026-10-07 KST. Contract: [caption ARM](../research/20261007-caption-arm-contract.md).
Actual phone SM-S938N/S25Ultra, ARM64/API36; no emulator. Inputs are synthetic tones,
not baby media. This validates runtime/performance for this phone, not population
accuracy, intent interpretation or every tester device.

Evidence: ignored `work/caption-arm-20261007-run01/result.json`, measurement.json,
benchmark/recovery-instrumentation.txt and installation logs. RunID
`a04e79c5779240fc853261832276f385`. Exact frozen app/test pins match the previously
reviewed build05 pair. The standalone verifier was separately rerun with
`--infant-caption-candidate` and returned0/statuspass.

|Case|Input|AST elapsed|Sampled peak PSS|Thermal before/after|Native caption windows|
|---|---|---|---|---|---|
|Cold|10s|2486ms|510882KiB|0/0|1|
|Warm1|20s|6300ms|520159KiB|0/0|3|
|Warm2|20s|6157ms|511979KiB|0/0|3|

PSS is sampled process memory, not exact allocation peak or whole-device RAM.
Thermal0 is Android THERMAL_STATUS_NONE; Celsius temperature was not measured.
The durations measure the AST stage, not full camera/decoding/video/UI elapsed.
All fall below the declared30s/900MiB gate. Native output finite in every window.

Benchmark1 and cancellation/recovery2actual instrumentation tests pass with0fail
and0skip. Research/test packages removed, process absence confirmed; the original
production installation metadata/hash stayed unchanged. No production records
were read and this is not a signed beta update/record-migration test.
Temporary APK copies removed. Owned run storage0→4450B at snapshot; final result
is additional small metadata. Drive free bytes77039386624→77146931200 reflect
system-wide changes and are not an exact reclaimed-byte measurement.

Independent Spec and Standards reviews each confirmed0mandatory findings from
actual artifact hashes, terminal test logs, device metadata and cleanup evidence.
Product remains0.5.18/code32 and no new beta submission yet.
