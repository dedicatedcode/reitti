package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@IntegrationTest
class DataCleanupServiceTest {

    @Autowired
    private DataCleanupService dataCleanupService;

    @Autowired
    private TripJdbcService tripJdbcService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private SignificantPlaceJdbcService placeJdbcService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;
    private User anotherUser;
    private SignificantPlace placeToRemove1;
    private SignificantPlace placeToRemove2;
    private SignificantPlace placeToKeep;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
        anotherUser = testingService.randomUser();

        // Create test places
        placeToRemove1 = createTestPlace(testUser, "Place to Remove 1", 53.863149, 10.700927);
        placeToRemove2 = createTestPlace(testUser, "Place to Remove 2", 53.864149, 10.701927);
        placeToKeep = createTestPlace(testUser, "Place to Keep", 53.865149, 10.702927);
    }

    @Test
    void cleanupForGeometryChange_ShouldRemoveTripsForSpecifiedPlaces() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        
        // Create visits for places
        ProcessedVisit visitToRemove1 = createTestVisit(placeToRemove1, baseTime, baseTime.plus(1, ChronoUnit.HOURS));
        ProcessedVisit visitToRemove2 = createTestVisit(placeToRemove2, baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS));
        ProcessedVisit visitToKeep = createTestVisit(placeToKeep, baseTime.plus(4, ChronoUnit.HOURS), baseTime.plus(5, ChronoUnit.HOURS));

        // Create trips between places
        Trip tripToRemove1 = createTestTrip(visitToRemove1, visitToRemove2);
        Trip tripToRemove2 = createTestTrip(visitToRemove2, visitToKeep);
        Trip tripToKeep = createTestTrip(visitToKeep, visitToKeep); // Self-trip or different scenario

        // When
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1, placeToRemove2);
        List<LocalDate> affectedDays = List.of(LocalDate.now());
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Trips involving removed places should be deleted
        assertTrue(tripJdbcService.findById(tripToRemove1.getId()).isEmpty());
        assertTrue(tripJdbcService.findById(tripToRemove2.getId()).isEmpty());
        
        // Trips not involving removed places should remain
        assertTrue(tripJdbcService.findById(tripToKeep.getId()).isPresent());
    }

    @Test
    void cleanupForGeometryChange_ShouldRemoveVisitsForSpecifiedPlaces() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        
        ProcessedVisit visitToRemove1 = createTestVisit(placeToRemove1, baseTime, baseTime.plus(1, ChronoUnit.HOURS));
        ProcessedVisit visitToRemove2 = createTestVisit(placeToRemove2, baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS));
        ProcessedVisit visitToKeep = createTestVisit(placeToKeep, baseTime.plus(4, ChronoUnit.HOURS), baseTime.plus(5, ChronoUnit.HOURS));

        // When
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1, placeToRemove2);
        List<LocalDate> affectedDays = List.of(LocalDate.now());
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Visits for removed places should be deleted
        assertTrue(processedVisitJdbcService.findById(visitToRemove1.getId()).isEmpty());
        assertTrue(processedVisitJdbcService.findById(visitToRemove2.getId()).isEmpty());
        
        // Visits for kept places should remain
        assertTrue(processedVisitJdbcService.findById(visitToKeep.getId()).isPresent());
    }

    @Test
    void cleanupForGeometryChange_ShouldNotRemoveVisitsForPlacesNotInRemovalList() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        
        // Create visits for all places
        ProcessedVisit visitToRemove = createTestVisit(placeToRemove1, baseTime, baseTime.plus(1, ChronoUnit.HOURS));
        ProcessedVisit visitToKeep1 = createTestVisit(placeToKeep, baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS));
        ProcessedVisit visitToKeep2 = createTestVisit(placeToKeep, baseTime.plus(4, ChronoUnit.HOURS), baseTime.plus(5, ChronoUnit.HOURS));
        
        // Create visit for another user (should not be affected)
        SignificantPlace anotherUserPlace = createTestPlace(anotherUser, "Another User Place", 53.866149, 10.703927);
        ProcessedVisit anotherUserVisit = createTestVisit(anotherUserPlace, baseTime, baseTime.plus(1, ChronoUnit.HOURS));

        // When - only remove placeToRemove1
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1);
        List<LocalDate> affectedDays = List.of(LocalDate.now());
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Only visit for removed place should be deleted
        assertTrue(processedVisitJdbcService.findById(visitToRemove.getId()).isEmpty());
        
        // Visits for places not in removal list should remain
        assertTrue(processedVisitJdbcService.findById(visitToKeep1.getId()).isPresent());
        assertTrue(processedVisitJdbcService.findById(visitToKeep2.getId()).isPresent());
        
        // Another user's visits should not be affected
        assertTrue(processedVisitJdbcService.findById(anotherUserVisit.getId()).isPresent());
    }

    @Test
    void cleanupForGeometryChange_ShouldRemoveSpecifiedPlaces() {
        // Given
        SignificantPlace anotherUserPlace = createTestPlace(anotherUser, "Another User Place", 53.866149, 10.703927);

        // When
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1, placeToRemove2);
        List<LocalDate> affectedDays = List.of(LocalDate.now());
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Specified places should be deleted
        assertTrue(placeJdbcService.findById(placeToRemove1.getId()).isEmpty());
        assertTrue(placeJdbcService.findById(placeToRemove2.getId()).isEmpty());
        
        // Places not in removal list should remain
        assertTrue(placeJdbcService.findById(placeToKeep.getId()).isPresent());
        
        // Another user's places should not be affected
        assertTrue(placeJdbcService.findById(anotherUserPlace.getId()).isPresent());
    }

    @Test
    void cleanupForGeometryChange_ShouldMarkRawLocationPointsAsUnprocessedForAffectedDays() {
        // Given
        LocalDate day1 = LocalDate.of(2023, 12, 1);
        LocalDate day2 = LocalDate.of(2023, 12, 2);
        LocalDate day3 = LocalDate.of(2023, 12, 3);
        
        Instant day1Time = day1.atStartOfDay().toInstant(ZoneOffset.UTC).plus(10, ChronoUnit.HOURS);
        Instant day2Time = day2.atStartOfDay().toInstant(ZoneOffset.UTC).plus(10, ChronoUnit.HOURS);
        Instant day3Time = day3.atStartOfDay().toInstant(ZoneOffset.UTC).plus(10, ChronoUnit.HOURS);

        // Create processed points on different days
        RawLocationPoint pointDay1 = createProcessedPoint(testUser, day1Time);
        RawLocationPoint pointDay2 = createProcessedPoint(testUser, day2Time);
        RawLocationPoint pointDay3 = createProcessedPoint(testUser, day3Time);
        
        // Create point for another user (should not be affected)
        RawLocationPoint anotherUserPoint = createProcessedPoint(anotherUser, day1Time);

        // Verify all points are initially processed
        assertTrue(findPointById(pointDay1.getId()).isProcessed());
        assertTrue(findPointById(pointDay2.getId()).isProcessed());
        assertTrue(findPointById(pointDay3.getId()).isProcessed());
        assertTrue(findPointById(anotherUserPoint.getId()).isProcessed());

        // When - cleanup with day1 and day2 as affected days
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1);
        List<LocalDate> affectedDays = List.of(day1, day2);
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Points on affected days should be marked as unprocessed
        assertFalse(findPointById(pointDay1.getId()).isProcessed());
        assertFalse(findPointById(pointDay2.getId()).isProcessed());
        
        // Points on unaffected days should remain processed
        assertTrue(findPointById(pointDay3.getId()).isProcessed());
        
        // Another user's points should not be affected
        assertTrue(findPointById(anotherUserPoint.getId()).isProcessed());
    }

    @Test
    void cleanupForGeometryChange_WithEmptyPlacesList_ShouldOnlyMarkPointsAsUnprocessed() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        ProcessedVisit visit = createTestVisit(placeToKeep, baseTime, baseTime.plus(1, ChronoUnit.HOURS));
        RawLocationPoint point = createProcessedPoint(testUser, baseTime);

        // When
        List<SignificantPlace> placesToRemove = List.of(); // Empty list
        List<LocalDate> affectedDays = List.of(LocalDate.now());
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Visit should remain (no places to remove)
        assertTrue(processedVisitJdbcService.findById(visit.getId()).isPresent());
        
        // Place should remain
        assertTrue(placeJdbcService.findById(placeToKeep.getId()).isPresent());
        
        // Point should be marked as unprocessed
        assertFalse(findPointById(point.getId()).isProcessed());
    }

    @Test
    void cleanupForGeometryChange_WithEmptyAffectedDays_ShouldRemovePlacesButNotMarkPoints() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        ProcessedVisit visitToRemove = createTestVisit(placeToRemove1, baseTime, baseTime.plus(1, ChronoUnit.HOURS));
        RawLocationPoint point = createProcessedPoint(testUser, baseTime);

        // When
        List<SignificantPlace> placesToRemove = List.of(placeToRemove1);
        List<LocalDate> affectedDays = List.of(); // Empty list
        dataCleanupService.cleanupForGeometryChange(testUser, placesToRemove, affectedDays);

        // Then
        // Visit should be removed
        assertTrue(processedVisitJdbcService.findById(visitToRemove.getId()).isEmpty());
        
        // Place should be removed
        assertTrue(placeJdbcService.findById(placeToRemove1.getId()).isEmpty());
        
        // Point should remain processed (no affected days)
        assertTrue(findPointById(point.getId()).isProcessed());
    }

    private SignificantPlace createTestPlace(User user, String name, double latitude, double longitude) {
        return placeJdbcService.create(user.getId(), new SignificantPlace(
            null,
            name,
            null,
            null,
            null,
            latitude,
            longitude,
            List.of(),
            ZoneId.systemDefault(),
            0L
        ));
    }

    private ProcessedVisit createTestVisit(SignificantPlace place, Instant startTime, Instant endTime) {
        long duration = ChronoUnit.SECONDS.between(startTime, endTime);
        ProcessedVisit visit = new ProcessedVisit(place, startTime, endTime, duration);
        return processedVisitJdbcService.create(testUser, visit);
    }

    private Trip createTestTrip(ProcessedVisit startVisit, ProcessedVisit endVisit) {
        long duration = ChronoUnit.SECONDS.between(startVisit.getEndTime(), endVisit.getStartTime());
        Trip trip = new Trip(
            null,
            startVisit.getEndTime(),
            endVisit.getStartTime(),
            duration,
            1000.0, // estimatedDistanceMeters
            1200.0, // travelledDistanceMeters
            null, // transportModeInferred
            startVisit,
            endVisit,
            List.of(), // rawLocationPoints
            1L
        );
        return tripJdbcService.create(testUser, trip);
    }

    private RawLocationPoint createProcessedPoint(User user, Instant timestamp) {
        RawLocationPoint point = new RawLocationPoint(
            null,
            timestamp,
            new GeoPoint(53.863149, 10.700927),
            10.0,
            null,
            false, // will be set to true after creation
            false,
            false,
            1L
        );
        
        RawLocationPoint created = rawLocationPointJdbcService.create(user, point);
        
        // Mark as processed
        RawLocationPoint processed = new RawLocationPoint(
            created.getId(),
            created.getTimestamp(),
            created.getGeom(),
            created.getAccuracyMeters(),
            created.getElevationMeters(),
            true, // processed = true
            created.isSynthetic(),
            created.isIgnored(),
            created.getVersion()
        );
        
        return rawLocationPointJdbcService.update(processed);
    }

    private RawLocationPoint findPointById(Long id) {
        return rawLocationPointJdbcService.findById(id)
            .orElseThrow(() -> new RuntimeException("Point not found: " + id));
    }
}
