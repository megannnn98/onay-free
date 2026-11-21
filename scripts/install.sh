#!/usr/bin/env bash
# Install the independent single APK. Legacy split installation is opt-in only.
# Usage: scripts/install.sh [path/to/onay-free.apk | --legacy-splits]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/work/release/onay-free.apk}"
if [ -z "${ANDROID_SERIAL:-}" ]; then
  n="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)"
  [ "$n" -eq 1 ] || { echo "expected exactly one adb device (or set ANDROID_SERIAL), found $n" >&2; exit 1; }
fi
if [ "$APK" = "--legacy-splits" ]; then
  OUT_DIR="$ROOT/work/out"
  [ -f "$OUT_DIR/base.apk" ] || { echo "missing $OUT_DIR/base.apk" >&2; exit 1; }
  echo "WARNING: legacy split package is kz.onay and conflicts with official ONAY!"
  adb install-multiple -r "$OUT_DIR"/base.apk "$OUT_DIR"/split_config.*.apk
else
  [ -f "$APK" ] || { echo "missing APK: $APK" >&2; exit 1; }
  command -v aapt2 >/dev/null || { echo "aapt2 is required to verify app identity" >&2; exit 1; }
  actual_package="$(aapt2 dump badging "$APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
  [ "$actual_package" = "kz.onay.free" ] || {
    echo "REFUSING to install $actual_package: expected independent package kz.onay.free" >&2
    exit 1
  }
  adb install -r "$APK"
fi
