"""Source integration checks for ONAY Free corridor rendering.

The JVM suite covers geometry, while these checks ensure the map uses that
result and does not regress to stacked widths.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FRAGMENT = (ROOT / "app/ui/kz/onay/nearby/ui/NearbyStopFragment.java").read_text(encoding="utf-8")


class CorridorWiringTest(unittest.TestCase):
    def test_fixed_width_and_reserved_common_colour(self):
        self.assertIn("SHARED_CORRIDOR_COLOR = 0xFF7C3AED", FRAGMENT)
        self.assertIn("ROUTE_LINE_DP = 4", FRAGMENT)
        self.assertIn("return dp(ROUTE_LINE_DP);", FRAGMENT)
        self.assertNotIn("routes.size()) * dp(3)", FRAGMENT)
        self.assertNotIn("lineWidthOf(", FRAGMENT)

    def test_overview_is_geometry_based_and_separate_from_full_paths(self):
        self.assertIn("RouteCorridors.overview(visibleGeometry)", FRAGMENT)
        self.assertIn("part.isShared() ? SHARED_CORRIDOR_COLOR : colorOf(part.getRouteId())", FRAGMENT)
        self.assertIn("overviewPolylines.add(map.addPolyline(po))", FRAGMENT)
        self.assertIn(".visible(highlight.hasSelection())", FRAGMENT)
        self.assertIn(".visible(!highlight.hasSelection())", FRAGMENT)

    def test_selection_switches_layers_without_rebuilding_geometry(self):
        begin = FRAGMENT.index("private void refreshHighlight(boolean moveCamera)")
        end = FRAGMENT.index("private void styleChip(", begin)
        body = FRAGMENT[begin:end]
        self.assertIn("p.setVisible(!selected)", body)
        self.assertIn("p.setVisible(selected)", body)
        self.assertIn("p.setWidth(routeLineWidth())", body)
        self.assertIn("p.setZIndex(sel ? 3f : lineZOf(id))", body)

    def test_map_reset_clears_both_layers(self):
        for name in ("private void drawStop()", "private void forgetMapObjects()"):
            begin = FRAGMENT.index(name)
            body = FRAGMENT[begin : begin + 400]
            self.assertIn("polylines.clear();", body)
            self.assertIn("overviewPolylines.clear();", body)


if __name__ == "__main__":
    unittest.main()
