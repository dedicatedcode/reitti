package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.triangulate.tri.Tri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripDetectionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TripDetectionService tripDetectionService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ProcessedVisitRepository processedVisitRepository;

    @Test
    @Transactional
    void shouldDetectTripsBetweenVisits() {
        this.importUntilProcessedVisits("/data/gpx/20250531.gpx");
        this.tripDetectionService.detectTripsForUser(new MergeVisitEvent(user.getUsername(), null, null));

        List<Trip> persistedTrips = tripRepository.findByUser(user);

        List<ProcessedVisit> processedVisits = this.processedVisitRepository.findByUserOrderByStartTime(user);

        List<Trip> expectedTrips = new ArrayList<>();

        for (int i = 0; i < processedVisits.size() - 1; i++) {
            ProcessedVisit start = processedVisits.get(i);
            ProcessedVisit end = processedVisits.get(i + 1);
            if (!end.getPlace().equals(start.getPlace())) {
                expectedTrips.add(new Trip(user, start.getPlace(), end.getPlace(), start.getStartTime(), start.getEndTime(), null, null));
            }
        }

        print(expectedTrips);

        assertEquals(expectedTrips.size(), persistedTrips.size());
        for (int i = 0; i < expectedTrips.size(); i++) {
            Trip expected = expectedTrips.get(i);
            Trip actual = persistedTrips.get(i);

            assertEquals(expected.getStartPlace(), actual.getStartPlace());
            assertEquals(expected.getEndPlace(), actual.getEndPlace());

        }
    }

    void print(List<Trip> trips) {
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            System.out.println(i + ": [" + trip.getStartPlace().getLatitudeCentroid() + "," + trip.getStartPlace().getLongitudeCentroid() + "] " +
                    "-> [" + trip.getEndPlace().getLatitudeCentroid() + "," + trip.getEndPlace().getLongitudeCentroid() + "] @" + trip.getTransportModeInferred());
        }
    }
}