package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NearestStopFinderTest {
    // One degree of latitude is about 111.2 km, so 0.001 degrees is about 111 m.
    private static final double USER_LAT = 43.2380;
    private static final double USER_LNG = 76.9450;

    private static StopPoint stop(long id, String name, Double lat, Double lng) {
        return new StopPoint(id, name, lat, lng);
    }

    @Test
    void haversineZeroForSamePoint() {
        assertEquals(0.0, Haversine.distanceMeters(USER_LAT, USER_LNG, USER_LAT, USER_LNG), 1e-9);
    }

    @Test
    void haversineOneThousandthDegreeOfLatitudeIsAbout111Meters() {
        double d = Haversine.distanceMeters(USER_LAT, USER_LNG, USER_LAT + 0.001, USER_LNG);
        assertEquals(111.2, d, 0.5);
    }

    @Test
    void haversineIsSymmetric() {
        double a = Haversine.distanceMeters(43.2, 76.9, 43.3, 77.0);
        double b = Haversine.distanceMeters(43.3, 77.0, 43.2, 76.9);
        assertEquals(a, b, 1e-6);
    }

    @Test
    void haversineHandlesAntimeridian() {
        double d = Haversine.distanceMeters(0.0, 179.9995, 0.0, -179.9995);
        assertEquals(111.2, d, 0.5);
    }

    @Test
    void picksClosestStop() {
        List<StopPoint> stops = Arrays.asList(
                stop(1, "Far", USER_LAT + 0.004, USER_LNG),
                stop(2, "Near", USER_LAT + 0.001, USER_LNG),
                stop(3, "Mid", USER_LAT, USER_LNG + 0.002));
        Optional<StopMatch> m = NearestStopFinder.findNearest(stops, USER_LAT, USER_LNG, 1000);
        assertTrue(m.isPresent());
        assertEquals(2L, m.get().getStop().getId());
        assertEquals(111.2, m.get().getDistanceMeters(), 0.5);
    }

    @Test
    void sameNameOnOppositeSidesAreDistinctStops() {
        // Both are called "Abay" but are different physical stops: the nearer one by distance wins,
        // the other is still returned as a separate candidate.
        StopPoint north = stop(10, "Abay", USER_LAT + 0.0002, USER_LNG);
        StopPoint south = stop(11, "Abay", USER_LAT - 0.0006, USER_LNG);
        List<StopMatch> r = NearestStopFinder.findNearby(Arrays.asList(south, north), USER_LAT, USER_LNG, 500, 5);
        assertEquals(2, r.size());
        assertEquals(10L, r.get(0).getStop().getId());
        assertEquals(11L, r.get(1).getStop().getId());
    }

    @Test
    void findNearbySortedByDistanceAndLimited() {
        List<StopPoint> stops = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            stops.add(stop(i, "S" + i, USER_LAT + 0.0005 * (7 - i), USER_LNG));
        }
        List<StopMatch> r = NearestStopFinder.findNearby(stops, USER_LAT, USER_LNG, 5000, 3);
        assertEquals(3, r.size());
        assertEquals(6L, r.get(0).getStop().getId());
        assertEquals(5L, r.get(1).getStop().getId());
        assertEquals(4L, r.get(2).getStop().getId());
    }

    @Test
    void stopOutsideRadiusIsExcluded() {
        List<StopPoint> stops = Collections.singletonList(stop(1, "Far", USER_LAT + 0.01, USER_LNG));
        assertFalse(NearestStopFinder.findNearest(stops, USER_LAT, USER_LNG, 500).isPresent());
    }

    @Test
    void stopExactlyOnRadiusIsIncluded() {
        StopPoint s = stop(1, "Edge", USER_LAT + 0.001, USER_LNG);
        double d = Haversine.distanceMeters(USER_LAT, USER_LNG, USER_LAT + 0.001, USER_LNG);
        assertTrue(NearestStopFinder.findNearest(Collections.singletonList(s), USER_LAT, USER_LNG, d).isPresent());
    }

    @Test
    void emptyStopListGivesEmpty() {
        assertFalse(NearestStopFinder.findNearest(Collections.<StopPoint>emptyList(), USER_LAT, USER_LNG, 1000).isPresent());
        assertTrue(NearestStopFinder.findNearby(Collections.<StopPoint>emptyList(), USER_LAT, USER_LNG, 1000, 5).isEmpty());
    }

    @Test
    void nullStopListGivesEmpty() {
        assertFalse(NearestStopFinder.findNearest(null, USER_LAT, USER_LNG, 1000).isPresent());
    }

    @Test
    void missingUserLocationGivesEmpty() {
        List<StopPoint> stops = Collections.singletonList(stop(1, "A", USER_LAT, USER_LNG));
        assertFalse(NearestStopFinder.findNearest(stops, null, USER_LNG, 1000).isPresent());
        assertFalse(NearestStopFinder.findNearest(stops, USER_LAT, null, 1000).isPresent());
        assertFalse(NearestStopFinder.findNearest(stops, Double.NaN, USER_LNG, 1000).isPresent());
    }

    @Test
    void stopsWithMissingOrInvalidCoordinatesAreSkipped() {
        List<StopPoint> stops = Arrays.asList(
                stop(1, "NoLat", null, USER_LNG),
                stop(2, "NoLng", USER_LAT, null),
                stop(3, "NaN", Double.NaN, USER_LNG),
                stop(4, "BadLat", 123.0, USER_LNG),
                stop(5, "Ok", USER_LAT + 0.001, USER_LNG));
        List<StopMatch> r = NearestStopFinder.findNearby(stops, USER_LAT, USER_LNG, 1000, 10);
        assertEquals(1, r.size());
        assertEquals(5L, r.get(0).getStop().getId());
    }

    @Test
    void outOfRangeLatitudeIsRejectedEvenWhenItWouldBeInsideTheRadius() {
        // Latitude 90.0005 does not exist. Near the pole its raw distance is only ~111 m,
        // so only explicit range validation keeps it out.
        StopPoint bogus = stop(1, "Bogus", 90.0005, 0.0);
        StopPoint real = stop(2, "Real", 89.9980, 0.0);
        List<StopMatch> r = NearestStopFinder.findNearby(Arrays.asList(bogus, real), 89.9995, 0.0, 1000, 5);
        assertEquals(1, r.size());
        assertEquals(2L, r.get(0).getStop().getId());
    }

    @Test
    void equalDistancesBreakTieByStopId() {
        StopPoint a = stop(7, "A", USER_LAT + 0.001, USER_LNG);
        StopPoint b = stop(3, "B", USER_LAT - 0.001, USER_LNG);
        List<StopMatch> r = NearestStopFinder.findNearby(Arrays.asList(a, b), USER_LAT, USER_LNG, 1000, 2);
        assertEquals(3L, r.get(0).getStop().getId());
        assertEquals(7L, r.get(1).getStop().getId());
    }

    @Test
    void nonPositiveLimitGivesEmpty() {
        List<StopPoint> stops = Collections.singletonList(stop(1, "A", USER_LAT, USER_LNG));
        assertTrue(NearestStopFinder.findNearby(stops, USER_LAT, USER_LNG, 1000, 0).isEmpty());
    }
}
