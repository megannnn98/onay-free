package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RouteLineTest {
    @Test
    void keepsPointsAndIdentity() {
        RouteLine l = new RouteLine(12L, 1, new double[] {43.0, 43.1}, new double[] {76.0, 76.1});
        assertEquals(12L, l.getRouteId());
        assertEquals(1, l.getDirectionIndex());
        assertEquals(2, l.size());
        assertEquals(43.1, l.getLat(1), 0.0);
        assertEquals(76.0, l.getLng(0), 0.0);
        assertFalse(l.isEmpty());
    }

    @Test
    void copiesInputArrays() {
        double[] lat = {1.0, 2.0};
        double[] lng = {3.0, 4.0};
        RouteLine l = new RouteLine(1L, 0, lat, lng);
        lat[0] = 99.0;
        assertEquals(1.0, l.getLat(0), 0.0);
    }

    @Test
    void mismatchedArrayLengthsAreRejected() {
        try {
            new RouteLine(1L, 0, new double[] {1.0}, new double[] {1.0, 2.0});
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("length"));
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    @Test
    void lessThanTwoPointsIsNotDrawable() {
        assertFalse(new RouteLine(1L, 0, new double[] {1.0}, new double[] {1.0}).isDrawable());
        assertTrue(new RouteLine(1L, 0, new double[] {1.0, 2.0}, new double[] {1.0, 2.0}).isDrawable());
        assertTrue(new RouteLine(1L, 0, new double[0], new double[0]).isEmpty());
    }

    @Test
    void finalBearingUsesLastTwoDistinctPoints() {
        // Last two points coincide, so the arrow direction comes from the previous distinct pair (east).
        RouteLine l = new RouteLine(1L, 0,
                new double[] {43.0, 43.0, 43.0},
                new double[] {76.000, 76.001, 76.001});
        assertEquals(90.0, l.finalBearing(), 0.5);
    }

    @Test
    void finalBearingOfSinglePointLineIsZero() {
        assertEquals(0.0, new RouteLine(1L, 0, new double[] {1.0}, new double[] {1.0}).finalBearing(), 0.0);
        assertEquals(Arrays.asList(1).size(), 1);
    }
}
