package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocationQualityTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long MAX_AGE = 120_000L;

    @Test
    void freshFixIsOk() {
        assertEquals(LocationQuality.Verdict.OK, LocationQuality.classify(43.2, 76.9, NOW - 5_000, NOW, MAX_AGE));
    }

    @Test
    void fixExactlyAtMaxAgeIsStillOk() {
        assertEquals(LocationQuality.Verdict.OK, LocationQuality.classify(43.2, 76.9, NOW - MAX_AGE, NOW, MAX_AGE));
    }

    @Test
    void olderFixIsStale() {
        assertEquals(LocationQuality.Verdict.STALE, LocationQuality.classify(43.2, 76.9, NOW - MAX_AGE - 1, NOW, MAX_AGE));
    }

    @Test
    void unknownFixTimeIsStale() {
        assertEquals(LocationQuality.Verdict.STALE, LocationQuality.classify(43.2, 76.9, null, NOW, MAX_AGE));
    }

    @Test
    void fixFromTheFutureCountsAsFresh() {
        // Small clock differences between the GPS chip and the phone must not reject a good fix.
        assertEquals(LocationQuality.Verdict.OK, LocationQuality.classify(43.2, 76.9, NOW + 3_000, NOW, MAX_AGE));
    }

    @Test
    void missingCoordinates() {
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(null, 76.9, NOW, NOW, MAX_AGE));
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(43.2, null, NOW, NOW, MAX_AGE));
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(Double.NaN, 76.9, NOW, NOW, MAX_AGE));
    }

    @Test
    void outOfRangeCoordinates() {
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(91.0, 76.9, NOW, NOW, MAX_AGE));
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(43.2, 181.0, NOW, NOW, MAX_AGE));
    }

    @Test
    void nullIslandIsTreatedAsMissing() {
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(0.0, 0.0, NOW, NOW, MAX_AGE));
    }

    @Test
    void missingWinsOverStale() {
        assertEquals(LocationQuality.Verdict.MISSING, LocationQuality.classify(null, null, null, NOW, MAX_AGE));
    }
}
