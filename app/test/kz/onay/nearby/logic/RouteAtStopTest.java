package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class RouteAtStopTest {
    private static RouteLine line(int direction, int points) {
        double[] lat = new double[points];
        double[] lng = new double[points];
        for (int i = 0; i < points; i++) {
            lat[i] = 43.0 + i * 0.001;
            lng[i] = 76.0;
        }
        return new RouteLine(7L, direction, lat, lng);
    }

    @Test
    void knownDirectionShowsOnlyItsOwnLines() {
        RouteAtStop r = new RouteAtStop(7L, "7", RouteInfo.TYPE_BUS, Arrays.asList(1));
        assertTrue(r.shows(line(1, 2)));
        assertFalse(r.shows(line(0, 2)));
    }

    @Test
    void unknownDirectionShowsEveryLine() {
        RouteAtStop r = new RouteAtStop(7L, "7", RouteInfo.TYPE_BUS, Collections.<Integer>emptyList());
        assertTrue(r.shows(line(0, 2)));
        assertTrue(r.shows(line(1, 2)));
    }

    @Test
    void lineWithOnePointOrNoLineIsNotShown() {
        RouteAtStop r = new RouteAtStop(7L, "7", RouteInfo.TYPE_BUS, Collections.<Integer>emptyList());
        assertFalse(r.shows(line(0, 1)));
        assertFalse(r.shows(null));
    }
}
