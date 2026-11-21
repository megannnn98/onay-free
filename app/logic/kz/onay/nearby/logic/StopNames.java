package kz.onay.nearby.logic;

import java.util.Map;

/** Picks the display name of a stop from its translations. */
public final class StopNames {
    private StopNames() {
    }

    /** With no usable name a placeholder with the stop id is returned. */
    public static String choose(Map<String, String> byLanguage, String preferredLanguage, long stopId) {
        String name = Translations.pick(byLanguage, preferredLanguage);
        return name != null ? name : "Остановка " + stopId;
    }
}
