package kz.onay.nearby.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Picks the display name of a stop from its translations. */
public final class StopNames {
    private StopNames() {
    }

    /**
     * Order: exact language key, same primary language ("ru" matches "ru-Ru"), Russian, any name.
     * Blank names are ignored. With no usable name a placeholder with the stop id is returned.
     */
    public static String choose(Map<String, String> byLanguage, String preferredLanguage, long stopId) {
        if (byLanguage != null) {
            List<String> keys = new ArrayList<>(byLanguage.keySet());
            Collections.sort(keys);
            if (preferredLanguage != null) {
                for (String k : keys) {
                    if (k.equalsIgnoreCase(preferredLanguage) && usable(byLanguage.get(k))) {
                        return byLanguage.get(k);
                    }
                }
                String pref = primary(preferredLanguage);
                for (String k : keys) {
                    if (primary(k).equals(pref) && usable(byLanguage.get(k))) {
                        return byLanguage.get(k);
                    }
                }
            }
            for (String k : keys) {
                if (primary(k).equals("ru") && usable(byLanguage.get(k))) {
                    return byLanguage.get(k);
                }
            }
            for (String k : keys) {
                if (usable(byLanguage.get(k))) {
                    return byLanguage.get(k);
                }
            }
        }
        return "Остановка " + stopId;
    }

    private static boolean usable(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String primary(String lang) {
        String l = lang.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < l.length() && l.charAt(i) != '-' && l.charAt(i) != '_') {
            i++;
        }
        return l.substring(0, i);
    }
}
