"""The access code (quick-entry PIN of ONAY!) is switched off by two smali patches."""
import importlib.util
import os
import shutil
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location("patch_smali", os.environ.get("PATCH_SMALI", os.path.join(ROOT, "scripts", "patch_smali.py")))
patch_smali = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(patch_smali)

SET_DIALOG = """.method public showAccessCodeSetDialog()V
    .locals 4

    .line 1
    new-instance v0, Landroid/os/Handler;
    return-void
.end method
"""

ASK = """.method public final Q()V
    .locals 3

    .line 1
    iget-boolean v0, p0, Lkz/onay/ui/base/BaseSecondaryActivity;->forbidAccessCodeDialog:Z
    return-void
.end method
"""

OTHER = """.method public final Q2()V
    .locals 3
    return-void
.end method
"""


def make_tree(root, main_text=SET_DIALOG, base_text=ASK):
    for relative, text in (("smali_classes11/kz/onay/ui/main/MainActivity.smali", main_text),
                           ("smali_classes11/kz/onay/ui/base/BaseSecondaryActivity.smali", base_text)):
        path = os.path.join(root, *relative.split("/"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)


def read(root, relative):
    with open(os.path.join(root, *relative.split("/")), encoding="utf-8") as f:
        return f.read()


class AccessCodePatch(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="smali-test-")
        self.addCleanup(shutil.rmtree, self.dir, True)

    def test_both_methods_return_immediately(self):
        make_tree(self.dir)
        patch_smali.patch_access_code(self.dir)
        main = read(self.dir, "smali_classes11/kz/onay/ui/main/MainActivity.smali")
        base = read(self.dir, "smali_classes11/kz/onay/ui/base/BaseSecondaryActivity.smali")
        self.assertIn(".method public showAccessCodeSetDialog()V\n    .locals 4\n    return-void\n", main)
        self.assertIn(".method public final Q()V\n    .locals 3\n    return-void\n", base)
        self.assertIn("new-instance v0, Landroid/os/Handler;", main, "the rest of the method is kept")

    def test_only_the_named_method_changes(self):
        make_tree(self.dir, base_text=OTHER + ASK)
        patch_smali.patch_access_code(self.dir)
        base = read(self.dir, "smali_classes11/kz/onay/ui/base/BaseSecondaryActivity.smali")
        self.assertIn(".method public final Q2()V\n    .locals 3\n    return-void\n.end method", base)
        self.assertEqual(base.count("return-void\n\n    .line 1"), 1)

    def test_missing_method_stops_the_build(self):
        make_tree(self.dir, base_text=OTHER)
        with self.assertRaises(SystemExit):
            patch_smali.patch_access_code(self.dir)

    def test_duplicate_method_stops_the_build(self):
        make_tree(self.dir, main_text=SET_DIALOG + SET_DIALOG)
        with self.assertRaises(SystemExit):
            patch_smali.patch_access_code(self.dir)

    def test_changed_locals_line_stops_the_build(self):
        make_tree(self.dir, main_text=SET_DIALOG.replace(".locals 4", ".registers 4"))
        with self.assertRaises(SystemExit):
            patch_smali.patch_access_code(self.dir)

    def test_second_run_is_refused(self):
        make_tree(self.dir)
        patch_smali.patch_access_code(self.dir)
        with self.assertRaises(SystemExit):
            patch_smali.patch_access_code(self.dir)

    def test_missing_file_stops_the_build(self):
        make_tree(self.dir)
        os.remove(os.path.join(self.dir, "smali_classes11", "kz", "onay", "ui", "base", "BaseSecondaryActivity.smali"))
        with self.assertRaises(SystemExit):
            patch_smali.patch_access_code(self.dir)


@unittest.skipUnless(os.path.isdir(os.path.join(ROOT, "work", "apktool_full", "smali_classes11")), "no decoded APK in work/")
class OnRealDecodedApp(unittest.TestCase):
    """The patches apply to the real ONAY! 3.6.5 smali (a copy of the two files)."""

    def test_applies_to_the_real_files(self):
        decoded = tempfile.mkdtemp(prefix="smali-real-")
        self.addCleanup(shutil.rmtree, decoded, True)
        for relative, _ in patch_smali.ACCESS_CODE_PATCHES:
            source = os.path.join(ROOT, "work", "apktool_full", "smali_classes11", *relative.split("/"))
            target = os.path.join(decoded, "smali_classes11", *relative.split("/"))
            os.makedirs(os.path.dirname(target))
            shutil.copyfile(source, target)
        patch_smali.patch_access_code(decoded)


if __name__ == "__main__":
    unittest.main()
