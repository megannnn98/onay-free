package kz.onay.nearby.logic;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** One direction of a route: its index and the ids of the stops it serves. */
public final class RouteDirectionInfo {
    private final int index;
    private final Set<Long> stopIds;

    public RouteDirectionInfo(int index, Set<Long> stopIds) {
        this.index = index;
        this.stopIds = stopIds == null ? Collections.<Long>emptySet() : new HashSet<>(stopIds);
    }

    public int getIndex() {
        return index;
    }

    public boolean servesStop(long stopId) {
        return stopIds.contains(stopId);
    }
}
