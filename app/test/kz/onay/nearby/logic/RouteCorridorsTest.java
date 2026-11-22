package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteCorridorsTest {
    private static final double LAT = 43.0;
    private static final double LNG = 76.9;
    private static final double R = 6371008.8;

    private static RouteLine line(long id, double... xyMeters) {
        double[] lats = new double[xyMeters.length / 2];
        double[] lngs = new double[lats.length];
        for (int i = 0; i < lats.length; i++) {
            lngs[i] = LNG + Math.toDegrees(xyMeters[2 * i] / (R * Math.cos(Math.toRadians(LAT))));
            lats[i] = LAT + Math.toDegrees(xyMeters[2 * i + 1] / R);
        }
        return new RouteLine(id, 0, lats, lngs);
    }

    private static long count(List<RouteCorridors.Part> paths, boolean shared) {
        return paths.stream().filter(p -> p.isShared() == shared).count();
    }

    @Test
    void emptyAndDegenerateGeometriesAreSafe() {
        assertTrue(RouteCorridors.overview(Collections.emptyList()).isEmpty());
        assertTrue(RouteCorridors.overview(Collections.singletonList(line(1, 0, 0))).isEmpty());
        assertTrue(RouteCorridors.overview(Collections.singletonList(line(1, 0, 0, 0, 0))).isEmpty());
    }

    @Test
    void loneRouteKeepsItsOwnColourEverywhere() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(
                Collections.singletonList(line(11, 0, 0, 200, 0)));
        assertEquals(0, count(paths, true));
        assertEquals(1, count(paths, false));
        assertEquals(11, paths.get(0).getRouteId());
        assertTrue(paths.get(0).size() >= 11);
    }

    @Test
    void sameRoadProducesOneSharedCorridorNotTwoOverlaidLines() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 200, 0),
                line(79, 0, 0, 200, 0)));
        assertEquals(1, count(paths, true));
        assertEquals(0, count(paths, false));
        assertEquals(65, paths.get(0).getRouteId());
    }

    @Test
    void reverseDirectionAndDifferentVertexSpacingAreMatched() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 100, 0, 200, 0),
                line(79, 200, 6, 140, 6, 75, 6, 0, 6)));
        assertEquals(1, count(paths, true));
        assertEquals(0, count(paths, false));
    }

    @Test
    void ownerIsStableWhenRouteOrderChanges() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(79, 0, 0, 200, 0), line(65, 200, 0, 0, 0)));
        assertEquals(1, count(paths, true));
        assertEquals(65, paths.get(0).getRouteId());
    }

    @Test
    void perpendicularCrossingIsNotShared() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, -100, 0, 100, 0),
                line(79, 0, -100, 0, 100)));
        assertEquals(0, count(paths, true));
        assertEquals(2, count(paths, false));
    }

    @Test
    void nearbyButDistinctParallelRoadsAreNotCombined() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 200, 0),
                line(79, 0, 20, 200, 20)));
        assertEquals(0, count(paths, true));
        assertEquals(2, count(paths, false));
    }

    @Test
    void shortOverlapIsNotMistakenForCorridor() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 45, 0), line(79, 0, 0, 45, 0)));
        assertEquals(0, count(paths, true));
        assertEquals(2, count(paths, false));
    }

    @Test
    void twoDirectionsOfOneRouteAreNotTwoRoutes() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 200, 0), line(65, 200, 0, 0, 0)));
        assertEquals(0, count(paths, true));
        assertEquals(2, count(paths, false));
    }

    @Test
    void branchesReturnToIndividualColours() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(65, 0, 0, 200, 0),
                line(79, 0, 0, 100, 0, 100, 150)));
        assertTrue(count(paths, true) >= 1);
        assertTrue(count(paths, false) >= 2);
        assertTrue(paths.stream().anyMatch(p -> !p.isShared() && p.getRouteId() == 65));
        assertTrue(paths.stream().anyMatch(p -> !p.isShared() && p.getRouteId() == 79));
    }

    @Test
    void sharedPartsAlwaysHaveAtLeastTwoPoints() {
        List<RouteCorridors.Part> paths = RouteCorridors.overview(Arrays.asList(
                line(1, 0, 0, 150, 0), line(2, 0, 0, 80, 0, 80, 100)));
        for (RouteCorridors.Part p : paths) {
            assertTrue(p.size() >= 2);
            assertTrue(Double.isFinite(p.getLat(0)));
            assertTrue(Double.isFinite(p.getLng(p.size() - 1)));
        }
    }

    @Test
    void selectedRouteStateDoesNotDependOnCorridorStyle() {
        RouteHighlightState state = new RouteHighlightState();
        state.toggle(65);
        assertTrue(state.isSelected(65));
        assertFalse(state.isSelected(79));
    }
}
