"""build_release_apks.sh builds both variants from one patched split set and verifies each of them."""
import os
import re
import subprocess
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(name):
    with open(os.path.join(ROOT, "scripts", name), encoding="utf-8") as f:
        return f.read()


class ReleaseVariants(unittest.TestCase):
    def test_script_is_valid_bash_and_strict(self):
        text = read("build_release_apks.sh")
        self.assertIn("set -euo pipefail", text)
        subprocess.run(["bash", "-n", os.path.join(ROOT, "scripts", "build_release_apks.sh")], check=True)

    def test_two_variants_with_distinct_file_names_and_packages(self):
        text = read("build_release_apks.sh")
        calls = re.findall(r'^build_variant (\S+) (\S+) "([^"]+)"', text, re.M)
        self.assertEqual([(c[0], c[1]) for c in calls], [("kz.onay.free", "map"), ("kz.onay", "full")])
        self.assertEqual(calls[0][2], "$RELEASE_DIR/onay-free-$VERSION.apk")
        self.assertEqual(calls[1][2], "$RELEASE_DIR/onay-free-$VERSION-kz.onay.apk")

    def test_each_variant_is_verified_for_its_own_package_and_the_release_certificate(self):
        text = read("build_release_apks.sh")
        self.assertIn('EXPECTED_PACKAGE="$package" bash "$ROOT/scripts/verify_universal_apk.sh" "$output" "$CERT"', text)
        self.assertIn("797be9d313ff09598d9cd04d2e3875c55098599f8913cd27a490b6bff559f7af", text)

    def test_every_variant_has_its_own_split_set_without_the_debug_activity(self):
        text = read("build_release_apks.sh")
        self.assertEqual(text.count("scripts/build_apk.sh"), 1, "called from build_variant, once per variant")
        self.assertIn('ONAY_VARIANT="$variant" ONAY_BUILD_UNIVERSAL=0 ONAY_DEBUG_ACTIVITY=0 OUT_DIR="$OUT_DIR/$variant"', text)
        self.assertEqual(text.count("build_dex.sh"), 1, "the dex is shared")

    def test_build_apk_selects_the_layout_patch_by_variant(self):
        text = read("build_apk.sh")
        self.assertRegex(text, r'map\) python3 -I "\$ROOT/scripts/patch_layout.py" "\$WORK/decoded" ;;')
        self.assertRegex(text, r'full\) python3 -I "\$ROOT/scripts/patch_layout.py" "\$WORK/decoded" --keep-navigation ;;')
        self.assertIn("ONAY_VARIANT must be map or full", text)

    def test_version_is_validated(self):
        text = read("build_release_apks.sh")
        self.assertIn("^[0-9]+\\.[0-9]+\\.[0-9]+$", text)

    def test_install_accepts_both_packages_and_nothing_else(self):
        text = read("install.sh")
        self.assertIn("kz.onay.free) ;;", text)
        self.assertRegex(text, r"\n    kz\.onay\)\n")
        self.assertIn("REFUSING to install", text)
        subprocess.run(["bash", "-n", os.path.join(ROOT, "scripts", "install.sh")], check=True)


if __name__ == "__main__":
    unittest.main()
