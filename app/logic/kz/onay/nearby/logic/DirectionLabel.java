package kz.onay.nearby.logic;

/** Human text for the direction of a route. */
public final class DirectionLabel {
    private DirectionLabel() {
    }

    /** "в сторону X" for the last stop of the direction, or null when the stop name is unknown. */
    public static String towards(String lastStopName) {
        if (lastStopName == null || lastStopName.trim().isEmpty()) {
            return null;
        }
        return "в сторону " + lastStopName.trim();
    }
}
