#!/usr/bin/env bash
# Compiles the new classes (pure logic + UI) against android.jar and the app's own classes,
# then converts them to a dex. App and library classes are used only as a compile classpath and are
# NOT put into the dex. Output: work/nearby-dex/classes.dex
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:-$HOME/Android/Sdk/platforms/android-36/android.jar}"
APP_CP="${APP_CP:-$ROOT/work/classpath/app-classes-v52.jar}"
OUT="$ROOT/work/nearby-dex"
for f in "$ANDROID_JAR" "$APP_CP"; do
  [ -f "$f" ] || { echo "missing $f" >&2; exit 1; }
done
CLS="$(mktemp -d "$ROOT/work/nearby-classes.XXXXXX")"
mkdir -p "$OUT"
javac --release 11 -proc:none -Xlint:all,-options,-deprecation -d "$CLS" -cp "$ANDROID_JAR:$APP_CP" \
  "$ROOT"/app/logic/kz/onay/nearby/logic/*.java "$ROOT"/app/ui/kz/onay/nearby/ui/*.java
find "$CLS" -name '*.class' | sort > "$CLS/classes.list"
test -s "$CLS/classes.list"
D8_OUT="$(mktemp -d "$ROOT/work/nearby-d8.XXXXXX")"
d8 --release --min-api 24 --lib "$ANDROID_JAR" --classpath "$APP_CP" --output "$D8_OUT" $(cat "$CLS/classes.list")
cp "$D8_OUT/classes.dex" "$OUT/classes.dex"
echo "dex: $OUT/classes.dex ($(wc -c < "$OUT/classes.dex") bytes, $(wc -l < "$CLS/classes.list") classes)"
