"""The ONAY Free launcher icon: generated resources, patch script, and (when an APK is available) the result."""
import hashlib
import importlib.util
import os
import re
import shutil
import struct
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "assets", "icon", "res")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def load_patch():
    spec = importlib.util.spec_from_file_location("patch_app_icon", os.path.join(ROOT, "scripts", "patch_app_icon.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def webp_info(path):
    """(width, height, has_alpha) of a lossless WebP (VP8L) without any imaging library."""
    with open(path, "rb") as f:
        head = f.read(30)
    assert head[:4] == b"RIFF" and head[8:12] == b"WEBP" and head[12:16] == b"VP8L", path
    assert head[20] == 0x2F, path
    bits = struct.unpack("<I", head[21:25])[0]
    return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1, bool((bits >> 28) & 1)


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


MANIFEST = ('<?xml version="1.0" encoding="utf-8"?><manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<application android:icon="@mipmap/ic_launcher_autumn" android:label="@string/application_name" '
            'android:roundIcon="@mipmap/ic_launcher_autumn_round"><activity android:name=".Main"/></application></manifest>')

ORIGINAL_ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon
  xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_autumn_background" />
    <foreground android:drawable="@mipmap/ic_launcher_autumn_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_autumn_foreground" />
</adaptive-icon>
"""


def make_decoded(root, manifest=MANIFEST, adaptive=ORIGINAL_ADAPTIVE):
    """A minimal decoded APK that has everything the patch expects to replace."""
    with open(os.path.join(root, "AndroidManifest.xml"), "w", encoding="utf-8") as f:
        f.write(manifest)
    for density in DENSITIES:
        directory = os.path.join(root, "res", "mipmap-" + density)
        os.makedirs(directory)
        for name in ("ic_launcher_autumn", "ic_launcher_autumn_round", "ic_launcher_autumn_foreground"):
            with open(os.path.join(directory, name + ".webp"), "wb") as f:
                f.write(b"ONAY-ORIGINAL")
    os.makedirs(os.path.join(root, "res", "drawable"))
    with open(os.path.join(root, "res", "drawable", "ic_launcher_autumn_background.xml"), "w") as f:
        f.write("<vector/>")
    os.makedirs(os.path.join(root, "res", "mipmap-anydpi-v26"))
    for suffix in ("", "_round"):
        with open(os.path.join(root, "res", "mipmap-anydpi-v26", "ic_launcher_autumn%s.xml" % suffix), "w") as f:
            f.write(adaptive)


class GeneratedResources(unittest.TestCase):
    def test_every_density_has_the_right_sizes(self):
        for density, factor in DENSITIES.items():
            directory = os.path.join(RES, "mipmap-" + density)
            legacy = round(48 * factor)
            canvas = round(108 * factor)
            expected = {
                "ic_launcher_autumn": legacy,
                "ic_launcher_autumn_round": legacy,
                "ic_launcher_autumn_foreground": canvas,
                "ic_launcher_autumn_monochrome": canvas,
            }
            for name, size in expected.items():
                width, height, alpha = webp_info(os.path.join(directory, name + ".webp"))
                self.assertEqual((width, height), (size, size), "%s/%s" % (density, name))
                self.assertTrue(alpha, "%s/%s needs an alpha channel" % (density, name))

    def test_legacy_sizes_are_the_android_standard(self):
        self.assertEqual([webp_info(os.path.join(RES, "mipmap-" + d, "ic_launcher_autumn.webp"))[0] for d in DENSITIES],
                         [48, 72, 96, 144, 192])

    def test_icons_are_resampled_not_copies_of_one_bitmap(self):
        hashes = {sha256(os.path.join(RES, "mipmap-" + d, "ic_launcher_autumn.webp")) for d in DENSITIES}
        self.assertEqual(len(hashes), len(DENSITIES))

    def test_source_image_is_kept_in_the_repository(self):
        self.assertTrue(os.path.isfile(os.path.join(ROOT, "assets", "icon", "onay-free-icon-source.png")))

    @unittest.skipUnless(importlib.util.find_spec("PIL"), "Pillow is not installed")
    def test_pixels(self):
        from PIL import Image
        directory = os.path.join(RES, "mipmap-xxxhdpi")
        legacy = Image.open(os.path.join(directory, "ic_launcher_autumn.webp")).convert("RGBA")
        self.assertEqual(legacy.getpixel((0, 0))[3], 0, "legacy icon corners are transparent")
        self.assertEqual(legacy.getpixel((96, 8))[:3], (0x14, 0x1A, 0x23), "background colour of the tile")
        rounded = Image.open(os.path.join(directory, "ic_launcher_autumn_round.webp")).convert("RGBA")
        self.assertEqual(rounded.getpixel((0, 0))[3], 0, "round icon corners are transparent")
        self.assertEqual(rounded.getpixel((96, 96))[3], 255)
        foreground = Image.open(os.path.join(directory, "ic_launcher_autumn_foreground.webp")).convert("RGBA")
        self.assertEqual(foreground.getpixel((0, 0))[3], 0, "adaptive foreground edges are transparent")
        box = foreground.getchannel("A").getbbox()
        centre = (foreground.width / 2, foreground.height / 2)
        safe = 33 * 4   # 66dp safe zone of the adaptive icon, radius in xxxhdpi pixels
        opaque = [(x, y) for x in range(0, foreground.width, 4) for y in range(0, foreground.height, 4)
                  if foreground.getpixel((x, y))[3] > 200 and max(foreground.getpixel((x, y))[:3]) > 150]
        self.assertTrue(opaque, "bright shapes exist")
        farthest = max(((x - centre[0]) ** 2 + (y - centre[1]) ** 2) ** 0.5 for x, y in opaque)
        self.assertLessEqual(farthest, safe + 4, "white and lime shapes stay inside the 66dp safe zone")
        self.assertIsNotNone(box)
        mono = Image.open(os.path.join(directory, "ic_launcher_autumn_monochrome.webp")).convert("RGBA")
        for x in range(0, mono.width, 6):
            for y in range(0, mono.height, 6):
                r, g, b, a = mono.getpixel((x, y))
                if a:
                    self.assertEqual((r, g, b), (255, 255, 255), "the themed icon is a single colour")


class PatchScript(unittest.TestCase):
    def setUp(self):
        self.module = load_patch()
        self.dir = tempfile.mkdtemp(prefix="icon-test-")
        self.addCleanup(shutil.rmtree, self.dir, True)

    def test_replaces_launcher_files_and_rewrites_xml(self):
        make_decoded(self.dir)
        self.module.patch(self.dir)
        for density in DENSITIES:
            for name in ("ic_launcher_autumn", "ic_launcher_autumn_round", "ic_launcher_autumn_foreground",
                         "ic_launcher_autumn_monochrome"):
                target = os.path.join(self.dir, "res", "mipmap-" + density, name + ".webp")
                self.assertEqual(sha256(target), sha256(os.path.join(RES, "mipmap-" + density, name + ".webp")))
        for suffix in ("", "_round"):
            root = ET.parse(os.path.join(self.dir, "res", "mipmap-anydpi-v26", "ic_launcher_autumn%s.xml" % suffix)).getroot()
            self.assertEqual(root.tag, "adaptive-icon")
            layers = {child.tag: child.get(ANDROID_NS + "drawable") for child in root}
            self.assertEqual(layers, {"background": "@drawable/ic_launcher_autumn_background",
                                      "foreground": "@mipmap/ic_launcher_autumn_foreground",
                                      "monochrome": "@mipmap/ic_launcher_autumn_monochrome"})
        background = ET.parse(os.path.join(self.dir, "res", "drawable", "ic_launcher_autumn_background.xml")).getroot()
        path = background.find("path")
        self.assertEqual(path.get(ANDROID_NS + "fillColor").upper(), "#FF141A23")

    def test_no_original_bytes_remain(self):
        make_decoded(self.dir)
        self.module.patch(self.dir)
        for current, _, files in os.walk(os.path.join(self.dir, "res")):
            for name in files:
                with open(os.path.join(current, name), "rb") as f:
                    self.assertNotEqual(f.read(), b"ONAY-ORIGINAL", os.path.join(current, name))

    def test_idempotent(self):
        make_decoded(self.dir)
        self.module.patch(self.dir)
        self.module.patch(self.dir)

    def test_manifest_icon_references_are_checked(self):
        make_decoded(self.dir, manifest=MANIFEST.replace('android:roundIcon="@mipmap/ic_launcher_autumn_round"',
                                                         'android:roundIcon="@mipmap/ic_launcher_winter_round"'))
        with self.assertRaises(SystemExit) as caught:
            self.module.patch(self.dir)
        self.assertIn("roundIcon", str(caught.exception))

    def test_missing_manifest_icon_stops(self):
        make_decoded(self.dir, manifest=MANIFEST.replace('android:icon="@mipmap/ic_launcher_autumn" ', ""))
        with self.assertRaises(SystemExit):
            self.module.patch(self.dir)

    def test_activity_alias_stops(self):
        make_decoded(self.dir, manifest=MANIFEST.replace("</application>", '<activity-alias android:name=".A"/></application>'))
        with self.assertRaises(SystemExit):
            self.module.patch(self.dir)

    def test_missing_original_file_stops_before_anything_is_written(self):
        make_decoded(self.dir)
        os.remove(os.path.join(self.dir, "res", "mipmap-hdpi", "ic_launcher_autumn_foreground.webp"))
        with self.assertRaises(SystemExit):
            self.module.patch(self.dir)
        with open(os.path.join(self.dir, "res", "mipmap-mdpi", "ic_launcher_autumn.webp"), "rb") as f:
            self.assertEqual(f.read(), b"ONAY-ORIGINAL")

    def test_unexpected_adaptive_format_stops(self):
        make_decoded(self.dir, adaptive=ORIGINAL_ADAPTIVE.replace("<monochrome", "<inset"))
        with self.assertRaises(SystemExit):
            self.module.patch(self.dir)

    def test_build_script_runs_the_patch_before_apktool_build(self):
        with open(os.path.join(ROOT, "scripts", "build_apk.sh"), encoding="utf-8") as f:
            text = f.read()
        patch_at = text.index("patch_app_icon.py")
        self.assertLess(patch_at, text.index("apktool -JXmx10g b "))


def apk_under_test():
    path = os.environ.get("ICON_TEST_APK")   # explicit only: a stale APK left in work/ would fail for the wrong reason
    return path if path and os.path.isfile(path) else None


@unittest.skipUnless(apk_under_test(), "set ICON_TEST_APK=<built apk> to check the build result")
class BuiltApk(unittest.TestCase):
    """Run after scripts/build_apk.sh; checks what the installer will see."""

    @classmethod
    def setUpClass(cls):
        cls.apk = apk_under_test()
        cls.zip = zipfile.ZipFile(cls.apk)
        cls.names = set(cls.zip.namelist())

    def icon_entries(self, name):
        return sorted(n for n in self.names if re.fullmatch(r"res/mipmap-[a-z]+dpi(-v\d+)?/%s\.webp" % name, n))

    def test_launcher_bitmaps_are_ours_for_every_density(self):
        for name in ("ic_launcher_autumn", "ic_launcher_autumn_round", "ic_launcher_autumn_foreground",
                     "ic_launcher_autumn_monochrome"):
            entries = self.icon_entries(name)
            self.assertEqual(len(entries), len(DENSITIES), "%s: %s" % (name, entries))
            expected = {sha256(os.path.join(RES, "mipmap-" + d, name + ".webp")) for d in DENSITIES}
            actual = {hashlib.sha256(self.zip.read(entry)).hexdigest() for entry in entries}
            self.assertEqual(actual, expected, name)

    def test_adaptive_icons_are_compiled_and_present(self):
        for suffix in ("", "_round"):
            self.assertIn("res/mipmap-anydpi-v26/ic_launcher_autumn%s.xml" % suffix, self.names)

    def test_aapt2_reports_the_launcher_icon_and_package(self):
        badging = subprocess.run(["aapt2", "dump", "badging", self.apk], capture_output=True, text=True, check=True).stdout
        self.assertRegex(badging, r"^package: name='kz\.onay\.free'")
        self.assertRegex(badging, r"application: label='[^']*' icon='res/mipmap-anydpi-v26/ic_launcher_autumn\.xml'")
        self.assertNotRegex(badging, r"application-icon[^\n]*ic_launcher_(summer|spring|new|debug)")


if __name__ == "__main__":
    unittest.main()
