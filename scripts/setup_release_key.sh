#!/usr/bin/env bash
# One-time release key provisioning on the OWNER's machine.
# NEVER run this for an existing kz.onay.free release unless intentionally resetting updates.
set -euo pipefail
set +x
umask 077

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$ROOT/work/keys/onay-free-release.jks"
REPO="megannnn98/onay-free"

for cmd in keytool gh base64; do
  command -v "$cmd" >/dev/null || { echo "missing $cmd" >&2; exit 1; }
done
gh auth status >/dev/null
[ ! -e "$KEYSTORE" ] || { echo "refusing to overwrite existing keystore: $KEYSTORE" >&2; exit 1; }
mkdir -p "$(dirname "$KEYSTORE")"

read -rsp "New release keystore password: " ONAY_KS_PASS; echo
read -rsp "Confirm password: " CONFIRM; echo
[ "$ONAY_KS_PASS" = "$CONFIRM" ] || { echo "passwords do not match" >&2; exit 1; }
[ "${#ONAY_KS_PASS}" -ge 12 ] || { echo "password must have at least 12 characters" >&2; exit 1; }
unset CONFIRM
export ONAY_KS_PASS
trap 'unset ONAY_KS_PASS' EXIT

# The :env syntax keeps the password out of keytool's command-line arguments.
keytool -genkeypair -alias test -keyalg RSA -keysize 3072 \
  -validity 3650 -storetype JKS -keystore "$KEYSTORE" \
  -storepass:env ONAY_KS_PASS -keypass:env ONAY_KS_PASS \
  -dname "CN=ONAY Free, OU=Independent, O=Independent, C=KZ" -noprompt

base64 -w0 "$KEYSTORE" | gh secret set ONAY_KEYSTORE_B64 -R "$REPO"
printf '%s' "$ONAY_KS_PASS" | gh secret set ONAY_KS_PASS -R "$REPO"
printf '%s' test | gh secret set ONAY_KS_ALIAS -R "$REPO"

echo "Release keystore created and repository secrets configured."
echo "BACK UP this keystore securely (and the password) outside this computer:"
echo "$KEYSTORE"
echo "Do NOT delete this file. Future updates need the same key."
