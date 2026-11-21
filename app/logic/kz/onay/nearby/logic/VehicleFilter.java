package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chooses which reported vehicles are worth drawing for the routes shown at a stop. */
public final class VehicleFilter {
    private VehicleFilter() {
    }

    /**
     * Keeps vehicles of the shown routes, in a direction that serves the stop (all directions when the
     * stop's direction is unknown), with valid coordinates and a report no older than {@code maxAgeSec}.
     * A report slightly in the future (clock difference) counts as fresh. Only the newest report per device
     * is kept. Sorted by route id, then device id.
     */
    public static List<VehiclePosition> select(List<VehiclePosition> all, List<RouteAtStop> shown,
            long nowSec, long maxAgeSec) {
        if (all == null || shown == null) {
            return Collections.emptyList();
        }
        Map<Long, RouteAtStop> byRoute = new HashMap<>();
        for (RouteAtStop r : shown) {
            byRoute.put(r.getRouteId(), r);
        }
        Map<Long, VehiclePosition> newest = new HashMap<>();
        for (VehiclePosition p : all) {
            RouteAtStop r = byRoute.get(p.getRouteId());
            if (r == null) {
                continue;
            }
            if (r.isDirectionKnown() && !r.getDirectionIndexes().contains(p.getDirectionIndex())) {
                continue;
            }
            if (!validCoordinates(p.getLat(), p.getLng())) {
                continue;
            }
            if (nowSec - p.getTimestampSec() > maxAgeSec) {
                continue;
            }
            VehiclePosition seen = newest.get(p.getDeviceId());
            if (seen == null || p.getTimestampSec() > seen.getTimestampSec()) {
                newest.put(p.getDeviceId(), p);
            }
        }
        List<VehiclePosition> out = new ArrayList<>(newest.values());
        Collections.sort(out, new Comparator<VehiclePosition>() {
            @Override
            public int compare(VehiclePosition a, VehiclePosition b) {
                int c = Long.compare(a.getRouteId(), b.getRouteId());
                return c != 0 ? c : Long.compare(a.getDeviceId(), b.getDeviceId());
            }
        });
        return out;
    }

    private static boolean validCoordinates(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0
                && !(lat == 0.0 && lng == 0.0);
    }
}
