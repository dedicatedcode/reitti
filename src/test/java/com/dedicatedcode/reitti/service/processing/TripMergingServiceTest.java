package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.repository.TripRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripMergingServiceTest extends AbstractIntegrationTest {


    @Autowired
    private TripRepository tripRepository;

    @Test
    void shouldMergeCorrectly() {
        List<Trip> trips = importData("/data/gpx/20250601.gpx", ImportStep.TRIPS);

        assertEquals(3, tripRepository.count());
    }
}