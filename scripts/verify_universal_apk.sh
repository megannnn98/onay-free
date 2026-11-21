#!/usr/bin/env bash
# Static verification of a single (universal) ONAY! APK. Used by CI and by hand before a release.
#
# Usage: scripts/verify_universal_apk.sh <apk> [expected_cert_sha256]
#   EXPECTED_PACKAGE   package name the APK must have (default kz.onay)
#
# It does NOT replace an install and launch test on a real device; it checks that the merge did not leave
# the APK depending on split APKs and that it satisfies the rules the Android installer enforces for
# extractNativeLibs="false".
set -euo pipefail

APK="${1:?usage: verify_universal_apk.sh <apk> [expected_cert_sha256]}"
EXPECTED_CERT="${2:-}"
EXPECTED_PACKAGE="${EXPECTED_PACKAGE:-kz.onay}"

for tool in unzip aapt2 apksigner zipalign; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done
[ -f "$APK" ] || { echo "missing APK: $APK" >&2; exit 1; }

fail() { echo "FAIL: $*" >&2; exit 1; }

echo "[1/7] zip integrity"
unzip -tqq "$APK" >/dev/null || fail "corrupt zip"
entries="$(unzip -Z1 "$APK")"
for entry in AndroidManifest.xml resources.arsc classes.dex; do
  grep -qx "$entry" <<<"$entries" || fail "missing $entry"
done

echo "[2/7] package and ABI"
badging="$(aapt2 dump badging "$APK" 2>/dev/null)"
grep -q "^package: name='${EXPECTED_PACKAGE//./\\.}'" <<<"$badging" || fail "package is not $EXPECTED_PACKAGE"
native="$(sed -n "s/^native-code: //p" <<<"$badging")"
[ "$native" = "'arm64-v8a'" ] || fail "native-code must be only arm64-v8a, got: ${native:-none}"

echo "[3/7] no dependency on split APKs"
manifest="$(aapt2 dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null)"
if grep -Eiq 'isSplitRequired|requiredSplitTypes|splitTypes|com\.android\.vending\.splits|com\.android\.dynamic\.apk\.fused\.modules|android:isFeatureSplit|"split"' <<<"$manifest"; then
  fail "manifest still refers to split APKs"
fi
grep -Eq "^package: .* split=" <<<"$badging" && fail "package is a split APK"

echo "[4/7] native libraries"
libs="$(grep -E '^lib/arm64-v8a/[^/]+\.so$' <<<"$entries" || true)"
[ -n "$libs" ] || fail "no arm64 native libraries inside the APK"
grep -Eq '^lib/' <<<"$entries" && [ -z "$(grep -E '^lib/' <<<"$entries" | grep -vE '^lib/arm64-v8a/')" ] \
  || fail "libraries for other ABIs are present"
if grep -q 'android:extractNativeLibs.*=false' <<<"$manifest"; then
  # The installer requires these entries to be stored uncompressed (and page aligned).
  while read -r method _ _ _ _ _ _ name; do
    case "$name" in
      lib/*.so|resources.arsc)
        [ "$method" = "Stored" ] || fail "$name must be stored uncompressed (is $method)" ;;
    esac
  done < <(unzip -v "$APK" | awk 'NR>3 && $1 != "--------" {print $2, $3, $4, $5, $6, $7, $8, $NF}')
fi

echo "[5/7] alignment"
zipalign -c -p 4 "$APK" || fail "not 4 KB page aligned"

echo "[6/7] signature"
verify="$(apksigner verify --print-certs "$APK" 2>/dev/null)" || fail "apksigner verify failed"
grep -Eq 'Verified using v[23](\.[0-9])? scheme.*: true' <<<"$(apksigner verify --verbose "$APK" 2>/dev/null)" \
  || fail "no v2 or v3 signature"
cert="$(sed -n 's/.*certificate SHA-256 digest: //p' <<<"$verify" | head -n 1)"
[ -n "$cert" ] || fail "cannot read the signing certificate"
if [ -n "$EXPECTED_CERT" ]; then
  [ "${cert,,}" = "${EXPECTED_CERT,,}" ] || fail "signing certificate differs from the expected one"
fi

echo "[7/7] summary"
echo "apk:         $APK"
echo "size:        $(stat -c %s "$APK") bytes"
echo "sha256:      $(sha256sum "$APK" | cut -d' ' -f1)"
echo "package:     $(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging") $(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -n 1)"
echo "certificate: $cert"
echo "OK: static checks passed (install and run it on a device before trusting it)"
