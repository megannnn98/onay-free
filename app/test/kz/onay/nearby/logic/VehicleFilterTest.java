package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleFilterTest {
    private static final long NOW = 1_700_000_000L;
    private static final long MAX_AGE = 180L;

    private static VehiclePosition v(long device, long route, int dir, long ts) {
        return new VehiclePosition(device, route, dir, 43.2, 76.9, 90.0, ts);
    }

    private static RouteAtStop at(long route, Integer... dirs) {
        return new RouteAtStop(route, "R" + route, RouteInfo.TYPE_BUS, Arrays.asList(dirs));
    }

    private static List<Long> devices(List<VehiclePosition> l) {
        List<Long> out = new ArrayList<>();
        for (VehiclePosition p : l) {
            out.add(p.getDeviceId());
        }
        return out;
    }

    @Test
    void keepsFreshVehiclesOfShownRoutes() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW - 5)), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L), devices(r));
    }

    @Test
    void dropsVehiclesOfOtherRoutes() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW), v(2, 99, 0, NOW)), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L), devices(r));
    }

    @Test
    void dropsOppositeDirectionWhenTheStopDirectionIsKnown() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW), v(2, 10, 1, NOW)), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L), devices(r));
    }

    @Test
    void keepsAllDirectionsWhenTheStopDirectionIsUnknown() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW), v(2, 10, 1, NOW)), Arrays.asList(at(10)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L, 2L), devices(r));
    }

    @Test
    void dropsStaleVehiclesButKeepsOnesExactlyAtTheLimit() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW - MAX_AGE), v(2, 10, 0, NOW - MAX_AGE - 1)),
                Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L), devices(r));
    }

    @Test
    void timestampSlightlyInTheFutureIsFresh() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(1, 10, 0, NOW + 20)), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(1, r.size());
    }

    @Test
    void dropsInvalidCoordinates() {
        List<VehiclePosition> all = Arrays.asList(
                new VehiclePosition(1, 10, 0, 0.0, 0.0, 0, NOW),
                new VehiclePosition(2, 10, 0, Double.NaN, 76.9, 0, NOW),
                new VehiclePosition(3, 10, 0, 95.0, 76.9, 0, NOW),
                new VehiclePosition(4, 10, 0, 43.2, 200.0, 0, NOW),
                new VehiclePosition(5, 10, 0, 43.2, 76.9, 0, NOW));
        assertEquals(Arrays.asList(5L), devices(VehicleFilter.select(all, Arrays.asList(at(10, 0)), NOW, MAX_AGE)));
    }

    @Test
    void keepsOnlyTheNewestReportPerDevice() {
        VehiclePosition older = new VehiclePosition(1, 10, 0, 43.1, 76.9, 0, NOW - 30);
        VehiclePosition newer = new VehiclePosition(1, 10, 0, 43.3, 76.9, 0, NOW - 5);
        List<VehiclePosition> r = VehicleFilter.select(Arrays.asList(older, newer), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(1, r.size());
        assertEquals(43.3, r.get(0).getLat(), 0.0);
        List<VehiclePosition> reversed = VehicleFilter.select(Arrays.asList(newer, older), Arrays.asList(at(10, 0)), NOW, MAX_AGE);
        assertEquals(43.3, reversed.get(0).getLat(), 0.0);
    }

    @Test
    void resultIsSortedByRouteThenDevice() {
        List<VehiclePosition> r = VehicleFilter.select(
                Arrays.asList(v(5, 20, 0, NOW), v(9, 10, 0, NOW), v(3, 20, 0, NOW), v(1, 10, 0, NOW)),
                Arrays.asList(at(10, 0), at(20, 0)), NOW, MAX_AGE);
        assertEquals(Arrays.asList(1L, 9L, 3L, 5L), devices(r));
    }

    @Test
    void emptyAndNullInputsGiveEmpty() {
        assertTrue(VehicleFilter.select(null, Arrays.asList(at(10, 0)), NOW, MAX_AGE).isEmpty());
        assertTrue(VehicleFilter.select(Arrays.asList(v(1, 10, 0, NOW)), null, NOW, MAX_AGE).isEmpty());
        assertTrue(VehicleFilter.select(Collections.<VehiclePosition>emptyList(), Arrays.asList(at(10, 0)), NOW, MAX_AGE).isEmpty());
    }

    @Test
    void latestTimestampIsTheNewestReportOrZero() {
        assertEquals(NOW, VehicleFilter.latestTimestamp(Arrays.asList(v(1, 10, 0, NOW), v(2, 10, 0, NOW - 7))));
        assertEquals(0L, VehicleFilter.latestTimestamp(Collections.<VehiclePosition>emptyList()));
        assertEquals(0L, VehicleFilter.latestTimestamp(null));
    }

    @Test
    void referenceIsTheSnapshotTimeWhateverThePhoneClockSays() {
        List<VehiclePosition> all = Arrays.asList(v(1, 10, 0, NOW), v(2, 10, 0, NOW - 400));
        assertEquals(NOW, VehicleFilter.referenceSec(all, NOW + 7200)); // phone clock 2 h ahead: hidden before
        assertEquals(NOW, VehicleFilter.referenceSec(all, NOW - 600)); // phone clock 10 min behind
    }

    @Test
    void referenceFallsBackToTheDeviceClockWithoutTimestamps() {
        assertEquals(NOW, VehicleFilter.referenceSec(Arrays.asList(v(1, 10, 0, 0)), NOW));
        assertEquals(NOW, VehicleFilter.referenceSec(Collections.<VehiclePosition>emptyList(), NOW));
        assertEquals(NOW, VehicleFilter.referenceSec(null, NOW));
    }

    @Test
    void oneCorruptFutureReportDoesNotHideTheFleet() {
        List<VehiclePosition> all = Arrays.asList(v(1, 10, 0, NOW - 5), v(2, 10, 0, NOW + 10L * 365 * 24 * 3600));
        long reference = VehicleFilter.referenceSec(all, NOW);
        assertEquals(NOW, reference);
        assertTrue(devices(VehicleFilter.select(all, Arrays.asList(at(10, 0)), reference, MAX_AGE)).contains(1L));
    }

    @Test
    void referenceKeepsASnapshotSlightlyAheadOfTheClock() {
        long ahead = NOW + 3600; // on the limit: still a reference
        assertEquals(ahead, VehicleFilter.referenceSec(Arrays.asList(v(1, 10, 0, ahead)), NOW));
        assertEquals(NOW, VehicleFilter.referenceSec(Arrays.asList(v(1, 10, 0, ahead + 1)), NOW));
    }

    @Test
    void phoneClockFarAheadWouldDropEverythingIfUsedAsReference() {
        List<VehiclePosition> all = Arrays.asList(v(1, 10, 0, NOW));
        assertTrue(VehicleFilter.select(all, Arrays.asList(at(10, 0)), NOW + 3600, MAX_AGE).isEmpty());
    }

    @Test
    void reportWithoutTimestampIsDropped() {
        assertTrue(VehicleFilter.select(Arrays.asList(v(1, 10, 0, 0)), Arrays.asList(at(10, 0)), NOW, MAX_AGE).isEmpty());
    }
}
