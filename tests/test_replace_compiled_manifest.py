"""Offline checks that cloning swaps only compiled manifest, not assets/code."""
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from replace_compiled_manifest import replace_manifest


class ManifestTransplantTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.original = root / "original.apk"
        self.rebuilt = root / "apktool-output.apk"
        self.output = root / "clone.apk"
        with zipfile.ZipFile(self.original, "w") as z:
            for name, data in {
                "AndroidManifest.xml": b"old binary",
                "classes.dex": b"unchanged classes",
                "resources.arsc": b"unchanged resources",
                "res/xml/paths.xml": b"unchanged compiled xml",
                "assets/protobuf.bin": b"unchanged assets",
                "lib/arm64-v8a/libcore.so": b"unchanged native",
                "META-INF/OLD.SF": b"old signature metadata",
            }.items():
                z.writestr(name, data)
        with zipfile.ZipFile(self.rebuilt, "w") as z:
            z.writestr("AndroidManifest.xml", bytes([3, 0, 8, 0]) + b"new binary")

    def test_manifest_replaced_but_payload_identical(self):
        replace_manifest(self.original, self.rebuilt, self.output)
        with zipfile.ZipFile(self.output) as z:
            self.assertEqual(z.read("AndroidManifest.xml"), bytes([3, 0, 8, 0]) + b"new binary")
            self.assertEqual(z.read("classes.dex"), b"unchanged classes")
            self.assertEqual(z.read("resources.arsc"), b"unchanged resources")
            self.assertEqual(z.read("res/xml/paths.xml"), b"unchanged compiled xml")
            self.assertEqual(z.read("assets/protobuf.bin"), b"unchanged assets")
            self.assertEqual(z.read("lib/arm64-v8a/libcore.so"), b"unchanged native")
            self.assertNotIn("META-INF/OLD.SF", z.namelist())

    def test_text_manifest_is_rejected(self):
        with zipfile.ZipFile(self.rebuilt, "w") as z:
            z.writestr("AndroidManifest.xml", b"<?xml version='1.0'?><manifest/>")
        with self.assertRaisesRegex(ValueError, "compiled"):
            replace_manifest(self.original, self.rebuilt, self.output)

    def test_output_must_not_overwrite_input(self):
        with self.assertRaisesRegex(ValueError, "new file"):
            replace_manifest(self.original, self.rebuilt, self.original)


if __name__ == "__main__":
    unittest.main()
