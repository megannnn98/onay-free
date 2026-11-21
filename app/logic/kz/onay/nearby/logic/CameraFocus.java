package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Which points the camera must show when a route is selected: the stop's neighbourhood, the user and the
 * vehicles of that route that are near the stop. Route lines are deliberately not part of it, because a line
 * runs through the whole city and would zoom the map out.
 */
public final class CameraFocus {
    private CameraFocus() {
    }

    /** Points as {lat, lng}. Vehicles farther than {@code vehicleRadiusMeters} from the stop are left out. */
    public static List<double[]> points(double stopLat, double stopLng, double[] user,
            List<VehiclePosition> routeVehicles, double vehicleRadiusMeters) {
        List<double[]> out = new ArrayList<>();
        out.add(new double[] {stopLat, stopLng});
        if (user != null) {
            out.add(new double[] {user[0], user[1]});
        }
        if (routeVehicles != null) {
            for (VehiclePosition v : routeVehicles) {
                if (Haversine.distanceMeters(stopLat, stopLng, v.getLat(), v.getLng()) <= vehicleRadiusMeters) {
                    out.add(new double[] {v.getLat(), v.getLng()});
                }
            }
        }
        return out;
    }
}
