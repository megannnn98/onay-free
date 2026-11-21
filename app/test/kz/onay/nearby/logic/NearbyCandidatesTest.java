package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyCandidatesTest {
    private static StopMatch m(long id, double meters) {
        return new StopMatch(new StopPoint(id, "S" + id, 43.0, 76.0), meters);
    }

    private static List<Long> ids(List<StopMatch> l) {
        List<Long> out = new ArrayList<>();
        for (StopMatch x : l) {
            out.add(x.getStop().getId());
        }
        return out;
    }

    @Test
    void oppositeSideStopCloseToTheNearestIsAnAlternative() {
        List<StopMatch> sorted = Arrays.asList(m(1, 20), m(2, 45), m(3, 400));
        assertEquals(Arrays.asList(2L), ids(NearbyCandidates.alternatives(sorted, 100, 3)));
    }

    @Test
    void farStopsAreNotAlternatives() {
        List<StopMatch> sorted = Arrays.asList(m(1, 20), m(2, 500));
        assertTrue(NearbyCandidates.alternatives(sorted, 100, 3).isEmpty());
    }

    @Test
    void limitIsApplied() {
        List<StopMatch> sorted = Arrays.asList(m(1, 10), m(2, 20), m(3, 30), m(4, 40));
        assertEquals(Arrays.asList(2L, 3L), ids(NearbyCandidates.alternatives(sorted, 100, 2)));
    }

    @Test
    void unsortedInputIsHandled() {
        // Ids deliberately do not follow the distances.
        List<StopMatch> unsorted = Arrays.asList(m(1, 60), m(3, 20), m(2, 40));
        assertEquals(Arrays.asList(2L, 1L), ids(NearbyCandidates.alternatives(unsorted, 100, 5)));
    }

    @Test
    void extraDistanceIsInclusive() {
        List<StopMatch> sorted = Arrays.asList(m(1, 20), m(2, 120));
        assertEquals(Arrays.asList(2L), ids(NearbyCandidates.alternatives(sorted, 100, 3)));
    }

    @Test
    void emptyNullAndSingleGiveEmpty() {
        assertTrue(NearbyCandidates.alternatives(Collections.<StopMatch>emptyList(), 100, 3).isEmpty());
        assertTrue(NearbyCandidates.alternatives(null, 100, 3).isEmpty());
        assertTrue(NearbyCandidates.alternatives(Arrays.asList(m(1, 5)), 100, 3).isEmpty());
    }

    @Test
    void nonPositiveMaxGivesEmpty() {
        assertTrue(NearbyCandidates.alternatives(Arrays.asList(m(1, 5), m(2, 6)), 100, 0).isEmpty());
    }

    @Test
    void sameStopIdIsNeverAnAlternativeToItself() {
        List<StopMatch> sorted = Arrays.asList(m(1, 20), m(1, 30), m(2, 40));
        assertEquals(Arrays.asList(2L), ids(NearbyCandidates.alternatives(sorted, 100, 3)));
    }
}
