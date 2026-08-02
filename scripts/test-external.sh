#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
GRADLE_BIN="${GRADLE_BIN:-gradle}"
EXTERNAL_BUILD="$ROOT/build/external"
M2_REPO="$ROOT/build/external-m2"

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required external integration tool is unavailable: $tool" >&2
    exit 2
  fi
}

require_tool "$MAVEN_BIN"
require_tool "$GRADLE_BIN"

rm -rf "$EXTERNAL_BUILD" "$M2_REPO"
mkdir -p "$EXTERNAL_BUILD" "$M2_REPO"

"$MAVEN_BIN" \
  --batch-mode \
  --no-transfer-progress \
  -Dmaven.repo.local="$M2_REPO" \
  -DskipTests \
  -f "$ROOT/pom.xml" \
  clean install

"$MAVEN_BIN" \
  --batch-mode \
  --no-transfer-progress \
  -Dmaven.repo.local="$M2_REPO" \
  -f "$ROOT/integration-tests/maven-reactor/pom.xml" \
  clean verify | tee "$EXTERNAL_BUILD/maven.log"

grep -Fq "REAL HIBERNATE VALIDATOR TESTS PASSED" "$EXTERNAL_BUILD/maven.log"

"$GRADLE_BIN" \
  --no-daemon \
  -p "$ROOT/integration-tests/gradle-multiproject" \
  -PjdkBuilderRepo="$M2_REPO" \
  clean check | tee "$EXTERNAL_BUILD/gradle-clean.log"

grep -Fq "REAL HIBERNATE VALIDATOR TESTS PASSED" "$EXTERNAL_BUILD/gradle-clean.log"

"$GRADLE_BIN" \
  --no-daemon \
  --info \
  -p "$ROOT/integration-tests/gradle-multiproject" \
  -PjdkBuilderRepo="$M2_REPO" \
  check | tee "$EXTERNAL_BUILD/gradle-incremental.log"

if ! grep -Eq ":(model|app):compileJava (UP-TO-DATE|FROM-CACHE)" \
    "$EXTERNAL_BUILD/gradle-incremental.log"; then
  echo "Gradle repeat build did not report compileJava as up-to-date or cached" >&2
  exit 1
fi

test -f "$ROOT/integration-tests/gradle-multiproject/model/build/generated/sources/annotationProcessor/java/main/integration/model/OrderBuilder.java"
test -f "$ROOT/integration-tests/gradle-multiproject/app/build/generated/sources/annotationProcessor/java/main/integration/app/PurchaseBuilder.java"

# Verify that a child builder API change invalidates and regenerates dependent generated code.
LINE_SOURCE="$ROOT/integration-tests/maven-reactor/model/src/main/java/integration/model/Line.java"
LINE_BACKUP="$EXTERNAL_BUILD/Line.java.original"
cp "$LINE_SOURCE" "$LINE_BACKUP"
restore_line_source() {
  cp "$LINE_BACKUP" "$LINE_SOURCE"
}
trap restore_line_source EXIT

python3 - "$LINE_SOURCE" <<'PY_LINE'
from pathlib import Path
import sys
path = Path(sys.argv[1])
source = path.read_text()
old = "@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)"
new = "@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED, buildMethod = \"finish\")"
if old not in source:
    raise SystemExit("Line.java incremental-test marker was not found")
path.write_text(source.replace(old, new, 1))
PY_LINE

"$GRADLE_BIN" \
  --no-daemon \
  --info \
  -p "$ROOT/integration-tests/gradle-multiproject" \
  -PjdkBuilderRepo="$M2_REPO" \
  check | tee "$EXTERNAL_BUILD/gradle-child-api-change.log"

grep -Fq "REAL HIBERNATE VALIDATOR TESTS PASSED" "$EXTERNAL_BUILD/gradle-child-api-change.log"

if grep -Fq ':model:compileJava UP-TO-DATE' "$EXTERNAL_BUILD/gradle-child-api-change.log"; then
  echo "Gradle failed to recompile the model after a child builder API change" >&2
  exit 1
fi

grep -Fq 'integration.model.Line finish(' \
  "$ROOT/integration-tests/gradle-multiproject/model/build/generated/sources/annotationProcessor/java/main/integration/model/LineBuilder.java"
grep -Fq 'operation.builder.finish(validator, groups)' \
  "$ROOT/integration-tests/gradle-multiproject/model/build/generated/sources/annotationProcessor/java/main/integration/model/OrderBuilder.java"

restore_line_source
trap - EXIT

echo "EXTERNAL MAVEN, GRADLE, AND HIBERNATE VALIDATOR TESTS PASSED"
