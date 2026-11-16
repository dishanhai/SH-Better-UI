#!/usr/bin/env sh
# CPU-only, reproducible before/after pipeline measurement. Requires a full JDK 17+.
set -eu
cd "$(dirname "$0")/.."
OUT=$(mktemp -d "${TMPDIR:-/tmp}/shb-dither-benchmark.XXXXXX")
trap 'rm -rf "$OUT"' EXIT INT TERM
if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"; else JAVAC=javac; JAVA=java; fi
"$JAVAC" --release 17 -d "$OUT" \
  src/kernel/java/com/dishanhai/sh_better_ui/kernel/api/effect/Dither.java \
  src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/DitherImagePipeline.java \
  src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/DitherUploadBudget.java \
  scripts/benchmarks/DitherPipelineBenchmark.java
"$JAVA" -Djava.awt.headless=true -cp "$OUT" com.dishanhai.sh_better_ui.client.gui.scaled.DitherPipelineBenchmark \
  "${1:-src/main/resources/assets/sh_better_ui/textures/background/dither_demo.jpg}" "${2:-cold}"
