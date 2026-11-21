package kz.onay.nearby.ui;

import java.util.Collection;
import java.util.List;
import kz.onay.nearby.logic.RouteInfo;
import kz.onay.nearby.logic.RouteLine;
import kz.onay.nearby.logic.StopPoint;

/** Everything the screen needs. Stage 2 uses {@link TestData}; later stages read the app's own data. */
public interface NearbyDataSource {
    /** User location as {lat, lng}, or null when unavailable. */
    double[] getUserLocation();

    List<StopPoint> getStops();

    /** Route ids the server lists for the stop. */
    Collection<Long> getRouteIdsAtStop(long stopId);

    List<RouteInfo> getRoutes();

    List<RouteLine> getLines(long routeId);

    /** Human label of a direction, or null when it is not known. */
    String getDirectionLabel(long routeId, int directionIndex);
}
