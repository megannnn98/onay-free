package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Chooses the routes shown for a stop. Never guesses a direction that the data does not show. */
public final class RoutesAtStopSelector {
    private RoutesAtStopSelector() {
    }

    /**
     * @param routeIdsAtStop ids the server lists for the stop (Stop.Routes)
     * @param routes         routes loaded so far; a listed id without an entry here is skipped
     */
    public static List<RouteAtStop> select(long stopId, Collection<Long> routeIdsAtStop, Collection<RouteInfo> routes) {
        if (routeIdsAtStop == null || routes == null) {
            return Collections.emptyList();
        }
        Map<Long, RouteInfo> byId = new LinkedHashMap<>();
        for (RouteInfo r : routes) {
            if (r != null && !byId.containsKey(r.getId())) {
                byId.put(r.getId(), r);
            }
        }
        Set<Long> wanted = new LinkedHashSet<>(routeIdsAtStop);
        List<RouteAtStop> out = new ArrayList<>();
        for (Long id : wanted) {
            RouteInfo r = byId.get(id);
            if (r == null || !isSupportedType(r.getTypeId())) {
                continue;
            }
            List<Integer> dirs = new ArrayList<>();
            for (RouteDirectionInfo d : r.getDirections()) {
                if (d.servesStop(stopId)) {
                    dirs.add(d.getIndex());
                }
            }
            Collections.sort(dirs);
            out.add(new RouteAtStop(r.getId(), r.getName(), r.getTypeId(), dirs));
        }
        Collections.sort(out, NAME_ORDER);
        return out;
    }

    private static boolean isSupportedType(int typeId) {
        return typeId == RouteInfo.TYPE_BUS || typeId == RouteInfo.TYPE_TROLLEYBUS;
    }

    private static final Comparator<RouteAtStop> NAME_ORDER = new Comparator<RouteAtStop>() {
        @Override
        public int compare(RouteAtStop a, RouteAtStop b) {
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            long ak = leadingNumber(an);
            long bk = leadingNumber(bn);
            if (ak != bk) {
                return Long.compare(ak, bk);
            }
            int c = an.compareTo(bn);
            return c != 0 ? c : Long.compare(a.getRouteId(), b.getRouteId());
        }
    };

    /** Numeric prefix of a route name; names with no number sort after numbered ones. */
    private static long leadingNumber(String name) {
        int i = 0;
        while (i < name.length() && Character.isDigit(name.charAt(i)) && i < 9) {
            i++;
        }
        return i == 0 ? Long.MAX_VALUE : Long.parseLong(name.substring(0, i));
    }
}
