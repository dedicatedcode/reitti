package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class ProcessedVisitJdbcServiceTest {

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private SignificantPlaceJdbcService placeJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;
    private SignificantPlace testPlace;
    private SignificantPlace anotherPlace;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
        
        testPlace = createTestPlace("Home", 53.863149, 10.700927);
        anotherPlace = createTestPlace("Work", 53.864149, 10.701927);
    }

    @Test
    void create_ShouldCreateProcessedVisit() {
        // Given
        Instant startTime = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant endTime = Instant.now().minus(1, ChronoUnit.HOURS);
        ProcessedVisit visit = new ProcessedVisit(testPlace, startTime, endTime, 3600L);

        // When
        ProcessedVisit created = processedVisitJdbcService.create(testUser, visit);

        // Then
        assertNotNull(created.getId());
        assertEquals(1L, created.getVersion());
        assertEquals(testPlace.getId(), created.getPlace().getId());
        assertEquals(startTime, created.getStartTime());
        assertEquals(endTime, created.getEndTime());
        assertEquals(3600L, created.getDurationSeconds());
    }

    @Test
    void findById_WithExistingId_ShouldReturnVisit() {
        // Given
        ProcessedVisit visit = createTestVisit(testPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);

        // When
        Optional<ProcessedVisit> found = processedVisitJdbcService.findById(visit.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(visit.getId(), found.get().getId());
        assertEquals(testPlace.getId(), found.get().getPlace().getId());
    }

    @Test
    void findById_WithNonExistingId_ShouldReturnEmpty() {
        // When
        Optional<ProcessedVisit> found = processedVisitJdbcService.findById(999L);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    void findByUser_ShouldReturnAllUserVisits() {
        // Given
        User anotherUser = testingService.randomUser();
        createTestVisit(testPlace, Instant.now().minus(3, ChronoUnit.HOURS), Instant.now().minus(2, ChronoUnit.HOURS), 3600L);
        createTestVisit(anotherPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);
        
        // Create visit for another user (should not be returned)
        SignificantPlace anotherUserPlace = createTestPlaceForUser(anotherUser, "Other Place", 53.865149, 10.702927);
        processedVisitJdbcService.create(anotherUser, new ProcessedVisit(anotherUserPlace, Instant.now().minus(30, ChronoUnit.MINUTES), Instant.now(), 1800L));

        // When
        List<ProcessedVisit> visits = processedVisitJdbcService.findByUser(testUser);

        // Then
        assertEquals(2, visits.size());
        assertTrue(visits.stream().allMatch(v -> v.getPlace().getId().equals(testPlace.getId()) || v.getPlace().getId().equals(anotherPlace.getId())));
    }

    @Test
    void findByUserAndTimeOverlap_ShouldReturnOverlappingVisits() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        
        // Visit 1: 4-3 hours ago (should overlap)
        createTestVisit(testPlace, baseTime, baseTime.plus(1, ChronoUnit.HOURS), 3600L);
        
        // Visit 2: 2-1 hours ago (should overlap)
        createTestVisit(anotherPlace, baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS), 3600L);
        
        // Visit 3: 6-5 hours ago (should not overlap)
        createTestVisit(testPlace, baseTime.minus(2, ChronoUnit.HOURS), baseTime.minus(1, ChronoUnit.HOURS), 3600L);

        // When - query for overlap with 3.5-1.5 hours ago
        List<ProcessedVisit> visits = processedVisitJdbcService.findByUserAndTimeOverlap(
            testUser, 
            baseTime.plus(30, ChronoUnit.MINUTES), 
            baseTime.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES)
        );

        // Then
        assertEquals(2, visits.size());
    }

    @Test
    void findByUserAndId_WithExistingVisit_ShouldReturnVisit() {
        // Given
        ProcessedVisit visit = createTestVisit(testPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);

        // When
        Optional<ProcessedVisit> found = processedVisitJdbcService.findByUserAndId(testUser, visit.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(visit.getId(), found.get().getId());
    }

    @Test
    void findByUserAndId_WithDifferentUser_ShouldReturnEmpty() {
        // Given
        User anotherUser = testingService.randomUser();
        ProcessedVisit visit = createTestVisit(testPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);

        // When
        Optional<ProcessedVisit> found = processedVisitJdbcService.findByUserAndId(anotherUser, visit.getId());

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    void findTopPlacesByStayTimeWithLimit_ShouldReturnTopPlaces() {
        // Given
        // Create multiple visits to testPlace (total 7200 seconds)
        createTestVisit(testPlace, Instant.now().minus(4, ChronoUnit.HOURS), Instant.now().minus(3, ChronoUnit.HOURS), 3600L);
        createTestVisit(testPlace, Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS), 3600L);
        
        // Create one visit to anotherPlace (total 1800 seconds)
        createTestVisit(anotherPlace, Instant.now().minus(30, ChronoUnit.MINUTES), Instant.now(), 1800L);

        // When
        List<Object[]> topPlaces = processedVisitJdbcService.findTopPlacesByStayTimeWithLimit(testUser, 10);

        // Then
        assertEquals(2, topPlaces.size());
        
        // First place should be testPlace with more stay time
        Object[] firstPlace = topPlaces.get(0);
        assertEquals("Home", firstPlace[0]);
        assertEquals(7200L, firstPlace[1]);
        assertEquals(2L, firstPlace[2]);
        
        // Second place should be anotherPlace
        Object[] secondPlace = topPlaces.get(1);
        assertEquals("Work", secondPlace[0]);
        assertEquals(1800L, secondPlace[1]);
        assertEquals(1L, secondPlace[2]);
    }

    @Test
    void findTopPlacesByStayTimeWithLimit_WithTimeRange_ShouldReturnFilteredPlaces() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        
        // Visit within range
        createTestVisit(testPlace, baseTime, baseTime.plus(1, ChronoUnit.HOURS), 3600L);
        
        // Visit outside range
        createTestVisit(anotherPlace, baseTime.minus(2, ChronoUnit.HOURS), baseTime.minus(1, ChronoUnit.HOURS), 3600L);

        // When
        List<Object[]> topPlaces = processedVisitJdbcService.findTopPlacesByStayTimeWithLimit(
            testUser, 
            baseTime.minus(30, ChronoUnit.MINUTES), 
            baseTime.plus(2, ChronoUnit.HOURS), 
            10
        );

        // Then
        assertEquals(1, topPlaces.size());
        assertEquals("Home", topPlaces.get(0)[0]);
    }

    @Test
    void update_ShouldUpdateVisit() {
        // Given
        ProcessedVisit visit = createTestVisit(testPlace, Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS), 3600L);
        
        Instant newStartTime = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant newEndTime = Instant.now().minus(30, ChronoUnit.MINUTES);
        ProcessedVisit updatedVisit = new ProcessedVisit(
            visit.getId(),
            anotherPlace,
            newStartTime,
            newEndTime,
            5400L,
            visit.getVersion()
        );

        // When
        ProcessedVisit result = processedVisitJdbcService.update(updatedVisit);

        // Then
        assertEquals(anotherPlace.getId(), result.getPlace().getId());
        assertEquals(newStartTime, result.getStartTime());
        assertEquals(newEndTime, result.getEndTime());
        assertEquals(5400L, result.getDurationSeconds());
    }

    @Test
    void deleteAll_WithVisitList_ShouldDeleteVisits() {
        // Given
        ProcessedVisit visit1 = createTestVisit(testPlace, Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS), 3600L);
        ProcessedVisit visit2 = createTestVisit(anotherPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);
        ProcessedVisit visit3 = createTestVisit(testPlace, Instant.now().minus(30, ChronoUnit.MINUTES), Instant.now(), 1800L);

        // When
        processedVisitJdbcService.deleteAll(List.of(visit1, visit2));

        // Then
        assertTrue(processedVisitJdbcService.findById(visit1.getId()).isEmpty());
        assertTrue(processedVisitJdbcService.findById(visit2.getId()).isEmpty());
        assertTrue(processedVisitJdbcService.findById(visit3.getId()).isPresent());
    }

    @Test
    void deleteAll_WithEmptyList_ShouldNotThrow() {
        // When/Then
        assertDoesNotThrow(() -> processedVisitJdbcService.deleteAll(List.of()));
        assertDoesNotThrow(() -> processedVisitJdbcService.deleteAll(null));
    }

    @Test
    void bulkInsert_ShouldInsertMultipleVisits() {
        // Given
        Instant baseTime = Instant.now().minus(4, ChronoUnit.HOURS);
        List<ProcessedVisit> visitsToInsert = List.of(
            new ProcessedVisit(testPlace, baseTime, baseTime.plus(1, ChronoUnit.HOURS), 3600L),
            new ProcessedVisit(anotherPlace, baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS), 3600L)
        );

        // When
        List<ProcessedVisit> inserted = processedVisitJdbcService.bulkInsert(testUser, visitsToInsert);

        // Then
        assertEquals(2, inserted.size());
        assertTrue(inserted.stream().allMatch(v -> v.getId() != null));
        
        List<ProcessedVisit> allVisits = processedVisitJdbcService.findByUser(testUser);
        assertEquals(2, allVisits.size());
    }

    @Test
    void bulkInsert_WithEmptyList_ShouldReturnEmptyList() {
        // When
        List<ProcessedVisit> result = processedVisitJdbcService.bulkInsert(testUser, List.of());

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteAllForUser_ShouldDeleteOnlyUserVisits() {
        // Given
        User anotherUser = testingService.randomUser();
        SignificantPlace anotherUserPlace = createTestPlaceForUser(anotherUser, "Other Place", 53.865149, 10.702927);
        
        createTestVisit(testPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L);
        processedVisitJdbcService.create(anotherUser, new ProcessedVisit(anotherUserPlace, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), 3600L));

        // When
        processedVisitJdbcService.deleteAllForUser(testUser);

        // Then
        assertTrue(processedVisitJdbcService.findByUser(testUser).isEmpty());
        assertEquals(1, processedVisitJdbcService.findByUser(anotherUser).size());
    }

    @Test
    void getAffectedDays_ShouldReturnUniqueDates() {
        // Given
        Instant day1Start = Instant.parse("2023-12-01T10:00:00Z");
        Instant day1End = Instant.parse("2023-12-01T15:00:00Z");
        Instant day2Start = Instant.parse("2023-12-02T09:00:00Z");
        Instant day2End = Instant.parse("2023-12-02T17:00:00Z");
        
        createTestVisit(testPlace, day1Start, day1End, 18000L);
        createTestVisit(testPlace, day2Start, day2End, 28800L);
        createTestVisit(anotherPlace, day1Start, day1End, 18000L);

        // When
        List<LocalDate> affectedDays = processedVisitJdbcService.getAffectedDays(List.of(testPlace, anotherPlace));

        // Then
        assertEquals(2, affectedDays.size());
        assertTrue(affectedDays.contains(LocalDate.of(2023, 12, 1)));
        assertTrue(affectedDays.contains(LocalDate.of(2023, 12, 2)));
    }

    @Test
    void getAffectedDays_WithEmptyPlaceList_ShouldReturnEmptyList() {
        // When
        List<LocalDate> affectedDays = processedVisitJdbcService.getAffectedDays(List.of());

        // Then
        assertTrue(affectedDays.isEmpty());
    }

    private ProcessedVisit createTestVisit(SignificantPlace place, Instant startTime, Instant endTime, Long duration) {
        ProcessedVisit visit = new ProcessedVisit(place, startTime, endTime, duration);
        return processedVisitJdbcService.create(testUser, visit);
    }

    private SignificantPlace createTestPlace(String name, double latitude, double longitude) {
        return createTestPlaceForUser(testUser, name, latitude, longitude);
    }

    private SignificantPlace createTestPlaceForUser(User user, String name, double latitude, double longitude) {
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
}
