"""The cold-start patch of MainActivity.onCreate: Life (tab 2) instead of Cards (tab 1)."""
import importlib.util
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location("patch_smali", os.environ.get("PATCH_SMALI", os.path.join(ROOT, "scripts", "patch_smali.py")))
patch_smali = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(patch_smali)

ON_CREATE = """.class public Lkz/onay/ui/main/MainActivity;
.method public onCreate(Landroid/os/Bundle;)V
    const/4 v2, 0x1

    .line 212
    invoke-virtual {p0}, Lkz/onay/ui/main/MainActivity;->A1()V

    .line 213
    .line 214
    if-nez p1, :cond_1

    .line 216
    .line 217
    invoke-virtual {p0, v2}, Lkz/onay/ui/main/MainActivity;->x1(I)V

    .line 218
    :cond_1
    invoke-virtual {p0}, Lkz/onay/ui/main/MainActivity;->showAccessCodeSetDialog()V
    return-void
.end method

.method public x1(I)V
    invoke-virtual {p0, v1}, Lkz/onay/ui/main/MainActivity;->x1(I)V
    return-void
.end method
"""


class StartTabPatch(unittest.TestCase):
    def test_cold_start_selects_tab_two_and_keeps_register_value(self):
        out = patch_smali.patch_start_tab(ON_CREATE)
        i = out.index("if-nez p1, :cond_1")
        branch = out[i:out.index("\n    :cond_1")]
        self.assertLess(branch.index("const/4 v2, 0x2"), branch.index("->x1(I)V"))
        self.assertGreater(branch.index("const/4 v2, 0x1"), branch.index("->x1(I)V"))

    def test_only_the_cold_start_branch_changes(self):
        out = patch_smali.patch_start_tab(ON_CREATE)
        self.assertEqual(out[out.index(".method public x1"):], ON_CREATE[ON_CREATE.index(".method public x1"):])
        self.assertIn("if-nez p1, :cond_1", out)  # saved state still restores the saved tab

    def test_changed_app_stops_the_build(self):
        with self.assertRaises(SystemExit):
            patch_smali.patch_start_tab(ON_CREATE.replace("if-nez p1", "if-eqz p1"))
        with self.assertRaises(SystemExit):
            patch_smali.patch_start_tab(ON_CREATE.replace(".method public onCreate", ".method public onStart"))

    def test_real_decoded_app_matches_once(self):
        path = os.path.join(ROOT, "work", "apktool_full", "smali_classes11", "kz", "onay", "ui", "main", "MainActivity.smali")
        if not os.path.exists(path):
            self.skipTest("decoded app not present")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        out = patch_smali.patch_start_tab(text)
        self.assertEqual(out.count("const/4 v2, 0x2"), text.count("const/4 v2, 0x2") + 1)


if __name__ == "__main__":
    unittest.main()
