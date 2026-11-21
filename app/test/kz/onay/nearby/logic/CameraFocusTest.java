package kz.onay.nearby.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CameraFocusTest {
    private static VehiclePosition at(long id, double lat, double lng) {
        return new VehiclePosition(id, 1L, 0, lat, lng, 0.0, 100L);
    }

    @Test
    void stopIsAlwaysFirst() {
        List<double[]> p = CameraFocus.points(43.2, 76.9, null, Collections.emptyList(), 1500);
        assertEquals(1, p.size());
        assertEquals(43.2, p.get(0)[0], 0.0);
        assertEquals(76.9, p.get(0)[1], 0.0);
    }

    @Test
    void nearUserIsIncluded() {
        List<double[]> p = CameraFocus.points(43.2, 76.9, new double[] {43.2010, 76.9}, null, 600);
        assertEquals(2, p.size());
        assertEquals(43.2010, p.get(1)[0], 0.0);
    }

    @Test
    void farUserIsLeftOutSoAPickedStopInAnotherDistrictStaysInFocus() {
        List<double[]> p = CameraFocus.points(43.2, 76.9, new double[] {43.3, 77.0}, null, 600);
        assertEquals(1, p.size());
    }

    @Test
    void userExactlyAtTheRadiusIsIncluded() {
        double d = Haversine.distanceMeters(43.2, 76.9, 43.2100, 76.9);
        assertEquals(2, CameraFocus.points(43.2, 76.9, new double[] {43.2100, 76.9}, null, d).size());
        assertEquals(1, CameraFocus.points(43.2, 76.9, new double[] {43.2100, 76.9}, null, d - 0.01).size());
    }

    @Test
    void farVehiclesAreLeftOutSoTheMapDoesNotZoomOutToTheCity() {
        VehiclePosition near = at(1, 43.2010, 76.9);   // about 110 m
        VehiclePosition far = at(2, 43.3500, 77.1);    // many km
        List<double[]> p = CameraFocus.points(43.2, 76.9, null, Arrays.asList(near, far), 1500);
        assertEquals(2, p.size());
        assertEquals(43.2010, p.get(1)[0], 0.0);
    }

    @Test
    void vehicleExactlyAtTheRadiusIsIncluded() {
        double d = Haversine.distanceMeters(43.2, 76.9, 43.2100, 76.9);
        List<double[]> p = CameraFocus.points(43.2, 76.9, null, Collections.singletonList(at(1, 43.2100, 76.9)), d);
        assertEquals(2, p.size());
        List<double[]> q = CameraFocus.points(43.2, 76.9, null, Collections.singletonList(at(1, 43.2100, 76.9)), d - 0.01);
        assertEquals(1, q.size());
    }
}
