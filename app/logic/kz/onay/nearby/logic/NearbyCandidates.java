package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Other stops that stand almost as close as the nearest one, typically the opposite side of the road. */
public final class NearbyCandidates {
    private NearbyCandidates() {
    }

    /**
     * @param matches     stops with their distance from the user, in any order
     * @param extraMeters how much farther than the nearest stop an alternative may be (inclusive)
     * @return up to {@code max} stops other than the nearest, nearest first
     */
    public static List<StopMatch> alternatives(List<StopMatch> matches, double extraMeters, int max) {
        if (matches == null || matches.size() < 2 || max <= 0) {
            return Collections.emptyList();
        }
        List<StopMatch> sorted = new ArrayList<>(matches);
        Collections.sort(sorted, new Comparator<StopMatch>() {
            @Override
            public int compare(StopMatch a, StopMatch b) {
                int c = Double.compare(a.getDistanceMeters(), b.getDistanceMeters());
                return c != 0 ? c : Long.compare(a.getStop().getId(), b.getStop().getId());
            }
        });
        StopMatch nearest = sorted.get(0);
        Set<Long> seen = new HashSet<>();
        seen.add(nearest.getStop().getId());
        List<StopMatch> out = new ArrayList<>();
        for (int i = 1; i < sorted.size() && out.size() < max; i++) {
            StopMatch m = sorted.get(i);
            if (m.getDistanceMeters() > nearest.getDistanceMeters() + extraMeters) {
                break;
            }
            if (seen.add(m.getStop().getId())) {
                out.add(m);
            }
        }
        return out;
    }
}
