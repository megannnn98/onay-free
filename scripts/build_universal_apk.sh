#!/usr/bin/env bash
# Merge the signed ONAY! split set into one installable APK and re-sign it.
# Accepts legacy split directories/.apks and already merged .apk releases.
# The latter is preferred to preserve recent modifications.
# ONAY_PACKAGE_NAME=kz.onay.free creates an independently installable app.
# ONAY_PACKAGE_NAME=kz.onay retains legacy behavior for forensic comparisons.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPLITS_INPUT="${SPLITS_INPUT:-$ROOT/work/out}"
OUTPUT_APK="${OUTPUT_APK:-$ROOT/work/release/onay-free.apk}"
DEFAULT_EDITOR="$ROOT/work/tools/APKEditor-1.4.9.jar"
APKEDITOR_JAR="${APKEDITOR_JAR:-$DEFAULT_EDITOR}"
EDITOR_URL="https://github.com/REAndroid/APKEditor/releases/download/V1.4.9/APKEditor-1.4.9.jar"
EDITOR_SHA256="a9cd40df818845456be6d696de6110c89edf4b0a0580cb83438ed6b25a366e67"

: "${ONAY_KS:?set ONAY_KS to the signing keystore path}"
: "${ONAY_KS_PASS:?set ONAY_KS_PASS to the signing key password}"
ALIAS="${ONAY_KS_ALIAS:-test}"
APKTOOL_JAR="${APKTOOL_JAR:-}"
ONAY_PACKAGE_NAME="${ONAY_PACKAGE_NAME:-kz.onay.free}"
case "$ONAY_PACKAGE_NAME" in
  kz.onay|kz.onay.free) ;;
  *) echo "unsupported ONAY_PACKAGE_NAME: $ONAY_PACKAGE_NAME" >&2; exit 1 ;;
esac

for tool in java zipalign apksigner unzip; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done
[ -f "$ONAY_KS" ] || { echo "missing signing keystore: $ONAY_KS" >&2; exit 1; }

# A directory contains already selected splits for one device, not all
# configurations from a full bundletool output.
SOURCE_SINGLE_APK=0
if [ -d "$SPLITS_INPUT" ]; then
  [ -f "$SPLITS_INPUT/base.apk" ] || { echo "missing base.apk in $SPLITS_INPUT" >&2; exit 1; }
  [ -f "$SPLITS_INPUT/split_config.arm64_v8a.apk" ] || {
    echo "missing arm64 split in $SPLITS_INPUT" >&2; exit 1;
  }
elif [ -f "$SPLITS_INPUT" ] && [[ "$SPLITS_INPUT" == *.apks ]]; then
  listing="$(unzip -Z1 "$SPLITS_INPUT")"
  grep -qx 'base.apk' <<<"$listing" || { echo "archive has no base.apk" >&2; exit 1; }
  grep -qx 'split_config.arm64_v8a.apk' <<<"$listing" || {
    echo "archive has no arm64 split" >&2; exit 1;
  }
elif [ -f "$SPLITS_INPUT" ] && [[ "$SPLITS_INPUT" == *.apk ]]; then
  SOURCE_SINGLE_APK=1
  unzip -tqq "$SPLITS_INPUT" || { echo "invalid source APK" >&2; exit 1; }
  listing="$(unzip -Z1 "$SPLITS_INPUT")"
  grep -qx 'AndroidManifest.xml' <<<"$listing" || { echo "source APK has no manifest" >&2; exit 1; }
  grep -Eq '^lib/arm64-v8a/[^/]+\.so$' <<<"$listing" || {
    echo "source APK has no arm64 libraries" >&2; exit 1;
  }
else
  echo "SPLITS_INPUT must be a split directory, .apks archive, or merged .apk: $SPLITS_INPUT" >&2
  exit 1
fi

# Only one ABI should be packaged. Other architectures require a separate build.
if [ "$SOURCE_SINGLE_APK" = "1" ]; then
  abi_splits=(split_config.arm64_v8a.apk)
elif [ -d "$SPLITS_INPUT" ]; then
  mapfile -t abi_splits < <(find "$SPLITS_INPUT" -maxdepth 1 -type f -name 'split_config.*.apk' -printf '%f\n' \
    | grep -E '^split_config\.(arm64_v8a|armeabi_v7a|x86|x86_64)\.apk$' || true)
else
  mapfile -t abi_splits < <(printf '%s\n' "$listing" \
    | grep -E '^split_config\.(arm64_v8a|armeabi_v7a|x86|x86_64)\.apk$' || true)
fi
if [ "${#abi_splits[@]}" -ne 1 ] || [ "${abi_splits[0]}" != split_config.arm64_v8a.apk ]; then
  echo "expected only the arm64 split, found: ${abi_splits[*]}" >&2
  exit 1
fi

