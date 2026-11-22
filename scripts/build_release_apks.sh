#!/usr/bin/env bash
# Builds both release variants of ONAY Free from the current sources and verifies them.
#
#   kz.onay.free  onay-free-<version>.apk              the map only (no bottom bar, no payment); installs next to the
#                                                      official ONAY! (the default choice)
#   kz.onay       onay-free-<version>-kz.onay.apk      the original bottom bar and payment stay (ONAY_VARIANT=full);
#                                                      keeps the original package name, so the official ONAY! has to
#                                                      be uninstalled first (other signature)
#
# Usage: scripts/build_release_apks.sh <version>      (for example 0.0.8)
# Environment (the key is never stored in the repository):
#   ONAY_KS, ONAY_KS_PASS, ONAY_KS_ALIAS   as for build_apk.sh
#   ONAY_CERT_SHA256                       expected certificate (default: the one of the previous releases)
# Output: work/release/. The version is only a file name; the app version stays the one of ONAY! 3.6.5.
set -euo pipefail

VERSION="${1:?usage: build_release_apks.sh <version>}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 0.0.8, got: $VERSION" >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${ONAY_KS:?set ONAY_KS to the signing keystore path}"
: "${ONAY_KS_PASS:?set ONAY_KS_PASS}"
CERT="${ONAY_CERT_SHA256:-797be9d313ff09598d9cd04d2e3875c55098599f8913cd27a490b6bff559f7af}"
OUT_DIR="${OUT_DIR:-$ROOT/work/out-release}"
RELEASE_DIR="$ROOT/work/release"
mkdir -p "$RELEASE_DIR"

echo "== [1/3] dex"
bash "$ROOT/scripts/build_dex.sh"

# The layout patch differs between the variants, so each one has its own patched split set.
build_variant() {
  local package="$1" variant="$2" output="$3" step="$4"
  echo "== [$step/3] $package ($variant) -> $(basename "$output")"
  ONAY_VARIANT="$variant" ONAY_BUILD_UNIVERSAL=0 ONAY_DEBUG_ACTIVITY=0 OUT_DIR="$OUT_DIR/$variant" \
    bash "$ROOT/scripts/build_apk.sh"
  SPLITS_INPUT="$OUT_DIR/$variant" OUTPUT_APK="$output" ONAY_PACKAGE_NAME="$package" \
    bash "$ROOT/scripts/build_universal_apk.sh"
  EXPECTED_PACKAGE="$package" bash "$ROOT/scripts/verify_universal_apk.sh" "$output" "$CERT"
}

build_variant kz.onay.free map "$RELEASE_DIR/onay-free-$VERSION.apk" 2
build_variant kz.onay full "$RELEASE_DIR/onay-free-$VERSION-kz.onay.apk" 3

echo "== done"
sha256sum "$RELEASE_DIR/onay-free-$VERSION.apk" "$RELEASE_DIR/onay-free-$VERSION-kz.onay.apk"
