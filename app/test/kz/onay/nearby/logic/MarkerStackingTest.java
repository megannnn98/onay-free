package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MarkerStackingTest {
    // 0.0001 degrees of latitude is about 11 m.
    @Test
    void aloneGetsZero() {
        assertArrayEquals(new int[] {0}, MarkerStacking.stackIndexes(new double[] {43.2}, new double[] {76.9}, 20));
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertArrayEquals(new int[0], MarkerStacking.stackIndexes(new double[0], new double[0], 20));
    }

    @Test
    void closePointsAreNumberedInOrder() {
        double[] lat = {43.2000, 43.2001, 43.2002};
        double[] lng = {76.9, 76.9, 76.9};
        assertArrayEquals(new int[] {0, 1, 2}, MarkerStacking.stackIndexes(lat, lng, 30));
    }

    @Test
    void farPointsAreNotStacked() {
        double[] lat = {43.2000, 43.2100};
        double[] lng = {76.9, 76.9};
        assertArrayEquals(new int[] {0, 0}, MarkerStacking.stackIndexes(lat, lng, 30));
    }

    @Test
    void distanceExactlyAtTheRadiusIsNotStacked() {
        double d = Haversine.distanceMeters(43.2, 76.9, 43.2001, 76.9);
        assertArrayEquals(new int[] {0, 0}, MarkerStacking.stackIndexes(
                new double[] {43.2, 43.2001}, new double[] {76.9, 76.9}, d));
        assertArrayEquals(new int[] {0, 1}, MarkerStacking.stackIndexes(
                new double[] {43.2, 43.2001}, new double[] {76.9, 76.9}, d + 0.01));
    }

    @Test
    void onlyEarlierPointsCount() {
        double[] lat = {43.2000, 43.2100, 43.2000};
        double[] lng = {76.9, 76.9, 76.9};
        assertArrayEquals(new int[] {0, 0, 1}, MarkerStacking.stackIndexes(lat, lng, 30));
    }

    @Test
    void mismatchedArraysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MarkerStacking.stackIndexes(new double[] {1}, new double[] {1, 2}, 10));
    }
}
