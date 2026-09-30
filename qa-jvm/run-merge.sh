#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLIN_TOOLS:?Point to the official Maven compiler/dependency directory documented in qa-jvm/README.md}"
source=${RECORD_TRANSFER_SOURCE:-outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/RecordTransfer.kt}
case "${1:-}" in
  '') test_source=qa-jvm/MergeRegression.kt; main=MergeRegressionKt ;;
  --collision-only) test_source=qa-jvm/MergeCollisionRepro.kt; main=MergeCollisionReproKt ;;
  *) echo "Usage: $0 [--collision-only]" >&2; exit 2 ;;
esac
mkdir -p qa-jvm/build
CP=$(printf '%s:' "$KOTLIN_TOOLS"/*.jar); CP=${CP%:}
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" \
  -d qa-jvm/build/merge.jar "$source" qa-jvm/StoreContract.kt "$test_source"
java -cp "qa-jvm/build/merge.jar:$CP" "com.moa.babylanguage.$main"
