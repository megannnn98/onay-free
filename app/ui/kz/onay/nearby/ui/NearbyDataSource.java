package kz.onay.nearby.ui;

import java.util.Collection;
import java.util.List;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.StopPoint;
import kz.onay.nearby.logic.VehiclePosition;

/** Everything the screen needs. Stage 2 uses {@link TestData}; later stages read the app's own data. */
public interface NearbyDataSource {
    /** All stops with coordinates. May touch the database, so call it off the main thread. */
    List<StopPoint> getStops();

    /** Loads display names for the given stops. Call off the main thread; {@link #getStopName} is then cheap. */
    void prefetchNames(Collection<StopPoint> stops);

    /** Display name of a stop; never touches the database. */
    String getStopName(long stopId);

    /** False while routes are not connected to real data; the screen then says so instead of showing nothing. */
    boolean hasRoutes();

    /**
     * Loads the routes of one stop (ids, directions, geometry) so that the getters below can answer.
     * Call off the main thread. Throws when the stop itself cannot be loaded; routes that fail
     * individually are only counted, see {@link #getFailedRouteCount()}.
     */
    void loadRoutesForStop(long stopId) throws Exception;

    /** How many routes of the last {@link #loadRoutesForStop} call could not be loaded. */
    int getFailedRouteCount();

    /** A snapshot of the reported positions of vehicles on the given routes. Call off the main thread. */
    List<VehiclePosition> loadVehicles(Collection<Long> routeIds) throws Exception;

    /** Route ids the server lists for the stop. */
    Collection<Long> getRouteIdsAtStop(long stopId);

    List<RouteInfo> getRoutes();

    List<RouteLine> getLines(long routeId);

    /** Human label of a direction, or null when it is not known. */
    String getDirectionLabel(long routeId, int directionIndex);
}
