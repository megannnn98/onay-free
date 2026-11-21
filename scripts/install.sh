#!/usr/bin/env bash
# Installs the built split set on the connected device (replaces a build signed with the same key).
# Usage: scripts/install.sh [out_dir]    default out_dir: work/out
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/work/out}"
[ -f "$OUT_DIR/base.apk" ] || { echo "missing $OUT_DIR/base.apk" >&2; exit 1; }
# With several devices attached set ANDROID_SERIAL to choose one (adb honours it).
if [ -z "${ANDROID_SERIAL:-}" ]; then
  n="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)"
  [ "$n" -eq 1 ] || { echo "expected exactly one adb device (or set ANDROID_SERIAL), found $n" >&2; exit 1; }
fi
adb install-multiple -r "$OUT_DIR"/base.apk "$OUT_DIR"/split_config.*.apk
