#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
JAR="$BUILD/jdk-builder-processor.jar"

rm -rf "$BUILD"
mkdir -p "$CLASSES"

mapfile -t SOURCES < <(find "$ROOT/src/main/java" -name '*.java' -print | sort)

javac \
  --release 21 \
  -proc:none \
  -Xlint:all \
  -Werror \
  -d "$CLASSES" \
  "${SOURCES[@]}"

cp -R "$ROOT/src/main/resources/." "$CLASSES/"
jar --create --file "$JAR" -C "$CLASSES" .

echo "Built $JAR"
