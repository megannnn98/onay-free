package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BearingTest {
    private static final double LAT = 43.2380;
    private static final double LNG = 76.9450;

    @Test
    void northIsZero() {
        assertEquals(0.0, Bearing.degrees(LAT, LNG, LAT + 0.001, LNG), 0.5);
    }

    @Test
    void eastIsNinety() {
        assertEquals(90.0, Bearing.degrees(LAT, LNG, LAT, LNG + 0.001), 0.5);
    }

    @Test
    void southIsOneEighty() {
        assertEquals(180.0, Bearing.degrees(LAT, LNG, LAT - 0.001, LNG), 0.5);
    }

    @Test
    void westIsTwoSeventy() {
        assertEquals(270.0, Bearing.degrees(LAT, LNG, LAT, LNG - 0.001), 0.5);
    }

    @Test
    void resultIsAlwaysInZeroToThreeSixty() {
        double b = Bearing.degrees(LAT, LNG, LAT + 0.0005, LNG - 0.001);
        assertTrue(b >= 0.0 && b < 360.0);
    }

    @Test
    void samePointGivesZero() {
        assertEquals(0.0, Bearing.degrees(LAT, LNG, LAT, LNG), 0.0);
    }
}
