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
        importData("/data/gpx/20250531.gpx", ImportStep.VISITS);

        visitMergingService.mergeVisits(new MergeVisitEvent(user.getUsername(), null, null));

        assertEquals(0, visitRepository.findByUserAndProcessedFalse(user).size());

        List<GeoPoint> expectedVisits = new ArrayList<>();

        expectedVisits.add(new GeoPoint(53.86334539659948, 10.701105248045259)); // Moltke
        expectedVisits.add(new GeoPoint(53.86889230000001, 10.680612066666669)); // Diele
        expectedVisits.add(new GeoPoint(53.86334539659948, 10.701105248045259)); // Moltke
        expectedVisits.add(new GeoPoint(53.86889230000001, 10.680612066666669)); // Diele
        expectedVisits.add(new GeoPoint(53.87306318052629, 10.732658768947365)); // Garten
        expectedVisits.add(new GeoPoint(53.871003894, 10.7458164105)); // Famila
        expectedVisits.add(new GeoPoint(53.8714586375, 10.747866387499998)); // Obi 1
        expectedVisits.add(new GeoPoint(53.87214355833334, 10.747553500000002)); // Obi 2
        expectedVisits.add(new GeoPoint(53.8714586375, 10.747866387499998)); // Obi 1
        expectedVisits.add(new GeoPoint(53.87306318052629, 10.732658768947365)); // Garten
        expectedVisits.add(new GeoPoint(53.86334539659948, 10.701105248045259)); // Moltke

        List<GeoPoint> actualVisits = this.processedVisitRepository.findByUserOrderByStartTime(user).stream().map(pv -> new GeoPoint(pv.getPlace().getLatitudeCentroid(), pv.getPlace().getLongitudeCentroid())).toList();
        verifyVisits(expectedVisits, actualVisits);
    }

    @Test
    @Transactional
    void shouldNotMergeVisitsAtEndOfDay() {
        importData("/data/gpx/20250601.gpx", ImportStep.VISITS);

        // Before merging
        // 1,53.86333445315504,10.701094198219016,2025-05-31T22:39:53.634Z,2025-06-01T06:48:05.555Z,29291,false
        // 2,53.86331389419786,10.701092915884196,2025-06-01T06:52:36.607Z,2025-06-01T12:14:16.108Z,19299,false
        // 3,53.86327076803925,10.701049170196082,2025-06-01T12:34:48.105Z,2025-06-01T13:01:27.045Z,1598,false
        // 4,53.835119448726246,10.982210150382198,2025-06-01T13:25:39Z,2025-06-01T14:54:15Z,5316,false
        // 5,53.83514816615395,10.982174839903868,2025-06-01T14:56:30Z,2025-06-01T15:55:18Z,3528,false
        // 6,53.835155407333325,10.982291070666665,2025-06-01T15:55:52Z,2025-06-01T16:05:31Z,579,false
        // 7,53.86333082351099,10.701096835697554,2025-06-01T16:58:19.380Z,2025-06-01T20:46:31.922Z,13692,false
        visitMergingService.mergeVisits(new MergeVisitEvent(user.getUsername(), null, null));

        // Expected after merging
        // 1,53.86333445315504,10.701094198219016,2025-05-31T22:39:53.634Z,2025-06-01T06:48:05.555Z,29291,false
        // 2,53.86331389419786,10.701092915884196,2025-06-01T06:52:36.607Z,2025-06-01T12:14:16.108Z,19299,false
        // 3,53.86327076803925,10.701049170196082,2025-06-01T12:34:48.105Z,2025-06-01T13:01:27.045Z,1598,false

        // 4,53.835119448726246,10.982210150382198,2025-06-01T13:25:39Z,2025-06-01T14:54:15Z,5316,false
        // 5,53.83514816615395,10.982174839903868,2025-06-01T14:56:30Z,2025-06-01T15:55:18Z,3528,false
        // 6,53.835155407333325,10.982291070666665,2025-06-01T15:55:52Z,2025-06-01T16:05:31Z,579,false

        // 7,53.86333082351099,10.701096835697554,2025-06-01T16:58:19.380Z,2025-06-01T20:46:31.922Z,13692,false


        assertEquals(0, visitRepository.findByUserAndProcessedFalse(user).size());

        List<GeoPoint> expectedVisits = new ArrayList<>();
        expectedVisits.add(new GeoPoint(53.863149, 10.700927)); // Moltke
        // trip between 1/2 walk, 1/2 car
        expectedVisits.add(new GeoPoint(53.863149, 10.700927)); // Moltke
        // trip between -> car
        expectedVisits.add(new GeoPoint(53.835121, 10.982272)); // Retelsdorf
        // trip between -> car
        expectedVisits.add(new GeoPoint(53.863149, 10.700927)); // Moltke
        // trip between 1/2 car, 1/2 walk
        expectedVisits.add(new GeoPoint(53.863149, 10.700927)); // Moltke

        List<GeoPoint> actualVisits = this.processedVisitRepository.findByUserOrderByStartTime(user).stream().map(pv -> new GeoPoint(pv.getPlace().getLatitudeCentroid(), pv.getPlace().getLongitudeCentroid())).toList();



        verifyVisits(expectedVisits, actualVisits);
    }

    private static void verifyVisits(List<GeoPoint> expectedVisits, List<GeoPoint> actualVisits) {
        assertEquals(expectedVisits.size(), actualVisits.size());
        for (int i = 0; i < actualVisits.size(); i++) {
            GeoPoint expected = expectedVisits.get(i);
            GeoPoint actual = actualVisits.get(i);

            double distanceInMeters = GeoUtils.distanceInMeters(actual.latitude(), actual.longitude(), expected.latitude(), expected.longitude());
            assertTrue(distanceInMeters < 50, "Distance between " + actual + " and " + expected + " is too large. Should be less than 25m but was " + distanceInMeters + "m for index " + i + ".");
        }
    }
}