package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A route with its loaded directions. type ids follow the app's own mapping (RoutesResourceHelper). */
public final class RouteInfo {
    public static final int TYPE_BUS = 0;
    public static final int TYPE_TROLLEYBUS = 1;

    private final long id;
    private final String name;
    private final int typeId;
    private final List<RouteDirectionInfo> directions;

    public RouteInfo(long id, String name, int typeId, List<RouteDirectionInfo> directions) {
        this.id = id;
        this.name = name;
        this.typeId = typeId;
        this.directions = directions == null
                ? Collections.<RouteDirectionInfo>emptyList()
                : new ArrayList<>(directions);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTypeId() {
        return typeId;
    }

    public List<RouteDirectionInfo> getDirections() {
        return Collections.unmodifiableList(directions);
    }
}
