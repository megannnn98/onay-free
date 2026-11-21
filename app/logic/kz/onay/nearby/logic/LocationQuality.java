package kz.onay.nearby.logic;

/** Decides whether a location fix is good enough to pick a stop from. */
public final class LocationQuality {
    public enum Verdict { OK, MISSING, STALE }

    private LocationQuality() {
    }

    /**
     * @param fixTimeMs time of the fix in epoch milliseconds, or null when unknown
     * @return MISSING for absent, invalid or (0, 0) coordinates; STALE when the fix is older than maxAgeMs
     *     or its time is unknown; a fix slightly in the future (clock difference) counts as fresh
     */
    public static Verdict classify(Double lat, Double lng, Long fixTimeMs, long nowMs, long maxAgeMs) {
        if (lat == null || lng == null || lat.isNaN() || lng.isNaN()
                || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0
                || (lat == 0.0 && lng == 0.0)) {
            return Verdict.MISSING;
        }
        if (fixTimeMs == null || nowMs - fixTimeMs > maxAgeMs) {
            return Verdict.STALE;
        }
        return Verdict.OK;
    }
}
