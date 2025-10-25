package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TransportModeOverrideJdbcServiceTest {

    @Autowired
    private TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void shouldAddTransportModeOverride() {
        // Given
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now();
        TransportMode mode = TransportMode.CYCLING;

        // When
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, mode, start, end);

        // Then
        List<TransportModeOverrideJdbcService.TransportModeOverride> overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);
        
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).getTransportMode()).isEqualTo(TransportMode.CYCLING);
        
        // Time should be the middle between start and end
        Instant expectedMiddle = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2);
        assertThat(overrides.get(0).getTime()).isEqualTo(expectedMiddle);
    }

    @Test
    void shouldReplaceExistingOverrideInTimeRange() {
        // Given
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        
        // Add first override
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.WALKING, start, end);
        
        // When - add another override in overlapping time range
        Instant newStart = start.plus(30, ChronoUnit.MINUTES);
        Instant newEnd = end.plus(30, ChronoUnit.MINUTES);
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.DRIVING, newStart, newEnd);

        // Then - should only have the new override
        List<TransportModeOverrideJdbcService.TransportModeOverride> overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);
        
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).getTransportMode()).isEqualTo(TransportMode.DRIVING);
    }

    @Test
    void shouldAllowMultipleOverridesInDifferentTimeRanges() {
        // Given
        Instant start1 = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant end1 = Instant.now().minus(2, ChronoUnit.HOURS);
        
        Instant start2 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end2 = Instant.now();

        // When
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.WALKING, start1, end1);
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.CYCLING, start2, end2);

        // Then
        List<TransportModeOverrideJdbcService.TransportModeOverride> overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);
        
        assertThat(overrides).hasSize(2);
        // Should be ordered by time
        assertThat(overrides.get(0).getTransportMode()).isEqualTo(TransportMode.WALKING);
        assertThat(overrides.get(1).getTransportMode()).isEqualTo(TransportMode.CYCLING);
    }

    @Test
    void shouldDeleteAllTransportModeOverrides() {
        // Given
        Instant start1 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant end1 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant start2 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end2 = Instant.now();
        
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.WALKING, start1, end1);
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.CYCLING, start2, end2);

        // When
        transportModeOverrideJdbcService.deleteAllTransportModeOverrides(testUser);

        // Then
        List<TransportModeOverrideJdbcService.TransportModeOverride> overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);
        
        assertThat(overrides).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForUserWithNoOverrides() {
        // When
        List<TransportModeOverrideJdbcService.TransportModeOverride> overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);

        // Then
        assertThat(overrides).isEmpty();
    }

    @Test
    void shouldIsolateOverridesBetweenUsers() {
        // Given
        User user2 = testingService.randomUser();
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now();

        // When
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.WALKING, start, end);
        transportModeOverrideJdbcService.addTransportModeOverride(user2, TransportMode.CYCLING, start, end);

        // Then
        List<TransportModeOverrideJdbcService.TransportModeOverride> user1Overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(testUser);
        List<TransportModeOverrideJdbcService.TransportModeOverride> user2Overrides = 
            transportModeOverrideJdbcService.getTransportModeOverrides(user2);
        
        assertThat(user1Overrides).hasSize(1);
        assertThat(user1Overrides.get(0).getTransportMode()).isEqualTo(TransportMode.WALKING);
        
        assertThat(user2Overrides).hasSize(1);
        assertThat(user2Overrides.get(0).getTransportMode()).isEqualTo(TransportMode.CYCLING);
    }
}
