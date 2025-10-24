package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.VisitDetectionParametersJdbcService;
import com.dedicatedcode.reitti.service.processing.TransportModeJdbcService;
import com.dedicatedcode.reitti.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;

    @Autowired
    private VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;

    @Autowired
    private TransportModeJdbcService transportModeJdbcService;

    @Test
    void shouldCreateUserWithExternalIdAndDefaultSettings() {
        // When
        User user = userService.createNewUser(
            "testuser",
            "Test User",
            "external123",
            "https://example.com/profile.jpg"
        );

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getExternalId()).isEqualTo("external123");
        assertThat(user.getProfileUrl()).isEqualTo("https://example.com/profile.jpg");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getPassword()).isEmpty();

        // Verify default visit detection parameters
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).hasSize(1);
        
        DetectionParameter param = detectionParams.get(0);
        assertThat(param.getVisitDetection().getMinDurationSeconds()).isEqualTo(300);
        assertThat(param.getVisitDetection().getMaxDistanceMeters()).isEqualTo(100);
        assertThat(param.getVisitDetection().getMinPointsInCluster()).isEqualTo(5);
        assertThat(param.getVisitDetection().getMaxTimeGapSeconds()).isEqualTo(330);
        
        assertThat(param.getVisitMerging().getMaxTimeGapHours()).isEqualTo(48);
        assertThat(param.getVisitMerging().getMaxDistanceMeters()).isEqualTo(200);
        assertThat(param.getVisitMerging().getMinDurationSeconds()).isEqualTo(300);

        // Verify default transport mode configurations
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).hasSize(4);
        
        // Should be sorted by maxKmh, nulls last
        assertThat(transportConfigs.get(0).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(transportConfigs.get(0).maxKmh()).isEqualTo(7.0);
        
        assertThat(transportConfigs.get(1).mode()).isEqualTo(TransportMode.CYCLING);
        assertThat(transportConfigs.get(1).maxKmh()).isEqualTo(20.0);
        
        assertThat(transportConfigs.get(2).mode()).isEqualTo(TransportMode.DRIVING);
        assertThat(transportConfigs.get(2).maxKmh()).isEqualTo(120.0);
        
        assertThat(transportConfigs.get(3).mode()).isEqualTo(TransportMode.WALKING);
        assertThat(transportConfigs.get(3).maxKmh()).isNull();
    }

    @Test
    void shouldCreateUserWithPasswordAndCustomSettings() {
        // When
        User user = userService.createNewUser(
            "adminuser",
            "Admin User",
            "password123",
            Role.ADMIN,
            UnitSystem.IMPERIAL,
            true,
            "en",
            52.5200,
            13.4050,
            "Europe/Berlin",
            TimeDisplayMode.TWELVE_HOUR
        );

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("adminuser");
        assertThat(user.getDisplayName()).isEqualTo("Admin User");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPassword()).isNotEmpty();
        assertThat(user.getPassword()).isNotEqualTo("password123"); // Should be encoded

        // Verify user settings were created
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings.getUnitSystem()).isEqualTo(UnitSystem.IMPERIAL);
        assertThat(settings.isPreferColoredMap()).isTrue();
        assertThat(settings.getSelectedLanguage()).isEqualTo("en");
        assertThat(settings.getHomeLatitude()).isEqualTo(52.5200);
        assertThat(settings.getHomeLongitude()).isEqualTo(13.4050);
        assertThat(settings.getTimeZoneOverride()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(settings.getTimeDisplayMode()).isEqualTo(TimeDisplayMode.TWELVE_HOUR);

        // Verify default parameters were also created
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).hasSize(1);

        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).hasSize(4);
    }

    @Test
    void shouldCreateUserWithNullTimezoneOverride() {
        // When
        User user = userService.createNewUser(
            "usernotz",
            "User No TZ",
            "password123",
            Role.USER,
            UnitSystem.METRIC,
            false,
            "de",
            null,
            null,
            null,
            TimeDisplayMode.TWENTY_FOUR_HOUR
        );

        // Then
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings.getTimeZoneOverride()).isNull();
        assertThat(settings.getHomeLatitude()).isNull();
        assertThat(settings.getHomeLongitude()).isNull();
    }

    @Test
    void shouldDeleteUserAndAllRelatedData() {
        // Given
        User user = testingService.randomUser();
        
        // Verify user has default data
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(detectionParams).isNotEmpty();
        assertThat(transportConfigs).isNotEmpty();

        // When
        userService.deleteUser(user);

        // Then - all related data should be deleted
        List<DetectionParameter> remainingParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        List<TransportModeConfig> remainingConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        
        assertThat(remainingParams).isEmpty();
        assertThat(remainingConfigs).isEmpty();
    }
}
