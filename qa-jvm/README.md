# Pure JVM merge regression

Requires Java 17+ and the following official Maven Central artifacts. Tested with Java 21 and Kotlin 2.2.10. Put jars in an external directory, not this repository:

| Maven group | Artifact | Version |
|---|---|---|
| org.jetbrains.kotlin | kotlin-compiler-embeddable | 2.2.10 |
| org.jetbrains.kotlin | kotlin-stdlib | 2.2.10 |
| org.jetbrains.kotlin | kotlin-script-runtime | 2.2.10 |
| org.jetbrains.kotlin | kotlin-reflect | 1.6.10 |
| org.jetbrains.kotlin | kotlin-daemon-embeddable | 2.2.10 |
| org.jetbrains.kotlinx | kotlinx-coroutines-core-jvm | 1.8.0 |
| org.jetbrains | annotations | 13.0 |
| com.google.android | android | 4.1.1.4 |

Official URL pattern: `https://repo.maven.apache.org/maven2/<group with dots replaced by slashes>/<artifact>/<version>/<artifact>-<version>.jar`. Compiler dependencies are declared in its official POM. Android's jar supplies compile-time signatures for RecordTransfer imports only; Android JSON methods are not invoked by these tests. `StoreContract.kt` intentionally supplies test-only models and validation; this is **not** testing Android storage.

From the repository root:

```sh
KOTLIN_TOOLS=/absolute/path/to/jars bash qa-jvm/run-merge.sh
```

This compiles the **actual complete production RecordTransfer.kt**, then executes eleven merge cases. It never compiles a rewritten merge algorithm. Build outputs stay under ignored `qa-jvm/build`.

To reproduce the original failure, obtain RecordTransfer.kt from verified commit `f42238c341ff890449154562052be15dc7f9b2e7` (also unchanged at `cc8403508dfb311e57c12d4b5d9f1226618ad851`) and run:

```sh
KOTLIN_TOOLS=/absolute/path/to/jars \
RECORD_TRANSFER_SOURCE=/absolute/path/to/baseline/RecordTransfer.kt \
bash qa-jvm/run-merge.sh --collision-only
```

Expected baseline: compilation succeeds, then runtime exits 1 with `Distinct collision record discarded`, additions empty, duplicates 1. Repeat `--collision-only` without source override against the candidate for the same assertion passing. Full candidate run exits 0 with 11/11 checks.

This is not an Android build, SQLite/migration test, JSON parser test, renderer/UI test, or real device/Play test. Run the repository's full Gradle/instrumentation commands separately before merging/release.
