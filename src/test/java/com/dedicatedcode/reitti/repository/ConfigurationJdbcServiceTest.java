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

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ConfigurationJdbcServiceTest {

    @Autowired
    private ConfigurationJdbcService configurationJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void shouldSaveAndFindConfiguration() {
        // Given
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                100L, 5L, 300L, 600L
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                24L, 1800L, 50L
        );
        Configuration configuration = new Configuration(
                null, visitDetection, visitMerging, Instant.now()
        );

        // When
        configurationJdbcService.saveConfiguration(testUser, configuration);

        // Then
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(configurations).hasSize(1);

        Configuration savedConfig = configurations.getFirst();
        assertThat(savedConfig.id()).isNotNull();
        assertThat(savedConfig.visitDetection().searchDistanceInMeters()).isEqualTo(100L);
        assertThat(savedConfig.visitDetection().minimumAdjacentPoints()).isEqualTo(5L);
        assertThat(savedConfig.visitDetection().minimumStayTimeInSeconds()).isEqualTo(300L);
        assertThat(savedConfig.visitDetection().maxMergeTimeBetweenSameStayPoints()).isEqualTo(600L);
        assertThat(savedConfig.visitMerging().searchDurationInHours()).isEqualTo(24L);
        assertThat(savedConfig.visitMerging().maxMergeTimeBetweenSameVisits()).isEqualTo(1800L);
        assertThat(savedConfig.visitMerging().minDistanceBetweenVisits()).isEqualTo(50L);
        assertThat(savedConfig.validSince()).isNotNull();
    }

    @Test
    void shouldSaveConfigurationWithNullValidSince() {
        // Given
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                200L, 3L, 600L, 1200L
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                12L, 900L, 25L
        );
        Configuration configuration = new Configuration(
                null, visitDetection, visitMerging, null
        );

        // When
        configurationJdbcService.saveConfiguration(testUser, configuration);

        // Then
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(configurations).hasSize(1);
        assertThat(configurations.getFirst().validSince()).isNull();
    }

    @Test
    void shouldUpdateConfiguration() {
        // Given - save initial configuration
        Configuration.VisitDetection initialVisitDetection = new Configuration.VisitDetection(
                100L, 5L, 300L, 600L
        );
        Configuration.VisitMerging initialVisitMerging = new Configuration.VisitMerging(
                24L, 1800L, 50L
        );
        Configuration initialConfig = new Configuration(
                null, initialVisitDetection, initialVisitMerging, Instant.now()
        );
        configurationJdbcService.saveConfiguration(testUser, initialConfig);

        List<Configuration> savedConfigs = configurationJdbcService.findAllConfigurationsForUser(testUser);
        Configuration savedConfig = savedConfigs.getFirst();

        // When - update the configuration
        Configuration.VisitDetection updatedVisitDetection = new Configuration.VisitDetection(
                150L, 7L, 450L, 900L
        );
        Configuration.VisitMerging updatedVisitMerging = new Configuration.VisitMerging(
                48L, 3600L, 75L
        );
        Instant newValidSince = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
        Configuration updatedConfig = new Configuration(
                savedConfig.id(), updatedVisitDetection, updatedVisitMerging, newValidSince
        );
        configurationJdbcService.updateConfiguration(updatedConfig);

        // Then
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(configurations).hasSize(1);

        Configuration result = configurations.getFirst();
        assertThat(result.id()).isEqualTo(savedConfig.id());
        assertThat(result.visitDetection().searchDistanceInMeters()).isEqualTo(150L);
        assertThat(result.visitDetection().minimumAdjacentPoints()).isEqualTo(7L);
        assertThat(result.visitDetection().minimumStayTimeInSeconds()).isEqualTo(450L);
        assertThat(result.visitDetection().maxMergeTimeBetweenSameStayPoints()).isEqualTo(900L);
        assertThat(result.visitMerging().searchDurationInHours()).isEqualTo(48L);
        assertThat(result.visitMerging().maxMergeTimeBetweenSameVisits()).isEqualTo(3600L);
        assertThat(result.visitMerging().minDistanceBetweenVisits()).isEqualTo(75L);
        assertThat(result.validSince()).isEqualTo(newValidSince);
    }

    @Test
    void shouldDeleteConfiguration() {
        // Given - save configuration with validSince
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                100L, 5L, 300L, 600L
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                24L, 1800L, 50L
        );
        Configuration configuration = new Configuration(
                null, visitDetection, visitMerging, Instant.now()
        );
        configurationJdbcService.saveConfiguration(testUser, configuration);

        List<Configuration> savedConfigs = configurationJdbcService.findAllConfigurationsForUser(testUser);
        Long configId = savedConfigs.getFirst().id();

        // When
        configurationJdbcService.delete(configId);

        // Then
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(configurations).isEmpty();
    }

    @Test
    void shouldNotDeleteConfigurationWithNullValidSince() {
        // Given - save configuration with null validSince
        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                100L, 5L, 300L, 600L
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                24L, 1800L, 50L
        );
        Configuration configuration = new Configuration(
                null, visitDetection, visitMerging, null
        );
        configurationJdbcService.saveConfiguration(testUser, configuration);

        List<Configuration> savedConfigs = configurationJdbcService.findAllConfigurationsForUser(testUser);
        Long configId = savedConfigs.getFirst().id();

        // When
        configurationJdbcService.delete(configId);

        // Then - configuration should still exist because validSince is null
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(configurations).hasSize(1);
    }

    @Test
    void shouldFindMultipleConfigurationsOrderedByValidSince() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant earlier = now.minusSeconds(3600);
        Instant later = now.plusSeconds(3600);

        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                100L, 5L, 300L, 600L
        );
        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                24L, 1800L, 50L
        );

        // Save configurations in different order
        Configuration config1 = new Configuration(null, visitDetection, visitMerging, now);
        Configuration config2 = new Configuration(null, visitDetection, visitMerging, later);
        Configuration config3 = new Configuration(null, visitDetection, visitMerging, earlier);
        Configuration config4 = new Configuration(null, visitDetection, visitMerging, null);

        configurationJdbcService.saveConfiguration(testUser, config1);
        configurationJdbcService.saveConfiguration(testUser, config2);
        configurationJdbcService.saveConfiguration(testUser, config3);
        configurationJdbcService.saveConfiguration(testUser, config4);

        // When
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(testUser);

        // Then - should be ordered by validSince DESC NULLS LAST
        assertThat(configurations).hasSize(4);
        assertThat(configurations.get(0).validSince()).isEqualTo(later);
        assertThat(configurations.get(1).validSince()).isEqualTo(now);
        assertThat(configurations.get(2).validSince()).isEqualTo(earlier);
        assertThat(configurations.get(3).validSince()).isNull();
    }

    @Test
    void shouldReturnEmptyListForUserWithNoConfigurations() {
        // Given
        User anotherUser = testingService.randomUser();

        // When
        List<Configuration> configurations = configurationJdbcService.findAllConfigurationsForUser(anotherUser);

        // Then
        assertThat(configurations).isEmpty();
    }
}