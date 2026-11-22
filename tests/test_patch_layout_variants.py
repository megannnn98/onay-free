"""patch_layout.py: the map variant hides the bottom bar, the kz.onay variant keeps it (and still hides the banners)."""
import importlib.util
import os
import re
import shutil
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = importlib.util.spec_from_file_location("patch_layout", os.path.join(ROOT, "scripts", "patch_layout.py"))
patch_layout = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(patch_layout)


def make_layouts(root):
    by_file = {}
    for name, view_id in patch_layout.TARGETS:
        by_file.setdefault(name, []).append(view_id)
    directory = os.path.join(root, "res", "layout")
    os.makedirs(directory)
    for name, ids in by_file.items():
        body = "".join('<View android:id="@id/%s" android:layout_width="1dp"/>' % i for i in ids)
        with open(os.path.join(directory, name), "w", encoding="utf-8") as f:
            f.write("<FrameLayout xmlns:android=\"x\">%s</FrameLayout>" % body)


def hidden(root, name):
    with open(os.path.join(root, "res", "layout", name), encoding="utf-8") as f:
        text = f.read()
    return {i for i in re.findall(r'android:id="@id/(\w+)"[^>]*android:visibility="gone"', text)}


class LayoutVariants(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="layout-test-")
        self.addCleanup(shutil.rmtree, self.dir, True)
        make_layouts(self.dir)
        self.nav = {v for n, v in patch_layout.TARGETS if n == "activity_main.xml"}
        self.banners = {(n, v) for n, v in patch_layout.TARGETS if n != "activity_main.xml"}

    def test_map_variant_hides_navigation_and_banners(self):
        patch_layout.main(self.dir)
        self.assertEqual(hidden(self.dir, "activity_main.xml"), self.nav)
        for name, view_id in self.banners:
            self.assertIn(view_id, hidden(self.dir, name))

    def test_full_variant_keeps_navigation_and_hides_banners(self):
        patch_layout.main(self.dir, keep_navigation=True)
        self.assertEqual(hidden(self.dir, "activity_main.xml"), set())
        for name, view_id in self.banners:
            self.assertIn(view_id, hidden(self.dir, name))
        self.assertTrue(self.nav, "the map variant has navigation ids to hide")


if __name__ == "__main__":
    unittest.main()
