"""Offline tests for replacing the extension DEX without modifying Android resources."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("replace_nearby_dex", ROOT / "scripts" / "replace_nearby_dex.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReplaceNearbyDexTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.src = self.root / "source.apk"
        self.dex = self.root / "compiled.dex"
        self.out = self.root / "unsigned.apk"
        self.original = b"dex\n035\x00" + module.NEARBY_CLASS + b"old implementation"
        self.replacement = b"dex\n035\x00" + module.NEARBY_CLASS + module.NEW_CORRIDOR_CLASS
        self.dex.write_bytes(self.replacement)
        with zipfile.ZipFile(self.src, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"manifest")
            archive.writestr("resources.arsc", b"resources")
            archive.writestr("classes.dex", b"dex\n035\x00" + b"original app")
            info = zipfile.ZipInfo("classes12.dex")
            info.compress_type = zipfile.ZIP_STORED
            archive.writestr(info, self.original)
            archive.writestr("lib/arm64-v8a/libnative.so", b"native")

    def test_only_extension_dex_is_replaced(self):
        self.assertEqual("classes12.dex", module.replace(self.src, self.dex, self.out))
        with zipfile.ZipFile(self.src) as source, zipfile.ZipFile(self.out) as output:
            self.assertEqual(source.namelist(), output.namelist())
            for name in source.namelist():
                if name == "classes12.dex":
                    self.assertEqual(output.read(name), self.replacement)
                else:
                    self.assertEqual(source.read(name), output.read(name))
                self.assertEqual(source.getinfo(name).compress_type, output.getinfo(name).compress_type)

    def test_rejects_wrong_replacement(self):
        self.dex.write_bytes(b"dex\n035\x00" + module.NEARBY_CLASS)
        with self.assertRaisesRegex(ValueError, "corridor logic"):
            module.replace(self.src, self.dex, self.out)

    def test_rejects_duplicate_extension_classes(self):
        with zipfile.ZipFile(self.src, "a") as archive:
            archive.writestr("classes13.dex", self.original)
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.replace(self.src, self.dex, self.out)

    def test_rejects_already_patched_source(self):
        with zipfile.ZipFile(self.src, "a") as archive:
            archive.writestr("classes13.dex", b"dex\n" + module.NEW_CORRIDOR_CLASS)
        with zipfile.ZipFile(self.src) as archive:
            # The source's actual extension dex must contain the corridor marker.
            pass
        self.src.unlink()
        with zipfile.ZipFile(self.src, "w") as archive:
            archive.writestr("classes.dex", b"dex\nmain")
            archive.writestr("classes12.dex", self.replacement)
        with self.assertRaisesRegex(ValueError, "already has corridor logic"):
            module.replace(self.src, self.dex, self.out)


if __name__ == "__main__":
    unittest.main()
