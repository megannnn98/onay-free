package kz.onay.nearby.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kz.onay.nearby.logic.RouteDirectionInfo;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.StopPoint;

/**
 * Invented data for the stage 2 prototype. Coordinates only resemble central Almaty.
 * Covers: two stops with one name on opposite sides, a route with two directions,
 * a trolleybus, a route whose direction is unknown, a route that does not serve the stop,
 * and a tram that must be filtered out.
 */
final class TestData implements NearbyDataSource {
    private static final double[] USER = {43.23842, 76.94520};
    // Next to the stop on the opposite side of the road (stop id 2).
    private static final double[] USER_SOUTH = {43.23822, 76.94566};

    private final List<StopPoint> stops = new ArrayList<>();
    private final Map<Long, List<Long>> routeIdsByStop = new HashMap<>();
    private final List<RouteInfo> routes = new ArrayList<>();
    private final Map<Long, List<RouteLine>> lines = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final boolean hasLocation;
    private final boolean south;

    /** state: null, "south" (user on the opposite side) or "nogps". */
    TestData(String state) {
        this.hasLocation = !"nogps".equals(state);
        this.south = "south".equals(state);
        stops.add(new StopPoint(1, "Абая / Назарбаева", 43.23850, 76.94500));
        stops.add(new StopPoint(2, "Абая / Назарбаева", 43.23825, 76.94560));
        stops.add(new StopPoint(3, "Достык / Сатпаева", 43.2352, 76.9561));
        stops.add(new StopPoint(4, "Аль-Фараби / Достык", 43.2301, 76.9570));
        routeIdsByStop.put(1L, Arrays.asList(12L, 79L, 121L, 65L, 7L));
        routeIdsByStop.put(2L, Arrays.asList(12L, 79L, 38L));
        routeIdsByStop.put(3L, Arrays.asList(38L, 79L));
        routeIdsByStop.put(4L, Arrays.asList(38L));

        routes.add(new RouteInfo(12, "12", RouteInfo.TYPE_BUS, Arrays.asList(
                new RouteDirectionInfo(0, set(1, 3, 4)), new RouteDirectionInfo(1, set(2)))));
        routes.add(new RouteInfo(79, "79", RouteInfo.TYPE_BUS, Arrays.asList(
                new RouteDirectionInfo(0, set(1, 3)), new RouteDirectionInfo(1, set(2, 4)))));
        routes.add(new RouteInfo(121, "121", RouteInfo.TYPE_TROLLEYBUS, Arrays.asList(
                new RouteDirectionInfo(0, set(1)), new RouteDirectionInfo(1, set()))));
        routes.add(new RouteInfo(65, "65", RouteInfo.TYPE_BUS, Collections.<RouteDirectionInfo>emptyList()));
        routes.add(new RouteInfo(38, "38", RouteInfo.TYPE_BUS, Arrays.asList(
                new RouteDirectionInfo(0, set(3, 4)), new RouteDirectionInfo(1, set(2)))));
        routes.add(new RouteInfo(7, "7", 3, Arrays.asList(new RouteDirectionInfo(0, set(1)))));

        addLine(12, 0, 43.2400, 76.9400, 43.2385, 76.9450, 43.2360, 76.9520, 43.2352, 76.9561, 43.2301, 76.9570);
        addLine(12, 1, 43.2298, 76.9575, 43.2349, 76.9566, 43.2357, 76.9525, 43.23825, 76.9456, 43.2397, 76.9405);
        addLine(79, 0, 43.2390, 76.9410, 43.2385, 76.9450, 43.2378, 76.9500, 43.2352, 76.9561);
        addLine(79, 1, 43.2349, 76.9565, 43.2375, 76.9505, 43.23825, 76.9456, 43.2386, 76.9412, 43.2300, 76.9572);
        addLine(121, 0, 43.2415, 76.9440, 43.2385, 76.9450, 43.2370, 76.9480, 43.2345, 76.9500);
        addLine(38, 0, 43.2352, 76.9561, 43.2301, 76.9570);
        addLine(38, 1, 43.2310, 76.9575, 43.2382, 76.9456);
        addLine(7, 0, 43.2420, 76.9450, 43.2385, 76.9450);

        labels.put("12/0", "в сторону Аль-Фараби / Достык");
        labels.put("12/1", "в сторону Абая / Сейфуллина");
        labels.put("79/0", "в сторону Достык / Сатпаева");
        labels.put("79/1", "в сторону Алматы-1");
        labels.put("121/0", "в сторону Парк Горького");
    }

    private static Set<Long> set(long... ids) {
        Set<Long> s = new HashSet<>();
        for (long id : ids) {
            s.add(id);
        }
        return s;
    }

    private void addLine(long routeId, int dir, double... latLng) {
        int n = latLng.length / 2;
        double[] la = new double[n];
        double[] ln = new double[n];
        for (int i = 0; i < n; i++) {
            la[i] = latLng[2 * i];
            ln[i] = latLng[2 * i + 1];
        }
        List<RouteLine> l = lines.get(routeId);
        if (l == null) {
            l = new ArrayList<>();
            lines.put(routeId, l);
        }
        l.add(new RouteLine(routeId, dir, la, ln));
    }

    /** Test-only: the pretend user position. The real screen gets it from GPS. */
    double[] getUserLocation() {
        if (!hasLocation) {
            return null;
        }
        return (south ? USER_SOUTH : USER).clone();
    }

    @Override
    public List<StopPoint> getStops() {
        return stops;
    }

    @Override
    public void prefetchNames(Collection<StopPoint> stops) {
    }

    @Override
    public String getStopName(long stopId) {
        for (StopPoint s : stops) {
            if (s.getId() == stopId) {
                return s.getName();
            }
        }
        return "Остановка " + stopId;
    }

    @Override
    public boolean hasRoutes() {
        return true;
    }

    @Override
    public Collection<Long> getRouteIdsAtStop(long stopId) {
        List<Long> r = routeIdsByStop.get(stopId);
        return r == null ? Collections.<Long>emptyList() : r;
    }

    @Override
    public List<RouteInfo> getRoutes() {
        return routes;
    }

    @Override
    public List<RouteLine> getLines(long routeId) {
        List<RouteLine> l = lines.get(routeId);
        return l == null ? Collections.<RouteLine>emptyList() : l;
    }

    @Override
    public String getDirectionLabel(long routeId, int directionIndex) {
        return labels.get(routeId + "/" + directionIndex);
    }
}
