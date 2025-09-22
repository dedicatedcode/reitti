package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class VisitDetectionParametersJdbcServiceTest {

    @Autowired
    private VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;

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
        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                100L, 5, 300L, 600L
        );
        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                24L, 1800L, 50L
        );
        DetectionParameter detectionParameter = new DetectionParameter(
                null, visitDetection, visitMerging, Instant.now(), false
        );

        // When
        visitDetectionParametersJdbcService.saveConfiguration(testUser, detectionParameter);

        // Then
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(detectionParameters).hasSize(1);

        DetectionParameter savedConfig = detectionParameters.getFirst();
        assertThat(savedConfig.getId()).isNotNull();
        assertThat(savedConfig.getVisitDetection().getSearchDistanceInMeters()).isEqualTo(100L);
        assertThat(savedConfig.getVisitDetection().getMinimumAdjacentPoints()).isEqualTo(5L);
        assertThat(savedConfig.getVisitDetection().getMinimumStayTimeInSeconds()).isEqualTo(300L);
        assertThat(savedConfig.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints()).isEqualTo(600L);
        assertThat(savedConfig.getVisitMerging().getSearchDurationInHours()).isEqualTo(24L);
        assertThat(savedConfig.getVisitMerging().getMaxMergeTimeBetweenSameVisits()).isEqualTo(1800L);
        assertThat(savedConfig.getVisitMerging().getMinDistanceBetweenVisits()).isEqualTo(50L);
        assertThat(savedConfig.getValidSince()).isNotNull();
    }

    @Test
    void shouldSaveConfigurationWithNullValidSince() {
        // Given
        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                200L, 3, 600L, 1200L
        );
        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                12L, 900L, 25L
        );
        DetectionParameter detectionParameter = new DetectionParameter(
                null, visitDetection, visitMerging, null, false
        );

        // When
        visitDetectionParametersJdbcService.saveConfiguration(testUser, detectionParameter);

        // Then
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(detectionParameters).hasSize(1);
        assertThat(detectionParameters.getFirst().getValidSince()).isNull();
    }

    @Test
    void shouldUpdateConfiguration() {
        // Given - save initial configuration
        DetectionParameter.VisitDetection initialVisitDetection = new DetectionParameter.VisitDetection(
                100L, 5, 300L, 600L
        );
        DetectionParameter.VisitMerging initialVisitMerging = new DetectionParameter.VisitMerging(
                24L, 1800L, 50L
        );
        DetectionParameter initialConfig = new DetectionParameter(
                null, initialVisitDetection, initialVisitMerging, Instant.now(), false
        );
        visitDetectionParametersJdbcService.saveConfiguration(testUser, initialConfig);

        List<DetectionParameter> savedConfigs = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        DetectionParameter savedConfig = savedConfigs.getFirst();

        // When - update the configuration
        DetectionParameter.VisitDetection updatedVisitDetection = new DetectionParameter.VisitDetection(
                150L, 7, 450L, 900L
        );
        DetectionParameter.VisitMerging updatedVisitMerging = new DetectionParameter.VisitMerging(
                48L, 3600L, 75L
        );
        Instant newValidSince = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);
        DetectionParameter updatedConfig = new DetectionParameter(
                savedConfig.getId(), updatedVisitDetection, updatedVisitMerging, newValidSince, false
        );
        visitDetectionParametersJdbcService.updateConfiguration(updatedConfig);

        // Then
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(detectionParameters).hasSize(1);

        DetectionParameter result = detectionParameters.getFirst();
        assertThat(result.getId()).isEqualTo(savedConfig.getId());
        assertThat(result.getVisitDetection().getSearchDistanceInMeters()).isEqualTo(150L);
        assertThat(result.getVisitDetection().getMinimumAdjacentPoints()).isEqualTo(7L);
        assertThat(result.getVisitDetection().getMinimumStayTimeInSeconds()).isEqualTo(450L);
        assertThat(result.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints()).isEqualTo(900L);
        assertThat(result.getVisitMerging().getSearchDurationInHours()).isEqualTo(48L);
        assertThat(result.getVisitMerging().getMaxMergeTimeBetweenSameVisits()).isEqualTo(3600L);
        assertThat(result.getVisitMerging().getMinDistanceBetweenVisits()).isEqualTo(75L);
        assertThat(result.getValidSince()).isEqualTo(newValidSince);
    }

    @Test
    void shouldDeleteConfiguration() {
        // Given - save configuration with validSince
        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                100L, 5, 300L, 600L
        );
        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                24L, 1800L, 50L
        );
        DetectionParameter detectionParameter = new DetectionParameter(
                null, visitDetection, visitMerging, Instant.now(), false
        );
        visitDetectionParametersJdbcService.saveConfiguration(testUser, detectionParameter);

        List<DetectionParameter> savedConfigs = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        Long configId = savedConfigs.getFirst().getId();

        // When
        visitDetectionParametersJdbcService.delete(configId);

        // Then
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(detectionParameters).isEmpty();
    }

    @Test
    void shouldNotDeleteConfigurationWithNullValidSince() {
        // Given - save configuration with null validSince
        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                100L, 5, 300L, 600L
        );
        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                24L, 1800L, 50L
        );
        DetectionParameter detectionParameter = new DetectionParameter(
                null, visitDetection, visitMerging, null, false
        );
        visitDetectionParametersJdbcService.saveConfiguration(testUser, detectionParameter);

        List<DetectionParameter> savedConfigs = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        Long configId = savedConfigs.getFirst().getId();

        // When
        visitDetectionParametersJdbcService.delete(configId);

        // Then - configuration should still exist because validSince is null
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);
        assertThat(detectionParameters).hasSize(1);
    }

    @Test
    void shouldFindMultipleConfigurationsOrderedByValidSince() {
        // Given
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant earlier = now.minusSeconds(3600);
        Instant later = now.plusSeconds(3600);

        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                100L, 5, 300L, 600L
        );
        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                24L, 1800L, 50L
        );

        // Save configurations in different order
        DetectionParameter config1 = new DetectionParameter(null, visitDetection, visitMerging, now, false);
        DetectionParameter config2 = new DetectionParameter(null, visitDetection, visitMerging, later, false);
        DetectionParameter config3 = new DetectionParameter(null, visitDetection, visitMerging, earlier, false);
        DetectionParameter config4 = new DetectionParameter(null, visitDetection, visitMerging, null, false);

        visitDetectionParametersJdbcService.saveConfiguration(testUser, config1);
        visitDetectionParametersJdbcService.saveConfiguration(testUser, config2);
        visitDetectionParametersJdbcService.saveConfiguration(testUser, config3);
        visitDetectionParametersJdbcService.saveConfiguration(testUser, config4);

        // When
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(testUser);

        // Then - should be ordered by validSince DESC NULLS LAST
        assertThat(detectionParameters).hasSize(4);
        assertThat(detectionParameters.get(0).getValidSince()).isEqualTo(later);
        assertThat(detectionParameters.get(1).getValidSince()).isEqualTo(now);
        assertThat(detectionParameters.get(2).getValidSince()).isEqualTo(earlier);
        assertThat(detectionParameters.get(3).getValidSince()).isNull();
    }

    @Test
    void shouldReturnEmptyListForUserWithNoConfigurations() {
        // Given
        User anotherUser = testingService.randomUser();

        // When
        List<DetectionParameter> detectionParameters = visitDetectionParametersJdbcService.findAllConfigurationsForUser(anotherUser);

        // Then
        assertThat(detectionParameters).isEmpty();
    }
}