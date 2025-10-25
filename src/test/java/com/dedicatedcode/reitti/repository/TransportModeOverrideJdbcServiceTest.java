package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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
        Optional<TransportMode> override = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start, end);
        
        assertThat(override).isPresent();
        assertThat(override.get()).isEqualTo(TransportMode.CYCLING);
    }

    @Test
    void shouldReplaceExistingOverrideInTimeRange() {
        // Given
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        
        // Add first override
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.WALKING, start, end);
        
        // When - add another override in overlapping time range
        Instant newStart = start.plus(1, ChronoUnit.MINUTES);
        Instant newEnd = end.plus(1, ChronoUnit.MINUTES);
        transportModeOverrideJdbcService.addTransportModeOverride(testUser, TransportMode.DRIVING, newStart, newEnd);

        // Then - should only have the new override in the overlapping range
        Optional<TransportMode> override = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, newStart, newEnd);
        
        assertThat(override).isPresent();
        assertThat(override.get()).isEqualTo(TransportMode.DRIVING);
        
        Optional<TransportMode> originalOverride =
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start, end);
        assertThat(originalOverride.get()).isEqualTo(TransportMode.DRIVING);
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
        Optional<TransportMode> override1 = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start1, end1);
        Optional<TransportMode> override2 = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start2, end2);
        
        assertThat(override1).isPresent();
        assertThat(override1.get()).isEqualTo(TransportMode.WALKING);
        
        assertThat(override2).isPresent();
        assertThat(override2.get()).isEqualTo(TransportMode.CYCLING);
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
        Optional<TransportMode> override1 = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start1, end1);
        Optional<TransportMode> override2 = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start2, end2);
        
        assertThat(override1).isEmpty();
        assertThat(override2).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalForUserWithNoOverrides() {
        // Given
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now();

        // When
        Optional<TransportMode> override = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start, end);

        // Then
        assertThat(override).isEmpty();
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
        Optional<TransportMode> user1Override = 
            transportModeOverrideJdbcService.getTransportModeOverride(testUser, start, end);
        Optional<TransportMode> user2Override = 
            transportModeOverrideJdbcService.getTransportModeOverride(user2, start, end);
        
        assertThat(user1Override).isPresent();
        assertThat(user1Override.get()).isEqualTo(TransportMode.WALKING);
        
        assertThat(user2Override).isPresent();
        assertThat(user2Override.get()).isEqualTo(TransportMode.CYCLING);
    }
}
