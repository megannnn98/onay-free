package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VehicleInfoTextTest {
    @Test
    void fullLine() {
        assertEquals("77 · автобус · в сторону Арман · данные 3 мин назад",
                VehicleInfoText.format("77", "автобус", "в сторону Арман", 200));
    }

    @Test
    void directionIsOmittedWhenUnknownOrBlank() {
        assertEquals("1 · троллейбус · данные меньше минуты назад", VehicleInfoText.format("1", "троллейбус", null, 30));
        assertEquals("1 · троллейбус · данные меньше минуты назад", VehicleInfoText.format("1", "троллейбус", "  ", 30));
    }

    @Test
    void justReportedAndTrimmedDirection() {
        assertEquals("101 · автобус · на запад · данные только что", VehicleInfoText.format("101", "автобус", " на запад ", 0));
        assertEquals("101 · автобус · данные только что", VehicleInfoText.format("101", "автобус", null, -5));
    }
}
