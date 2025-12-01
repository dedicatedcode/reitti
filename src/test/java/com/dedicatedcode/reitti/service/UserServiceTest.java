package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.VisitDetectionParametersJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
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

        // Verify default visit detection parameters are created
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).hasSize(1);

        // Verify default transport mode configurations are created
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).hasSize(4);
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
                Language.EN,
                52.5200,
                13.4050,
                "Europe/Berlin",
                TimeDisplayMode.DEFAULT,
                "#f1ba63"
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
        assertThat(settings.getSelectedLanguage()).isEqualTo(Language.EN);
        assertThat(settings.getHomeLatitude()).isEqualTo(52.5200);
        assertThat(settings.getHomeLongitude()).isEqualTo(13.4050);
        assertThat(settings.getTimeZoneOverride()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(settings.getTimeDisplayMode()).isEqualTo(TimeDisplayMode.DEFAULT);

        // Verify default parameters are created
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).isNotEmpty();

        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).isNotEmpty();
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
            Language.DE,
            null,
            null,
            null,
            TimeDisplayMode.DEFAULT,
            "#f1ba63"
        );

        // Then
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings.getTimeZoneOverride()).isNull();
    }

    @Test
    void shouldDeleteUserAndAllRelatedData() {
        // Given
        User user = userService.createNewUser(
            "deleteuser",
            "Delete User",
            "external456",
            "https://example.com/delete.jpg"
        );
        
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
