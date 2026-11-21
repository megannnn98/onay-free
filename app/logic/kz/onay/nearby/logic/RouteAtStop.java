package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A route as shown for one stop. An empty direction list means the direction is unknown. */
public final class RouteAtStop {
    private final long routeId;
    private final String name;
    private final int typeId;
    private final List<Integer> directionIndexes;

    public RouteAtStop(long routeId, String name, int typeId, List<Integer> directionIndexes) {
        this.routeId = routeId;
        this.name = name;
        this.typeId = typeId;
        this.directionIndexes = Collections.unmodifiableList(new ArrayList<>(directionIndexes));
    }

    public long getRouteId() {
        return routeId;
    }

    public String getName() {
        return name;
    }

    public int getTypeId() {
        return typeId;
    }

    public List<Integer> getDirectionIndexes() {
        return directionIndexes;
    }

    public boolean isDirectionKnown() {
        return !directionIndexes.isEmpty();
    }
}
