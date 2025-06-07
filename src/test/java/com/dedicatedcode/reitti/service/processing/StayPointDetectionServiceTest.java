package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StayPointDetectionServiceTest extends AbstractIntegrationTest {

    @Test
    void shouldCalculateCorrectStayPoints() {
        List<StayPoint> stayPoints = importData("/data/gpx/20250531.gpx", ImportStep.STAY_POINTS);

        List<GeoPoint> expectedStayPointsInOrder = new ArrayList<>();
        expectedStayPointsInOrder.add(new GeoPoint(53.86334557300011, 10.701107468000021)); //Moltkestr.
        expectedStayPointsInOrder.add(new GeoPoint(53.86889230000001, 10.680612066666669)); //Diele.
        expectedStayPointsInOrder.add(new GeoPoint(53.86334557300011, 10.701107468000021)); //Moltkestr.
        expectedStayPointsInOrder.add(new GeoPoint(53.86889230000001, 10.680612066666669)); //Diele.
        expectedStayPointsInOrder.add(new GeoPoint(53.87306318052629, 10.732658768947365)); //Garten.
        expectedStayPointsInOrder.add(new GeoPoint(53.87101884785715, 10.745859928571429)); //Fimila
        expectedStayPointsInOrder.add(new GeoPoint(53.871636138461504, 10.747298292564096)); //Obi
        expectedStayPointsInOrder.add(new GeoPoint(53.87216447272729, 10.747552527272727)); //Obi
        expectedStayPointsInOrder.add(new GeoPoint(53.871564058,10.747507870888889)); //Obi
        expectedStayPointsInOrder.add(new GeoPoint(53.873079353158, 10.73264953157896)); //Garten
        expectedStayPointsInOrder.add(new GeoPoint(53.86334557300011, 10.701107468000021)); //Moltkestr.

        verifyStayPoints(stayPoints, expectedStayPointsInOrder);
    }

    private static void verifyStayPoints(List<StayPoint> stayPoints, List<GeoPoint> expectedStayPointsInOrder) {
        List<StayPoint> distinctStayPoints = new ArrayList<>();
        StayPoint last = null;
        int checkThresholdInMeters = 50;
        for (StayPoint point : stayPoints) {
            if (last == null || GeoUtils.distanceInMeters(last, point) >= checkThresholdInMeters) {
                last = point;
                distinctStayPoints.add(point);
            }
        }

        assertEquals(expectedStayPointsInOrder.size(), distinctStayPoints.size());
        for (int i = 0; i < expectedStayPointsInOrder.size(); i++) {
            GeoPoint expected = expectedStayPointsInOrder.get(i);
            StayPoint actual = distinctStayPoints.get(i);

            double distanceInMeters = GeoUtils.distanceInMeters(actual.getLatitude(), actual.getLongitude(), expected.latitude(), expected.longitude());
            assertTrue(distanceInMeters < checkThresholdInMeters,
                    "Distance between " + actual + " and " + expected + " is too large. Should be less than " + checkThresholdInMeters + "m but was " + distanceInMeters + "m for index " + i + ".");
        }
    }
}