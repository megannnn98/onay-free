package kz.onay.nearby.logic;

import java.util.Map;

/** Picks the display name (the number shown on the button) of a route from its translations. */
public final class RouteNames {
    private RouteNames() {
    }

    /** With no usable name a placeholder with the route id is returned. */
    public static String choose(Map<String, String> byLanguage, String preferredLanguage, long routeId) {
        String name = Translations.pick(byLanguage, preferredLanguage);
        return name != null ? name.trim() : "Маршрут " + routeId;
    }
}