if [ "$SOURCE_SINGLE_APK" = "0" ]; then
if [ ! -f "$APKEDITOR_JAR" ]; then
  if [ "$APKEDITOR_JAR" != "$DEFAULT_EDITOR" ]; then
    echo "missing APKEditor jar: $APKEDITOR_JAR" >&2
    exit 1
  fi
  command -v curl >/dev/null || { echo "missing tool: curl" >&2; exit 1; }
  mkdir -p "$(dirname "$DEFAULT_EDITOR")"
  download="$(mktemp "${DEFAULT_EDITOR}.download.XXXXXXXX")"
  trap 'rm -f "$download"' EXIT
  curl --fail --location --silent --show-error "$EDITOR_URL" -o "$download"
  actual="$(sha256sum "$download" | cut -d' ' -f1)"
  [ "$actual" = "$EDITOR_SHA256" ] || { echo "APKEditor sha256 mismatch" >&2; exit 1; }
  mv "$download" "$DEFAULT_EDITOR"
  trap - EXIT
fi
# Check even a cached copy of the pinned upstream tool (but not an explicit override).
if [ "$APKEDITOR_JAR" = "$DEFAULT_EDITOR" ]; then
  actual="$(sha256sum "$APKEDITOR_JAR" | cut -d' ' -f1)"
  [ "$actual" = "$EDITOR_SHA256" ] || { echo "APKEditor sha256 mismatch" >&2; exit 1; }
fi
fi

work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT

if [ "$SOURCE_SINGLE_APK" = "1" ]; then
  echo "[1/4] use existing merged APK (preserves latest code)"
  cp "$SPLITS_INPUT" "$work/merged-unsigned.apk"
else
  echo "[1/4] merge split APKs"
  java -Xmx6g -jar "$APKEDITOR_JAR" m -i "$SPLITS_INPUT" \
    -o "$work/merged-unsigned.apk" -validate-modules -clean-meta
  [ -s "$work/merged-unsigned.apk" ] || { echo "APKEditor produced no APK" >&2; exit 1; }
fi

# Only the *merged standalone APK* changes package identity. Existing split
# APKs are intentionally kept as legacy kz.onay; the OS never sees them.
unsigned="$work/merged-unsigned.apk"
if [ "$ONAY_PACKAGE_NAME" = "kz.onay.free" ]; then
  echo "[identity] preserve DEX namespace; rebase manifest package after split merge"
  [ -n "$APKTOOL_JAR" ] || command -v apktool >/dev/null || {
    echo "clone build needs apktool or APKTOOL_JAR" >&2; exit 1;
  }
  command -v aapt2 >/dev/null || { echo "clone build needs aapt2" >&2; exit 1; }
  command -v python3 >/dev/null || { echo "clone build needs python3" >&2; exit 1; }
  run_apktool() {
    if [ -n "$APKTOOL_JAR" ]; then
      java -Xmx6g -jar "$APKTOOL_JAR" "$@"
    else
      apktool -JXmx6g "$@"
    fi
  }
  run_apktool d -f -r -s -o "$work/decoded" "$unsigned"
  python3 -I "$ROOT/scripts/patch_clone_manifest.py" "$work/decoded/AndroidManifest.xml"
  run_apktool b -f -o "$work/cloned-unsigned.apk" "$work/decoded"
  python3 -I "$ROOT/scripts/verify_clone_payload.py" "$unsigned" "$work/cloned-unsigned.apk"
  unsigned="$work/cloned-unsigned.apk"
  actual_package="$(aapt2 dump badging "$unsigned" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
  [ "$actual_package" = "$ONAY_PACKAGE_NAME" ] || {
    echo "clone built as $actual_package, expected $ONAY_PACKAGE_NAME" >&2; exit 1;
  }
fi

echo "[2/4] align"
zipalign -f -p 4 "$unsigned" "$work/aligned.apk"
zipalign -c -p 4 "$work/aligned.apk"

echo "[3/4] sign with the same key as the split build"
apksigner sign --ks "$ONAY_KS" --ks-pass env:ONAY_KS_PASS \
  --key-pass env:ONAY_KS_PASS --ks-key-alias "$ALIAS" \
  --out "$work/signed.apk" "$work/aligned.apk"

# Validate the APK rather than treating a successful merge command as proof.
echo "[4/4] verify"
apksigner verify --verbose "$work/signed.apk"
zipalign -c -p 4 "$work/signed.apk"
unzip -tqq "$work/signed.apk"
files="$(unzip -Z1 "$work/signed.apk")"
for entry in AndroidManifest.xml resources.arsc classes.dex; do
  grep -qx "$entry" <<<"$files" || { echo "merged APK is missing $entry" >&2; exit 1; }
done
# The arm64 native libraries must no longer depend on a separate split.
grep -Eq '^lib/arm64-v8a/[^/]+\.so$' <<<"$files" || {
  echo "merged APK is missing arm64 libraries" >&2; exit 1;
}

mkdir -p "$(dirname "$OUTPUT_APK")"
cp "$work/signed.apk" "$OUTPUT_APK"
echo "ready: $OUTPUT_APK"
echo "The APK still needs a real-device smoke test before publishing a release."
