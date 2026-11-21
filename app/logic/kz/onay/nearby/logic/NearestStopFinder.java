package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds stops near the user. Stops are distinguished by id, so two stops with one name stay separate. */
public final class NearestStopFinder {
    private NearestStopFinder() {
    }

    public static Optional<StopMatch> findNearest(List<StopPoint> stops, Double lat, Double lng, double maxRadiusMeters) {
        List<StopMatch> r = findNearby(stops, lat, lng, maxRadiusMeters, 1);
        return r.isEmpty() ? Optional.<StopMatch>empty() : Optional.of(r.get(0));
    }

    /** Stops within the radius (inclusive), nearest first, ties broken by id, at most {@code limit}. */
    public static List<StopMatch> findNearby(List<StopPoint> stops, Double lat, Double lng, double maxRadiusMeters, int limit) {
        if (stops == null || limit <= 0 || !isValid(lat, lng)) {
            return Collections.emptyList();
        }
        List<StopMatch> matches = new ArrayList<>();
        for (StopPoint s : stops) {
            if (s == null || !isValid(s.getLat(), s.getLng())) {
                continue;
            }
            double d = Haversine.distanceMeters(lat, lng, s.getLat(), s.getLng());
            if (d <= maxRadiusMeters) {
                matches.add(new StopMatch(s, d));
            }
        }
        Collections.sort(matches, new Comparator<StopMatch>() {
            @Override
            public int compare(StopMatch a, StopMatch b) {
                int c = Double.compare(a.getDistanceMeters(), b.getDistanceMeters());
                return c != 0 ? c : Long.compare(a.getStop().getId(), b.getStop().getId());
            }
        });
        return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
    }

    private static boolean isValid(Double lat, Double lng) {
        return lat != null && lng != null
                && !lat.isNaN() && !lng.isNaN()
                && lat >= -90.0 && lat <= 90.0
                && lng >= -180.0 && lng <= 180.0;
    }
}
