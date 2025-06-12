package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.GeoPoint;
import com.dedicatedcode.reitti.model.GeoUtils;
import com.dedicatedcode.reitti.model.StayPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dedicatedcode.reitti.TestConstants.Points.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitDetectionServiceTest extends AbstractIntegrationTest {

    @Test
    void shouldCalculateCorrectStayPoints() {
        List<StayPoint> stayPoints = importData("/data/gpx/20250531.gpx", ImportStep.STAY_POINTS);

        List<GeoPoint> expectedStayPointsInOrder = new ArrayList<>();

        expectedStayPointsInOrder.add(MOLTKESTR);
        expectedStayPointsInOrder.add(DIELE);
        expectedStayPointsInOrder.add(MOLTKESTR);
        expectedStayPointsInOrder.add(DIELE);
        expectedStayPointsInOrder.add(GARTEN);
        expectedStayPointsInOrder.add(FAMILA);
        expectedStayPointsInOrder.add(OBI);
        expectedStayPointsInOrder.add(GARTEN);
        expectedStayPointsInOrder.add(MOLTKESTR);

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
        verifyPoints(expectedStayPointsInOrder, distinctStayPoints.stream().map(p -> new GeoPoint(p.getLatitude(), p.getLongitude())).toList(), 50);
    }
}