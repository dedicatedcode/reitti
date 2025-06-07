package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class TripMergingServiceTest extends AbstractIntegrationTest {
    @Autowired
    private TripMergingService tripMergingService;

    @Autowired
    private TripRepository tripRepository;

    @Test
    void shouldMergeCorrectly() {
        importUntilTrips("/data/gpx/20250601.gpx");

        assertEquals(1, tripRepository.count());
    }
}