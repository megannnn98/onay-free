package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DirectionLabelTest {
    @Test
    void namesTheLastStop() {
        assertEquals("в сторону Аль-Фараби", DirectionLabel.towards("Аль-Фараби"));
    }

    @Test
    void trimsTheName() {
        assertEquals("в сторону Достык", DirectionLabel.towards("  Достык  "));
    }

    @Test
    void blankOrMissingNameGivesNoLabel() {
        assertNull(DirectionLabel.towards(null));
        assertNull(DirectionLabel.towards(""));
        assertNull(DirectionLabel.towards("   "));
    }
}
