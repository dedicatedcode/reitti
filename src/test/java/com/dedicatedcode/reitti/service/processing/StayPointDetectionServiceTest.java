package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.GeoUtils;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StayPointDetectionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    private StayPointDetectionService stayPointDetectionService;

    @Test
    void shouldCalculateCorrectStayPoints() {
        importGpx("/data/gpx/20250531.gpx");
        List<RawLocationPoint> all = rawLocationPointRepository.findByUserOrderByTimestampAsc(user);
        int splitSize = 100;
        List<List<StayPoint>> stayPoints = new ArrayList<>();
        while (all.size() >= splitSize) {
            List<RawLocationPoint> current = new ArrayList<>(all.subList(0, splitSize));
            all.removeAll(current);
            stayPoints.add(stayPointDetectionService.detectStayPoints(user, current));
        }
        if (!all.isEmpty()) {
            stayPoints.add(stayPointDetectionService.detectStayPoints(user, all));
        }

        List<StayPoint> expectedStayPointsInOrder = new ArrayList<>();
        expectedStayPointsInOrder.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.
        expectedStayPointsInOrder.add(new StayPoint(53.86889230000001, 10.680612066666669, null, null, null)); //Diele.
        expectedStayPointsInOrder.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.
        expectedStayPointsInOrder.add(new StayPoint(53.86889230000001, 10.680612066666669, null, null, null)); //Diele.
        expectedStayPointsInOrder.add(new StayPoint(53.87306318052629, 10.732658768947365, null, null, null)); //Garten.
        expectedStayPointsInOrder.add(new StayPoint(53.87101884785715, 10.745859928571429, null, null, null)); //Fimila
        expectedStayPointsInOrder.add(new StayPoint(53.871636138461504, 10.747298292564096, null, null, null)); //Obi
        expectedStayPointsInOrder.add(new StayPoint(53.87216447272729, 10.747552527272727, null, null, null)); //Obi
        expectedStayPointsInOrder.add(new StayPoint(53.871564058,10.747507870888889, null, null, null)); //Obi
        expectedStayPointsInOrder.add(new StayPoint(53.873079353158, 10.73264953157896, null, null, null)); //Garten
        expectedStayPointsInOrder.add(new StayPoint(53.86334557300011, 10.701107468000021, null, null, null)); //Moltkestr.

        List<StayPoint> distinctStayPoints = new ArrayList<>();

        List<StayPoint> flatStayPoints = stayPoints.stream().flatMap(Collection::stream).sorted(Comparator.comparing(StayPoint::getArrivalTime)).toList();
        StayPoint last = null;
        int checkThresholdInMeters = 50;
        for (StayPoint point : flatStayPoints) {
            if (last == null || GeoUtils.distanceInMeters(last, point) >= checkThresholdInMeters) {
                last = point;
                distinctStayPoints.add(point);
            }
        }

        assertEquals(expectedStayPointsInOrder.size(), distinctStayPoints.size());
        for (int i = 0; i < expectedStayPointsInOrder.size(); i++) {
            StayPoint expected = expectedStayPointsInOrder.get(i);
            StayPoint actual = distinctStayPoints.get(i);

            assertTrue(GeoUtils.distanceInMeters(actual, expected) < checkThresholdInMeters,
                    "Distance between " + actual + " and " + expected + " is too large. Should be less than " + checkThresholdInMeters + "m but was " + GeoUtils.distanceInMeters(actual, expected) + "m for index " + i + ".");
        }
    }
}