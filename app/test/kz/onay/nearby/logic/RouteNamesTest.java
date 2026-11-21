package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteNamesTest {
    private static Map<String, String> names(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void usesPreferredLanguage() {
        assertEquals("12", RouteNames.choose(names("ru-Ru", "12", "en_US", "12A"), "ru", 5));
        assertEquals("12A", RouteNames.choose(names("ru-Ru", "12", "en_US", "12A"), "en", 5));
    }

    @Test
    void noNamePlaceholderMentionsRouteNotStop() {
        assertEquals("Маршрут 5", RouteNames.choose(Collections.<String, String>emptyMap(), "ru", 5));
        assertEquals("Маршрут 5", RouteNames.choose(null, "ru", 5));
    }

    @Test
    void surroundingSpacesAreTrimmed() {
        assertEquals("12", RouteNames.choose(names("ru-Ru", " 12 "), "ru", 5));
    }

    @Test
    void blankNameFallsBackToAnotherLanguage() {
        assertEquals("12", RouteNames.choose(names("ru-Ru", " ", "kk-KZ", "12"), "ru", 5));
    }
}
