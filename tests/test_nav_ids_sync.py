"""The ids hidden by scripts/patch_layout.py must be exactly the ids the "..." button shows again."""
import importlib.util
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.environ.get("NEARBY_FRAGMENT", os.path.join(ROOT, "app", "ui", "kz", "onay", "nearby", "ui", "NearbyStopFragment.java"))


def patched_ids():
    spec = importlib.util.spec_from_file_location("patch_layout", os.path.join(ROOT, "scripts", "patch_layout.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return {view_id for name, view_id in module.TARGETS if name == "activity_main.xml"}


def java_ids():
    with open(JAVA, encoding="utf-8") as f:
        text = f.read()
    block = re.search(r"ORIGINAL_NAVIGATION_IDS = \{(.*?)\};", text, re.S)
    assert block, "ORIGINAL_NAVIGATION_IDS not found"
    return set(re.findall(r'"([A-Za-z_]+)"', block.group(1)))


class NavigationIdsInSync(unittest.TestCase):
    def test_lists_are_identical(self):
        self.assertEqual(patched_ids(), java_ids())

    def test_lists_are_not_empty(self):
        self.assertGreaterEqual(len(patched_ids()), 8)


if __name__ == "__main__":
    unittest.main()
