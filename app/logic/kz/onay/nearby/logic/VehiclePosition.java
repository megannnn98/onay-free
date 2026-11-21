package kz.onay.nearby.logic;

/** One report of a vehicle: where it is, which route and direction, and when the report was made. */
public final class VehiclePosition {
    private final long deviceId;
    private final long routeId;
    private final int directionIndex;
    private final double lat;
    private final double lng;
    private final double bearing;
    private final long timestampSec;

    public VehiclePosition(long deviceId, long routeId, int directionIndex, double lat, double lng,
            double bearing, long timestampSec) {
        this.deviceId = deviceId;
        this.routeId = routeId;
        this.directionIndex = directionIndex;
        this.lat = lat;
        this.lng = lng;
        this.bearing = bearing;
        this.timestampSec = timestampSec;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public long getRouteId() {
        return routeId;
    }

    public int getDirectionIndex() {
        return directionIndex;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getBearing() {
        return bearing;
    }

    public long getTimestampSec() {
        return timestampSec;
    }
}
