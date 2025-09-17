package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ConfigurationJdbcServiceTest {

    @Autowired
    private ConfigurationJdbcService configurationJdbcService;

    @Autowired
    private TestingService testingService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = testingService.admin();
    }

    @Test
    void shouldFindCurrentConfigurationForUser() {
        // When
        Optional<Configuration> result = configurationJdbcService.findCurrentConfigurationForUser(admin);
        
        // Then
        assertThat(result).isPresent();
        Configuration config = result.get();
        assertThat(config.validSince()).isNull(); // Default configuration from migration
        assertThat(config.visitDetection().searchDistanceInMeters()).isEqualTo(100);
        assertThat(config.visitDetection().minimumAdjacentPoints()).isEqualTo(5);
        assertThat(config.visitDetection().minimumStayTimeInSeconds()).isEqualTo(300);
        assertThat(config.visitDetection().maxMergeTimeBetweenSameStayPoints()).isEqualTo(300);
        assertThat(config.visitMerging().searchDurationInHours()).isEqualTo(48);
        assertThat(config.visitMerging().maxMergeTimeBetweenSameVisits()).isEqualTo(300);
        assertThat(config.visitMerging().minDistanceBetweenVisits()).isEqualTo(200);
    }

    @Test
    void shouldSaveNewConfiguration() {
        // Given
        Instant validSince = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
            150, 7, 600, 450
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
            72, 450, 300
        );
        Configuration newConfig = new Configuration(null, visitDetection, visitMerging, validSince);
        
        // When
        configurationJdbcService.saveConfiguration(admin, newConfig);
        
        // Then
        Optional<Configuration> result = configurationJdbcService.findCurrentConfigurationForUser(admin);
        assertThat(result).isPresent();
        Configuration savedConfig = result.get();
        assertThat(savedConfig.id()).isNotNull();
        assertThat(savedConfig.validSince()).isEqualTo(validSince);
        assertThat(savedConfig.visitDetection().searchDistanceInMeters()).isEqualTo(150);
        assertThat(savedConfig.visitDetection().minimumAdjacentPoints()).isEqualTo(7);
        assertThat(savedConfig.visitMerging().searchDurationInHours()).isEqualTo(72);
    }

    @Test
    void shouldFindAllConfigurationsForUser() {
        // Given - Save additional configurations with different valid_since dates
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        
        Configuration.VisitDetection visitDetection1 = new Configuration.VisitDetection(
            200, 10, 900, 600
        );
        Configuration.VisitMerging visitMerging1 = new Configuration.VisitMerging(
            24, 600, 400
        );
        Configuration pastConfig = new Configuration(null, visitDetection1, visitMerging1, past);
        
        Configuration.VisitDetection visitDetection2 = new Configuration.VisitDetection(
            250, 12, 1200, 800
        );
        Configuration.VisitMerging visitMerging2 = new Configuration.VisitMerging(
            96, 800, 500
        );
        Configuration futureConfig = new Configuration(null, visitDetection2, visitMerging2, future);
        
        configurationJdbcService.saveConfiguration(admin, pastConfig);
        configurationJdbcService.saveConfiguration(admin, futureConfig);
        
        // When
        List<Configuration> allConfigs = configurationJdbcService.findAllConfigurationsForUser(admin);
        
        // Then
        assertThat(allConfigs).hasSize(4); // Default + past + future
        assertThat(allConfigs.get(0).validSince()).isEqualTo(future); // Most recent first
        assertThat(allConfigs.get(1).validSince()).isEqualTo(past);
        assertThat(allConfigs.get(3).validSince()).isNull(); // Default config
    }

    @Test
    void shouldUpdateConfiguration() {
        // Given
        Optional<Configuration> currentConfig = configurationJdbcService.findCurrentConfigurationForUser(admin);
        assertThat(currentConfig).isPresent();
        
        Configuration.VisitDetection newVisitDetection = new Configuration.VisitDetection(
            300, 15, 1800, 900
        );
        Configuration.VisitMerging newVisitMerging = new Configuration.VisitMerging(
            120, 900, 600
        );
        Instant newValidSince = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Configuration updatedConfig = new Configuration(
            currentConfig.get().id(), 
            newVisitDetection, 
            newVisitMerging, 
            newValidSince
        );
        
        // When
        configurationJdbcService.updateConfiguration(updatedConfig);
        
        // Then
        Optional<Configuration> result = configurationJdbcService.findCurrentConfigurationForUser(admin);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(currentConfig.get().id());
        assertThat(result.get().validSince()).isEqualTo(newValidSince);
        assertThat(result.get().visitDetection().searchDistanceInMeters()).isEqualTo(300);
        assertThat(result.get().visitMerging().searchDurationInHours()).isEqualTo(120);
    }

    @Test
    void shouldDeleteConfiguration() {
        // Given
        Instant validSince = Instant.now();
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
            400, 20, 2400, 1200
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
            144, 1200, 800
        );
        Configuration configToDelete = new Configuration(null, visitDetection, visitMerging, validSince);

        int beforeAdd = configurationJdbcService.findAllConfigurationsForUser(admin).size();
        configurationJdbcService.saveConfiguration(admin, configToDelete);
        
        // Verify it was saved
        List<Configuration> beforeDelete = configurationJdbcService.findAllConfigurationsForUser(admin);
        assertThat(beforeDelete).hasSize(beforeAdd + 2 ); // Default + new one
        
        // Get the saved configuration to get its ID
        Configuration savedConfig = beforeDelete.stream()
            .filter(config -> config.validSince() != null && config.validSince().equals(validSince))
            .findFirst()
            .orElseThrow();
        
        // When
        configurationJdbcService.deleteConfiguration(savedConfig.id());
        
        // Then
        List<Configuration> afterDelete = configurationJdbcService.findAllConfigurationsForUser(admin);
        assertThat(afterDelete).hasSize(beforeAdd + 1); // Only default remains
        assertThat(afterDelete.get(0).validSince()).isNull(); // Default config
    }

    @Test
    void shouldReturnCurrentConfigurationBasedOnValidSince() {
        // Given - Create configurations with past, present, and future valid_since
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant present = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        
        Configuration.VisitDetection pastDetection = new Configuration.VisitDetection(
            100, 5, 300, 300
        );
        Configuration.VisitMerging pastMerging = new Configuration.VisitMerging(
            48, 300, 200
        );
        Configuration pastConfig = new Configuration(null, pastDetection, pastMerging, past);
        
        Configuration.VisitDetection presentDetection = new Configuration.VisitDetection(
            200, 10, 600, 600
        );
        Configuration.VisitMerging presentMerging = new Configuration.VisitMerging(
            72, 600, 400
        );
        Configuration presentConfig = new Configuration(null, presentDetection, presentMerging, present);
        
        Configuration.VisitDetection futureDetection = new Configuration.VisitDetection(
            300, 15, 900, 900
        );
        Configuration.VisitMerging futureMerging = new Configuration.VisitMerging(
            96, 900, 600
        );
        Configuration futureConfig = new Configuration(null, futureDetection, futureMerging, future);
        
        configurationJdbcService.saveConfiguration(admin, pastConfig);
        configurationJdbcService.saveConfiguration(admin, presentConfig);
        configurationJdbcService.saveConfiguration(admin, futureConfig);
        
        // When
        Optional<Configuration> currentConfig = configurationJdbcService.findCurrentConfigurationForUser(admin);
        
        // Then - Should return the most recent valid configuration (present, not future)
        assertThat(currentConfig).isPresent();
        assertThat(currentConfig.get().validSince()).isEqualTo(present);
        assertThat(currentConfig.get().visitDetection().searchDistanceInMeters()).isEqualTo(200);
    }
}
