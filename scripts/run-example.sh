#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/build.sh"

JAR="$ROOT/build/jdk-builder-processor.jar"
OUT="$ROOT/build/example/classes"
GEN="$ROOT/build/example/generated"
rm -rf "$ROOT/build/example"
mkdir -p "$OUT" "$GEN"

mapfile -t SOURCES < <(find "$ROOT/examples/src" -name '*.java' -print | sort)

javac \
  --release 21 \
  -cp "$JAR" \
  -processorpath "$JAR" \
  -s "$GEN" \
  -d "$OUT" \
  "${SOURCES[@]}"

java -cp "$JAR:$OUT" example.ExampleMain
