package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StopNamesTest {
    private static Map<String, String> names(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void exactLanguageKeyWins() {
        assertEquals("Абая", StopNames.choose(names("en_US", "Abay", "ru-Ru", "Абая"), "ru-Ru", 7));
    }

    @Test
    void exactKeyBeatsAnotherVariantOfTheSameLanguage() {
        assertEquals("Yank", StopNames.choose(names("en_GB", "Brit", "en_US", "Yank"), "en_US", 7));
    }

    @Test
    void matchesByLanguagePrefix() {
        assertEquals("Абая", StopNames.choose(names("en_US", "Abay", "ru-Ru", "Абая"), "ru", 7));
        assertEquals("Abay", StopNames.choose(names("en_US", "Abay", "ru-Ru", "Абая"), "en", 7));
    }

    @Test
    void prefixMatchIsCaseInsensitive() {
        assertEquals("Абая", StopNames.choose(names("RU-ru", "Абая"), "ru", 7));
    }

    @Test
    void fallsBackToRussianThenToAnyName() {
        assertEquals("Абая", StopNames.choose(names("en_US", "Abay", "ru-Ru", "Абая"), "kk", 7));
        assertEquals("Abay", StopNames.choose(names("en_US", "Abay"), "kk", 7));
    }

    @Test
    void blankNamesAreIgnored() {
        assertEquals("Abay", StopNames.choose(names("ru-Ru", "  ", "en_US", "Abay"), "ru", 7));
    }

    @Test
    void noNameGivesPlaceholderWithId() {
        assertEquals("Остановка 7", StopNames.choose(Collections.<String, String>emptyMap(), "ru", 7));
        assertEquals("Остановка 7", StopNames.choose(null, "ru", 7));
        assertEquals("Остановка 7", StopNames.choose(names("ru-Ru", ""), "ru", 7));
    }

    @Test
    void nullPreferredLanguageStillWorks() {
        assertEquals("Абая", StopNames.choose(names("ru-Ru", "Абая"), null, 7));
    }
}
