#!/usr/bin/env bash
# Compiles the pure-logic layer with its JUnit tests and runs them on the JVM.
# Needs work/libs/junit-console.jar (junit-platform-console-standalone 1.11.4).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JUNIT="$ROOT/work/libs/junit-console.jar"
[ -f "$JUNIT" ] || { echo "missing $JUNIT" >&2; exit 1; }
OUT="$(mktemp -d "$ROOT/work/unit-out.XXXXXX")"
javac --release 11 -Xlint:all -Werror -d "$OUT" -cp "$JUNIT" \
  "$ROOT"/app/logic/kz/onay/nearby/logic/*.java "$ROOT"/app/test/kz/onay/nearby/logic/*.java
java -jar "$JUNIT" execute --class-path "$OUT" --scan-class-path --disable-banner
