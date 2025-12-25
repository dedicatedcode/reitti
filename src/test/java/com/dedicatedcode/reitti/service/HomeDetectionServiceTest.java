package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.dedicatedcode.reitti.model.geo.SignificantPlace.PlaceType.HOME;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class HomeDetectionServiceTest {

    @Autowired
    private HomeDetectionService homeDetectionService;

    @Test
    void testFindAccommodation_SingleCandidate() {
        // Create a significant place
        SignificantPlace place = new SignificantPlace(1L, "Home", "123 Main St", "City", "US", 40.0, -74.0, null, HOME, ZoneId.of("America/New_York"), true, 1L);

        // Create a visit that spans sleeping hours
        Instant start = Instant.parse("2023-10-01T20:00:00Z");
        Instant end = Instant.parse("2023-10-02T08:00:00Z");
        ProcessedVisit visit = new ProcessedVisit(1L, place, start, end, 43200L, 1L); // 12 hours

        Instant memoryStart = Instant.parse("2023-10-01T00:00:00Z");
        Instant memoryEnd = Instant.parse("2023-10-02T12:00:00Z");

        Optional<ProcessedVisit> result = homeDetectionService.findAccommodation(List.of(visit), memoryStart, memoryEnd);

        assertTrue(result.isPresent());
        assertEquals(visit, result.get());
    }

    @Test
    void testFindAccommodation_MultipleCandidates_SameDuration_PickClosestToEnd() {
        // Create two places
        SignificantPlace place1 = new SignificantPlace(1L, "Home1", "123 Main St", "City", "US", 40.0, -74.0, null, HOME, ZoneId.of("America/New_York"), true, 1L);
        SignificantPlace place2 = new SignificantPlace(2L, "Home2", "456 Elm St", "City", "US", 40.1, -74.1, null, HOME, ZoneId.of("America/New_York"), true, 1L);

        // Create visits with same duration (8 hours sleeping), but different end times
        // Visit1 ends closer to memoryEnd
        Instant start1 = Instant.parse("2023-10-01T22:00:00Z");
        Instant end1 = Instant.parse("2023-10-02T06:00:00Z"); // Ends at 06:00, close to memoryEnd
        ProcessedVisit visit1 = new ProcessedVisit(1L, place1, start1, end1, 28800L, 1L);

        // Visit2 ends farther from memoryEnd
        Instant start2 = Instant.parse("2023-10-01T22:00:00Z");
        Instant end2 = Instant.parse("2023-10-02T04:00:00Z"); // Ends at 04:00, farther
        ProcessedVisit visit2 = new ProcessedVisit(2L, place2, start2, end2, 28800L, 1L);

        Instant memoryStart = Instant.parse("2023-10-01T00:00:00Z");
        Instant memoryEnd = Instant.parse("2023-10-02T12:00:00Z");

        Optional<ProcessedVisit> result = homeDetectionService.findAccommodation(List.of(visit1, visit2), memoryStart, memoryEnd);

        assertTrue(result.isPresent());
        assertEquals(visit1, result.get()); // Should pick visit1 as it's closer to memoryEnd
    }

    @Test
    void testFindAccommodation_MultipleCandidates_DifferentDurations() {
        // Similar setup, but place1 has higher duration
        SignificantPlace place1 = new SignificantPlace(1L, "Home1", "123 Main St", "City", "US", 40.0, -74.0, null, HOME, ZoneId.of("America/New_York"), true, 1L);
        SignificantPlace place2 = new SignificantPlace(2L, "Home2", "456 Elm St", "City", "US", 40.1, -74.1, null, HOME, ZoneId.of("America/New_York"), true, 1L);

        // Visit1: higher duration
        Instant start1 = Instant.parse("2023-10-01T20:00:00Z");
        Instant end1 = Instant.parse("2023-10-02T08:00:00Z");
        ProcessedVisit visit1 = new ProcessedVisit(1L, place1, start1, end1, 43200L, 1L);

        // Visit2: lower duration
        Instant start2 = Instant.parse("2023-10-01T22:00:00Z");
        Instant end2 = Instant.parse("2023-10-02T06:00:00Z");
        ProcessedVisit visit2 = new ProcessedVisit(2L, place2, start2, end2, 28800L, 1L);

        Instant memoryStart = Instant.parse("2023-10-01T00:00:00Z");
        Instant memoryEnd = Instant.parse("2023-10-02T12:00:00Z");

        Optional<ProcessedVisit> result = homeDetectionService.findAccommodation(List.of(visit1, visit2), memoryStart, memoryEnd);

        assertTrue(result.isPresent());
        assertEquals(visit1, result.get()); // Should pick the one with higher duration
    }

    @Test
    void testFindAccommodation_NoVisits() {
        Instant memoryStart = Instant.parse("2023-10-01T00:00:00Z");
        Instant memoryEnd = Instant.parse("2023-10-02T12:00:00Z");

        Optional<ProcessedVisit> result = homeDetectionService.findAccommodation(List.of(), memoryStart, memoryEnd);

        assertFalse(result.isPresent());
    }

    @Test
    void testFindAccommodation_VisitsOutsideMemoryRange() {
        SignificantPlace place = new SignificantPlace(1L, "Home", "123 Main St", "City", "US", 40.0, -74.0, null, HOME, ZoneId.of("America/New_York"), true, 1L);

        // Visit completely outside memory range
        Instant start = Instant.parse("2023-09-30T20:00:00Z");
        Instant end = Instant.parse("2023-09-30T22:00:00Z");
        ProcessedVisit visit = new ProcessedVisit(1L, place, start, end, 7200L, 1L);

        Instant memoryStart = Instant.parse("2023-10-01T00:00:00Z");
        Instant memoryEnd = Instant.parse("2023-10-02T12:00:00Z");

        Optional<ProcessedVisit> result = homeDetectionService.findAccommodation(List.of(visit), memoryStart, memoryEnd);

        assertFalse(result.isPresent());
    }
}
