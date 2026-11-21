package kz.onay.nearby.logic;

/** The drawn geometry of one direction of a route. Plain arrays, no map types. */
public final class RouteLine {
    private final long routeId;
    private final int directionIndex;
    private final double[] lats;
    private final double[] lngs;

    public RouteLine(long routeId, int directionIndex, double[] lats, double[] lngs) {
        if (lats.length != lngs.length) {
            throw new IllegalArgumentException("lats and lngs must have the same length");
        }
        this.routeId = routeId;
        this.directionIndex = directionIndex;
        this.lats = lats.clone();
        this.lngs = lngs.clone();
    }

    public long getRouteId() {
        return routeId;
    }

    public int getDirectionIndex() {
        return directionIndex;
    }

    public int size() {
        return lats.length;
    }

    public double getLat(int i) {
        return lats[i];
    }

    public double getLng(int i) {
        return lngs[i];
    }

    public boolean isEmpty() {
        return lats.length == 0;
    }

    public boolean isDrawable() {
        return lats.length >= 2;
    }

    /** Bearing of the last segment with two distinct points, used to orient the direction arrow. */
    public double finalBearing() {
        for (int i = lats.length - 1; i > 0; i--) {
            if (lats[i] != lats[i - 1] || lngs[i] != lngs[i - 1]) {
                return Bearing.degrees(lats[i - 1], lngs[i - 1], lats[i], lngs[i]);
            }
        }
        return 0.0;
    }
}
