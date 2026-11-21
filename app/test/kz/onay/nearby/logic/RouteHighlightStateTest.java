package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteHighlightStateTest {
    @Test
    void startsWithNoSelectionAndEverythingOpaque() {
        RouteHighlightState s = new RouteHighlightState();
        assertNull(s.getSelectedRouteId());
        assertFalse(s.hasSelection());
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(1L), 0.0);
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(2L), 0.0);
    }

    @Test
    void selectingDimsTheOthers() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(1L);
        assertEquals(Long.valueOf(1L), s.getSelectedRouteId());
        assertTrue(s.isSelected(1L));
        assertFalse(s.isSelected(2L));
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(1L), 0.0);
        assertEquals(RouteHighlightState.DIM_ALPHA, s.alphaFor(2L), 0.0);
    }

    @Test
    void togglingSameRouteClearsSelection() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(1L);
        s.toggle(1L);
        assertNull(s.getSelectedRouteId());
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(2L), 0.0);
    }

    @Test
    void togglingAnotherRouteSwitchesSelection() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(1L);
        s.toggle(2L);
        assertEquals(Long.valueOf(2L), s.getSelectedRouteId());
        assertEquals(RouteHighlightState.DIM_ALPHA, s.alphaFor(1L), 0.0);
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(2L), 0.0);
    }

    @Test
    void clearRemovesSelection() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(1L);
        s.clear();
        assertFalse(s.hasSelection());
        assertEquals(RouteHighlightState.FULL_ALPHA, s.alphaFor(1L), 0.0);
    }

    @Test
    void dimAlphaIsBetweenZeroAndFull() {
        assertTrue(RouteHighlightState.DIM_ALPHA > 0f);
        assertTrue(RouteHighlightState.DIM_ALPHA < RouteHighlightState.FULL_ALPHA);
    }

    @Test
    void retainOnlyDropsSelectionOfRouteThatDisappeared() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(5L);
        s.retainOnly(java.util.Arrays.asList(1L, 2L));
        assertFalse(s.hasSelection());
    }

    @Test
    void retainOnlyKeepsSelectionOfRouteThatStillExists() {
        RouteHighlightState s = new RouteHighlightState();
        s.toggle(2L);
        s.retainOnly(java.util.Arrays.asList(1L, 2L));
        assertEquals(Long.valueOf(2L), s.getSelectedRouteId());
    }
}
