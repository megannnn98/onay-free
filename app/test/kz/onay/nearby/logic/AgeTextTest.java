package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgeTextTest {
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;

    @Test
    void underAMinute() {
        assertEquals("меньше минуты назад", AgeText.ago(0));
        assertEquals("меньше минуты назад", AgeText.ago(59_999));
    }

    @Test
    void negativeAgeCountsAsJustNow() {
        assertEquals("меньше минуты назад", AgeText.ago(-5_000));
    }

    @Test
    void minutes() {
        assertEquals("1 мин назад", AgeText.ago(MIN));
        assertEquals("59 мин назад", AgeText.ago(59 * MIN + 59_999));
    }

    @Test
    void hours() {
        assertEquals("1 ч назад", AgeText.ago(HOUR));
        assertEquals("23 ч назад", AgeText.ago(23 * HOUR + 59 * MIN));
    }

    @Test
    void moreThanADay() {
        assertEquals("более суток назад", AgeText.ago(24 * HOUR));
        assertEquals("более суток назад", AgeText.ago(1000 * HOUR));
    }
}
