#!/usr/bin/env bash
# Builds the modified ONAY! split set from the ORIGINAL splits, reproducibly.
#
#   original base.apk -> apktool decode (fresh dir every time) -> patch manifest
#   -> apktool build (-JXmx10g, -f) -> add new dex -> zipalign -> apksigner
#   original splits   -> re-signed with the same key
#
# Required environment (the key is NEVER stored in the repository):
#   ONAY_KS        path to the test keystore (jks)
#   ONAY_KS_PASS   keystore and key password
#   ONAY_KS_ALIAS  key alias (default: test)
# Optional: ORIG_DIR (default work/apks), OUT_DIR (default work/out)
#   ONAY_DEBUG_ACTIVITY=1  also adds the exported debug activity (adb entry point with test data and
#                          location override); the default release build has no such entry point.
# Needs a dex produced by scripts/build_dex.sh (work/nearby-dex/classes.dex).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ORIG_DIR="${ORIG_DIR:-$ROOT/work/apks}"
OUT_DIR="${OUT_DIR:-$ROOT/work/out}"
DEX="$ROOT/work/nearby-dex/classes.dex"
: "${ONAY_KS:?set ONAY_KS to the keystore path}"
: "${ONAY_KS_PASS:?set ONAY_KS_PASS}"
ALIAS="${ONAY_KS_ALIAS:-test}"

for f in "$ORIG_DIR/base.apk" "$DEX" "$ONAY_KS"; do
  [ -f "$f" ] || { echo "missing: $f" >&2; exit 1; }
done
for t in apktool zipalign apksigner python3; do
  command -v "$t" >/dev/null || { echo "missing tool: $t" >&2; exit 1; }
done

WORK="$(mktemp -d "$ROOT/work/build.XXXXXX")"   # always a fresh directory: no leftovers from failed builds
cleanup() {
  python3 -I -c 'import shutil,sys; shutil.rmtree(sys.argv[1], ignore_errors=True)' "$WORK"
}
trap cleanup EXIT

echo "[1/6] decode"
apktool -JXmx10g d -f -o "$WORK/decoded" "$ORIG_DIR/base.apk" >"$WORK/decode.log" 2>&1 \
  || { tail -20 "$WORK/decode.log" >&2; exit 1; }

echo "[2/6] patch smali, ads, layout and manifest"
python3 -I "$ROOT/scripts/patch_smali.py" "$WORK/decoded"
python3 -I "$ROOT/scripts/patch_ads.py" "$WORK/decoded"
python3 -I "$ROOT/scripts/patch_layout.py" "$WORK/decoded"
if [ "${ONAY_DEBUG_ACTIVITY:-0}" = "1" ]; then
  python3 -I "$ROOT/scripts/patch_manifest.py" "$WORK/decoded/AndroidManifest.xml"
else
  echo "release build: no debug activity in the manifest"
fi

echo "[3/6] build"
apktool -JXmx10g b -f -o "$WORK/base_built.apk" "$WORK/decoded" >"$WORK/build.log" 2>&1 \
  || { tail -20 "$WORK/build.log" >&2; exit 1; }

echo "[4/6] add dex"
python3 -I "$ROOT/scripts/add_dex.py" "$WORK/base_built.apk" "$DEX"

echo "[5/6] align and sign base"
mkdir -p "$OUT_DIR"
zipalign -f -p 4 "$WORK/base_built.apk" "$OUT_DIR/base.apk"
zipalign -c -p 4 "$OUT_DIR/base.apk"
apksigner sign --ks "$ONAY_KS" --ks-pass env:ONAY_KS_PASS --key-pass env:ONAY_KS_PASS \
  --ks-key-alias "$ALIAS" "$OUT_DIR/base.apk" 2> >(grep -v WARNING >&2)
apksigner verify "$OUT_DIR/base.apk" 2> >(grep -v WARNING >&2)

echo "[6/6] re-sign original splits"
for s in "$ORIG_DIR"/split_config.*.apk; do
  cp "$s" "$OUT_DIR/$(basename "$s")"
  apksigner sign --ks "$ONAY_KS" --ks-pass env:ONAY_KS_PASS --key-pass env:ONAY_KS_PASS \
    --ks-key-alias "$ALIAS" "$OUT_DIR/$(basename "$s")" 2> >(grep -v WARNING >&2)
done
python3 -I -c 'import glob,os,sys
for p in glob.glob(os.path.join(sys.argv[1], "*.idsig")): os.remove(p)' "$OUT_DIR"
echo "done: $(ls "$OUT_DIR"/*.apk | wc -l) apk files in $OUT_DIR"
