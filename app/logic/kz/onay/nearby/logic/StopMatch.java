package kz.onay.nearby.logic;

/** A stop together with its distance from the user. */
public final class StopMatch {
    private final StopPoint stop;
    private final double distanceMeters;

    public StopMatch(StopPoint stop, double distanceMeters) {
        this.stop = stop;
        this.distanceMeters = distanceMeters;
    }

    public StopPoint getStop() {
        return stop;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }
}
