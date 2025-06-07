package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.repository.TripRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
class TripMergingServiceTest extends AbstractIntegrationTest {
    @Autowired
    private TripMergingService tripMergingService;

    @Autowired
    private TripRepository tripRepository;

    @Test
    void shouldMergeCorrectly() {
        List<Trip> trips = importUntilTrips("/data/gpx/20250601.gpx");

        assertEquals(1, tripRepository.count());
    }
}