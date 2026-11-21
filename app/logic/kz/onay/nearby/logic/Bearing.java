package kz.onay.nearby.logic;

/** Initial compass bearing between two points, in degrees from north clockwise, [0, 360). */
public final class Bearing {
    private Bearing() {
    }

    public static double degrees(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        if (x == 0.0 && y == 0.0) {
            return 0.0;
        }
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }
}
