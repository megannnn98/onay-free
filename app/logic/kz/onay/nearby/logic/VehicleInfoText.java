package kz.onay.nearby.logic;

/** One line of text about a tapped vehicle. Only facts the app has: route, type, direction, report age. */
public final class VehicleInfoText {
    private VehicleInfoText() {
    }

    public static String format(String routeName, String typeLabel, String directionLabel, long reportAgeSec) {
        StringBuilder sb = new StringBuilder(routeName).append(" · ").append(typeLabel);
        if (directionLabel != null && !directionLabel.trim().isEmpty()) {
            sb.append(" · ").append(directionLabel.trim());
        }
        sb.append(" · данные ").append(reportAgeSec <= 0 ? "только что" : AgeText.ago(reportAgeSec * 1000L));
        return sb.toString();
    }
}
