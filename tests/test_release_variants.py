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
        calls = re.findall(r'^build_variant (\S+) "([^"]+)"', text, re.M)
        self.assertEqual([c[0] for c in calls], ["kz.onay.free", "kz.onay"])
        self.assertEqual(calls[0][1], "$RELEASE_DIR/onay-free-$VERSION.apk")
        self.assertEqual(calls[1][1], "$RELEASE_DIR/onay-free-$VERSION-kz.onay.apk")

    def test_each_variant_is_verified_for_its_own_package_and_the_release_certificate(self):
        text = read("build_release_apks.sh")
        self.assertIn('EXPECTED_PACKAGE="$package" bash "$ROOT/scripts/verify_universal_apk.sh" "$output" "$CERT"', text)
        self.assertIn("797be9d313ff09598d9cd04d2e3875c55098599f8913cd27a490b6bff559f7af", text)

    def test_split_set_is_built_once_without_the_debug_activity(self):
        text = read("build_release_apks.sh")
        self.assertEqual(text.count("scripts/build_apk.sh"), 1)
        self.assertIn("ONAY_BUILD_UNIVERSAL=0 ONAY_DEBUG_ACTIVITY=0", text)

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
