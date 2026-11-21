package kz.onay.nearby.logic;

/**
 * Decides which vehicle markers sit on top of each other so the UI can shift their icons apart.
 * The real positions are never changed: only an index (0, 1, 2, ...) per marker is returned and the UI
 * uses it to move the icon's anchor, so every label stays readable.
 */
public final class MarkerStacking {
    private MarkerStacking() {
    }

    /**
     * For each point, the number of earlier points closer than {@code radiusMeters}. A lone point gets 0.
     *
     * @throws IllegalArgumentException when the arrays differ in length
     */
    public static int[] stackIndexes(double[] lats, double[] lngs, double radiusMeters) {
        if (lats.length != lngs.length) {
            throw new IllegalArgumentException("lats and lngs differ in length");
        }
        int[] out = new int[lats.length];
        for (int i = 0; i < lats.length; i++) {
            int n = 0;
            for (int j = 0; j < i; j++) {
                if (Haversine.distanceMeters(lats[i], lngs[i], lats[j], lngs[j]) < radiusMeters) {
                    n++;
                }
            }
            out[i] = n;
        }
        return out;
    }
}
