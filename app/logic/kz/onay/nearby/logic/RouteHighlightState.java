package kz.onay.nearby.logic;

import java.util.Collection;

/** Which route is selected and how opaque each route line should be. Plain Java, no Android types. */
public final class RouteHighlightState {
    public static final float FULL_ALPHA = 1.0f;
    public static final float DIM_ALPHA = 0.25f;

    private Long selectedRouteId;

    public Long getSelectedRouteId() {
        return selectedRouteId;
    }

    public boolean hasSelection() {
        return selectedRouteId != null;
    }

    public boolean isSelected(long routeId) {
        return selectedRouteId != null && selectedRouteId == routeId;
    }

    /** Selects the route, or clears the selection when it is already selected. */
    public void toggle(long routeId) {
        selectedRouteId = isSelected(routeId) ? null : Long.valueOf(routeId);
    }

    public void clear() {
        selectedRouteId = null;
    }

    /** Drops the selection if its route is no longer shown (for example after the stop changed). */
    public void retainOnly(Collection<Long> routeIds) {
        if (selectedRouteId != null && (routeIds == null || !routeIds.contains(selectedRouteId))) {
            selectedRouteId = null;
        }
    }

    /** A route line is drawn only for the selected route: all lines at once would cover the map. */
    public boolean isLineVisible(long routeId) {
        return isSelected(routeId);
    }

    public float alphaFor(long routeId) {
        return selectedRouteId == null || selectedRouteId == routeId ? FULL_ALPHA : DIM_ALPHA;
    }
}
