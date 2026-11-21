package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoutesAtStopSelectorTest {
    private static final long STOP = 500L;
    private static final long OTHER_STOP = 600L;

    private static Set<Long> ids(Long... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    private static RouteInfo route(long id, String name, int type, RouteDirectionInfo... dirs) {
        return new RouteInfo(id, name, type, Arrays.asList(dirs));
    }

    private static List<String> names(List<RouteAtStop> r) {
        List<String> out = new ArrayList<>();
        for (RouteAtStop x : r) {
            out.add(x.getName());
        }
        return out;
    }

    @Test
    void returnsOnlyRoutesListedForTheStop() {
        List<RouteInfo> all = Arrays.asList(
                route(1, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(2, "79", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(OTHER_STOP))));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L), all);
        assertEquals(Arrays.asList("12"), names(r));
    }

    @Test
    void keepsBusAndTrolleybusDropsOtherTypes() {
        List<RouteInfo> all = Arrays.asList(
                route(1, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(2, "7", RouteInfo.TYPE_TROLLEYBUS, new RouteDirectionInfo(0, ids(STOP))),
                route(3, "M1", 2, new RouteDirectionInfo(0, ids(STOP))),
                route(4, "T1", 3, new RouteDirectionInfo(0, ids(STOP))),
                route(5, "55", 4, new RouteDirectionInfo(0, ids(STOP))));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L, 2L, 3L, 4L, 5L), all);
        assertEquals(Arrays.asList("7", "12"), names(r));
        assertEquals(RouteInfo.TYPE_TROLLEYBUS, r.get(0).getTypeId());
        assertEquals(RouteInfo.TYPE_BUS, r.get(1).getTypeId());
    }

    @Test
    void removesDuplicateRouteIds() {
        RouteInfo a = route(1, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP)));
        RouteInfo b = route(1, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP)));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L, 1L), Arrays.asList(a, b));
        assertEquals(1, r.size());
    }

    @Test
    void reportsOnlyDirectionsThatServeTheStop() {
        RouteInfo r12 = route(1, "12", RouteInfo.TYPE_BUS,
                new RouteDirectionInfo(0, ids(STOP, 700L)),
                new RouteDirectionInfo(1, ids(OTHER_STOP, 701L)));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L), Arrays.asList(r12));
        assertEquals(1, r.size());
        assertTrue(r.get(0).isDirectionKnown());
        assertEquals(Arrays.asList(0), r.get(0).getDirectionIndexes());
    }

    @Test
    void routeServingStopInBothDirectionsReportsBoth() {
        RouteInfo ring = route(1, "12", RouteInfo.TYPE_BUS,
                new RouteDirectionInfo(1, ids(STOP)),
                new RouteDirectionInfo(0, ids(STOP)));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L), Arrays.asList(ring));
        assertEquals(Arrays.asList(0, 1), r.get(0).getDirectionIndexes());
    }

    @Test
    void unknownDirectionIsNotGuessed() {
        // The server says the route serves the stop, but no loaded direction contains it.
        RouteInfo r12 = route(1, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(OTHER_STOP)));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L), Arrays.asList(r12));
        assertEquals(1, r.size());
        assertFalse(r.get(0).isDirectionKnown());
        assertTrue(r.get(0).getDirectionIndexes().isEmpty());
    }

    @Test
    void routeWithoutLoadedDirectionsIsKeptWithUnknownDirection() {
        RouteInfo r12 = new RouteInfo(1, "12", RouteInfo.TYPE_BUS, Collections.<RouteDirectionInfo>emptyList());
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L), Arrays.asList(r12));
        assertEquals(1, r.size());
        assertFalse(r.get(0).isDirectionKnown());
    }

    @Test
    void routeIdWithoutRouteInfoIsSkipped() {
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(99L), Collections.<RouteInfo>emptyList());
        assertTrue(r.isEmpty());
    }

    @Test
    void emptyOrNullInputsGiveEmptyList() {
        assertTrue(RoutesAtStopSelector.select(STOP, Collections.<Long>emptyList(), Collections.<RouteInfo>emptyList()).isEmpty());
        assertTrue(RoutesAtStopSelector.select(STOP, null, null).isEmpty());
        assertTrue(RoutesAtStopSelector.select(STOP, Arrays.asList(1L), null).isEmpty());
    }

    @Test
    void sortsByNumberThenText() {
        List<RouteInfo> all = Arrays.asList(
                route(1, "121", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(2, "12", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(3, "79", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(4, "12A", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(5, "38", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))),
                route(6, "A5", RouteInfo.TYPE_BUS, new RouteDirectionInfo(0, ids(STOP))));
        List<RouteAtStop> r = RoutesAtStopSelector.select(STOP, Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L), all);
        assertEquals(Arrays.asList("12", "12A", "38", "79", "121", "A5"), names(r));
    }
}
