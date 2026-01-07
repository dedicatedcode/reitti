package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class RawLocationPointJdbcServiceTest {

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
        anotherUser = testingService.randomUser();
    }

    @Test
    void markAllAsUnprocessedForUser_WithSpecificDates_ShouldOnlyMarkPointsOnThoseDays() {
        // Given
        Instant day1 = LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant day2 = LocalDate.of(2023, 12, 2).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant day3 = LocalDate.of(2023, 12, 3).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Create points on different days, all initially processed
        RawLocationPoint point1Day1 = createProcessedPoint(testUser, day1.plus(10, ChronoUnit.HOURS));
        RawLocationPoint point2Day1 = createProcessedPoint(testUser, day1.plus(14, ChronoUnit.HOURS));
        RawLocationPoint point1Day2 = createProcessedPoint(testUser, day2.plus(9, ChronoUnit.HOURS));
        RawLocationPoint point1Day3 = createProcessedPoint(testUser, day3.plus(11, ChronoUnit.HOURS));
        
        // Create point for another user (should not be affected)
        RawLocationPoint anotherUserPoint = createProcessedPoint(anotherUser, day1.plus(12, ChronoUnit.HOURS));

        // Verify all points are initially processed
        assertTrue(findPointById(point1Day1.getId()).isProcessed());
        assertTrue(findPointById(point2Day1.getId()).isProcessed());
        assertTrue(findPointById(point1Day2.getId()).isProcessed());
        assertTrue(findPointById(point1Day3.getId()).isProcessed());
        assertTrue(findPointById(anotherUserPoint.getId()).isProcessed());

        // When - mark only day 1 and day 2 as unprocessed
        List<LocalDate> affectedDays = List.of(
            LocalDate.of(2023, 12, 1),
            LocalDate.of(2023, 12, 2)
        );
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(testUser, affectedDays);

        // Then
        // Points on day 1 and day 2 should be unprocessed
        assertFalse(findPointById(point1Day1.getId()).isProcessed());
        assertFalse(findPointById(point2Day1.getId()).isProcessed());
        assertFalse(findPointById(point1Day2.getId()).isProcessed());
        
        // Point on day 3 should still be processed
        assertTrue(findPointById(point1Day3.getId()).isProcessed());
        
        // Another user's point should not be affected
        assertTrue(findPointById(anotherUserPoint.getId()).isProcessed());
    }

    @Test
    void markAllAsUnprocessedForUser_WithEmptyDateList_ShouldNotMarkAnyPoints() {
        // Given
        Instant day1 = LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        RawLocationPoint point = createProcessedPoint(testUser, day1.plus(10, ChronoUnit.HOURS));
        
        assertTrue(findPointById(point.getId()).isProcessed());

        // When
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(testUser, List.of());

        // Then
        assertTrue(findPointById(point.getId()).isProcessed());
    }

    @Test
    void markAllAsUnprocessedForUser_WithNonExistentDates_ShouldNotMarkAnyPoints() {
        // Given
        Instant day1 = LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        RawLocationPoint point = createProcessedPoint(testUser, day1.plus(10, ChronoUnit.HOURS));
        
        assertTrue(findPointById(point.getId()).isProcessed());

        // When - mark a different date
        List<LocalDate> affectedDays = List.of(LocalDate.of(2023, 12, 15));
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(testUser, affectedDays);

        // Then
        assertTrue(findPointById(point.getId()).isProcessed());
    }

    @Test
    void markAllAsUnprocessedForUser_WithPointsAlreadyUnprocessed_ShouldRemainUnprocessed() {
        // Given
        Instant day1 = LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        RawLocationPoint unprocessedPoint = createUnprocessedPoint(testUser, day1.plus(10, ChronoUnit.HOURS));
        RawLocationPoint processedPoint = createProcessedPoint(testUser, day1.plus(14, ChronoUnit.HOURS));
        
        assertFalse(findPointById(unprocessedPoint.getId()).isProcessed());
        assertTrue(findPointById(processedPoint.getId()).isProcessed());

        // When
        List<LocalDate> affectedDays = List.of(LocalDate.of(2023, 12, 1));
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(testUser, affectedDays);

        // Then
        assertFalse(findPointById(unprocessedPoint.getId()).isProcessed());
        assertFalse(findPointById(processedPoint.getId()).isProcessed());
    }

    @Test
    void markAllAsUnprocessedForUser_WithMultipleDaysAndUsers_ShouldOnlyAffectCorrectUserAndDays() {
        // Given
        Instant day1 = LocalDate.of(2023, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant day2 = LocalDate.of(2023, 12, 2).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant day3 = LocalDate.of(2023, 12, 3).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Test user points
        RawLocationPoint testUserDay1 = createProcessedPoint(testUser, day1.plus(10, ChronoUnit.HOURS));
        RawLocationPoint testUserDay2 = createProcessedPoint(testUser, day2.plus(10, ChronoUnit.HOURS));
        RawLocationPoint testUserDay3 = createProcessedPoint(testUser, day3.plus(10, ChronoUnit.HOURS));
        
        // Another user points
        RawLocationPoint anotherUserDay1 = createProcessedPoint(anotherUser, day1.plus(10, ChronoUnit.HOURS));
        RawLocationPoint anotherUserDay2 = createProcessedPoint(anotherUser, day2.plus(10, ChronoUnit.HOURS));

        // When - mark only day 1 and day 2 for test user
        List<LocalDate> affectedDays = List.of(
            LocalDate.of(2023, 12, 1),
            LocalDate.of(2023, 12, 2)
        );
        rawLocationPointJdbcService.markAllAsUnprocessedForUser(testUser, affectedDays);

        // Then
        // Test user's points on affected days should be unprocessed
        assertFalse(findPointById(testUserDay1.getId()).isProcessed());
        assertFalse(findPointById(testUserDay2.getId()).isProcessed());
        
        // Test user's point on unaffected day should remain processed
        assertTrue(findPointById(testUserDay3.getId()).isProcessed());
        
        // Another user's points should not be affected
        assertTrue(findPointById(anotherUserDay1.getId()).isProcessed());
        assertTrue(findPointById(anotherUserDay2.getId()).isProcessed());
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
            created.isInvalid(),
            created.getVersion()
        );
        
        return rawLocationPointJdbcService.update(processed);
    }

    private RawLocationPoint createUnprocessedPoint(User user, Instant timestamp) {
        RawLocationPoint point = new RawLocationPoint(
            null,
            timestamp,
            new GeoPoint(53.863149, 10.700927),
            10.0,
            null,
            false, // processed = false
            false,
            false,
            false,
            1L
        );
        
        return rawLocationPointJdbcService.create(user, point);
    }

    private RawLocationPoint findPointById(Long id) {
        return rawLocationPointJdbcService.findById(id)
            .orElseThrow(() -> new RuntimeException("Point not found: " + id));
    }
}
