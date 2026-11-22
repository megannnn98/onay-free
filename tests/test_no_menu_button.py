"""The stop card has no "..." button: the original ONAY! bar (cards, payment, menu) is not reachable from the screen."""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app", "ui", "kz", "onay", "nearby", "ui", "NearbyStopFragment.java")


def source():
    with open(JAVA, encoding="utf-8") as f:
        return f.read()


def body(text, signature):
    start = text.index(signature)
    return text[start:text.index("\n    }\n", start)]


class NoMenuButton(unittest.TestCase):
    def test_no_dots_button_and_no_toggle(self):
        text = source()
        self.assertNotIn("⋯", text)
        self.assertNotIn("toggleOriginalNavigation", text)
        self.assertNotIn("moreButton", text)

    def test_the_bar_is_shown_only_by_first_run_paths(self):
        text = source()
        shown = [m.start() for m in re.finditer(r"originalNavigationShown = true;", text)]
        self.assertEqual(len(shown), 2, "openRoutesTabOnce and its error fallback only")
        first_run = text[text.index("private void openRoutesTabOnce()"):text.index("private void openRoutesTabAgain()")]
        self.assertEqual(first_run.count("originalNavigationShown = true;"), 2)

    def test_bar_is_hidden_again_when_stops_are_loaded(self):
        loaded = body(source(), "private void onStopsLoaded(")
        tail = loaded[loaded.index("mapView.setVisibility(View.VISIBLE);"):]
        self.assertIn("originalNavigationShown = false;", tail)
        self.assertLess(tail.index("originalNavigationShown = false;"), tail.index("applyOriginalNavigation();"))


if __name__ == "__main__":
    unittest.main()
