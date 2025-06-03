package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.GeoUtils;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VisitMergingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private VisitMergingService visitMergingService;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ProcessedVisitRepository processedVisitRepository;

    @Test
    @Transactional
    void shouldMergeVisitsInTimeFrame() {
        importUntilVisits("/data/gpx/20250531.gpx");

        visitMergingService.mergeVisits(new MergeVisitEvent(user.getUsername(), null, null));

        assertEquals(0, visitRepository.findByUserAndProcessedFalse(user).size());

        List<GeoPoint> expectedVisits = new ArrayList<>();

        expectedVisits.add(new GeoPoint(53.86334539659948,10.701105248045259)); // Moltke
        expectedVisits.add(new GeoPoint(53.86889230000001,10.680612066666669)); // Diele
        expectedVisits.add(new GeoPoint(53.86334539659948,10.701105248045259)); // Moltke
        expectedVisits.add(new GeoPoint(53.86889230000001,10.680612066666669)); // Diele
        expectedVisits.add(new GeoPoint(53.87306318052629,10.732658768947365)); // Garten
        expectedVisits.add(new GeoPoint(53.871003894,10.7458164105)); // Famila
        expectedVisits.add(new GeoPoint(53.8714586375,10.747866387499998)); // Obi 1
        expectedVisits.add(new GeoPoint(53.87214355833334,10.747553500000002)); // Obi 2
        expectedVisits.add(new GeoPoint(53.8714586375,10.747866387499998)); // Obi 1
        expectedVisits.add(new GeoPoint(53.87306318052629,10.732658768947365)); // Garten
        expectedVisits.add(new GeoPoint(53.86334539659948,10.701105248045259)); // Moltke

        List<GeoPoint> actualVisits = this.processedVisitRepository.findByUserOrderByStartTime(user).stream().map(pv -> new GeoPoint(pv.getPlace().getLatitudeCentroid(), pv.getPlace().getLongitudeCentroid())).toList();
        assertEquals(expectedVisits.size(), actualVisits.size());
        for (int i = 0; i < actualVisits.size(); i++) {
            GeoPoint expected = expectedVisits.get(i);
            GeoPoint actual = actualVisits.get(i);

            double distanceInMeters = GeoUtils.distanceInMeters(actual.latitude(), actual.longitude(), expected.latitude(), expected.longitude());
            assertTrue(distanceInMeters < 50, "Distance between " + actual + " and " + expected + " is too large. Should be less than 25m but was " + distanceInMeters + "m for index " + i + ".");
        }

    }
}