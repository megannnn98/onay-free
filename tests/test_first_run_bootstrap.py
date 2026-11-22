"""Contract checks for clean-install bootstrap source wiring.

These guard against a regression to clicking a hidden navigation child.
They are NOT replacements for an Android cold-install smoke test.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FRAGMENT = (ROOT / "app/ui/kz/onay/nearby/ui/NearbyStopFragment.java").read_text(encoding="utf-8")


class FirstRunBootstrapSourceTest(unittest.TestCase):
    def test_empty_stop_db_exposes_recovery_and_hides_empty_map_and_dock(self):
        start = FRAGMENT.index("private void onStopsLoaded(")
        end = FRAGMENT.index("private void locate()", start)
        body = FRAGMENT[start:end]
        self.assertIn("if (stops.isEmpty())", body)
        self.assertIn("mapView.setVisibility(View.INVISIBLE)", body)
        self.assertIn("dockView.setVisibility(View.GONE)", body)
        self.assertIn('"Открыть транспорт", this::openRoutesTabAgain', body)
        self.assertIn("openRoutesTabOnce();", body)
        self.assertIn("mapView.setVisibility(View.VISIBLE)", body)
        self.assertIn("dockView.setVisibility(View.VISIBLE)", body)
        self.assertIn("locate();", body)

    def test_transport_bootstrap_does_not_click_hidden_navigation(self):
        start = FRAGMENT.index("private void openRoutesTabOnce()")
        end = FRAGMENT.index("private void toggleExpandedChips()", start)
        body = FRAGMENT[start:end]
        self.assertNotIn("performClick()", body)
        self.assertIn('getMethod("x1", int.class)', body)
        self.assertIn("selectTab.invoke(requireActivity(), 0)", body)
        self.assertIn("routesTabOpenedForStops = true", body)
        self.assertIn("routesTabOpenedForStops = false", body)
        self.assertIn("recheckOnResume = true", body)
        self.assertIn("recheckOnResume = false", body)
        self.assertIn("catch (ReflectiveOperationException | RuntimeException e)", body)
        self.assertIn("private void openRoutesTabAgain()", body)

    def test_hidden_dock_does_not_reserve_map_space(self):
        self.assertIn("dockView.getVisibility() == View.GONE ? 0", FRAGMENT)


if __name__ == "__main__":
    unittest.main()
