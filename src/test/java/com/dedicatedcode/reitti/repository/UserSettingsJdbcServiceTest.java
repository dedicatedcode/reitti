package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class UserSettingsJdbcServiceTest {
    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;
    @Autowired
    private UserJdbcService userJdbcService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId1;

    @BeforeEach
    void setUp() {
        // Create test users
        testUserId1 = createTestUser();
    }

    private Long createTestUser() {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role) VALUES (?, ?, ?, ?)",
                username, "password", "Test User", Role.ADMIN.name()
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    @Test
    void findByUserId_WhenUserSettingsDoNotExist_ShouldReturnEmpty() {
        Optional<UserSettings> result = userSettingsJdbcService.findByUserId(testUserId1);
        
        assertThat(result).isEmpty();
    }

    @Test
    void save_WhenCreatingNewUserSettings_ShouldInsertAndReturnWithId() {
        UserSettings newSettings = new UserSettings(testUserId1, true, "fi", UnitSystem.METRIC, 60.1699, 24.9384, null, TimeDisplayMode.DEFAULT, Instant.now(), null);
        
        UserSettings savedSettings = userSettingsJdbcService.save(newSettings);
        
        assertThat(savedSettings.getUserId()).isEqualTo(testUserId1);
        assertThat(savedSettings.isPreferColoredMap()).isTrue();
        assertThat(savedSettings.getSelectedLanguage()).isEqualTo("fi");
        assertThat(savedSettings.getHomeLatitude()).isEqualTo(60.1699);
        assertThat(savedSettings.getHomeLongitude()).isEqualTo(24.9384);
        assertThat(savedSettings.getVersion()).isEqualTo(1L);
    }

    @Test
    void save_WhenUpdatingExistingUserSettings_ShouldUpdateAndIncrementVersion() {
        // Create initial settings
        UserSettings initialSettings = new UserSettings(testUserId1, false, "en", UnitSystem.METRIC, null, null, null, TimeDisplayMode.DEFAULT, Instant.now(), null);
        UserSettings savedSettings = userSettingsJdbcService.save(initialSettings);
        
        // Update settings
        UserSettings updatedSettings = new UserSettings(
                testUserId1,
                true, 
                "de",
                UnitSystem.IMPERIAL,
                52.5200,
                13.4050,
                null,
                TimeDisplayMode.DEFAULT,
                Instant.now(), savedSettings.getVersion());
        
        UserSettings result = userSettingsJdbcService.save(updatedSettings);
        
        assertThat(result.getUserId()).isEqualTo(testUserId1);
        assertThat(result.isPreferColoredMap()).isTrue();
        assertThat(result.getSelectedLanguage()).isEqualTo("de");
        assertThat(result.getUnitSystem()).isEqualTo(UnitSystem.IMPERIAL);
        assertThat(result.getHomeLatitude()).isEqualTo(52.5200);
        assertThat(result.getHomeLongitude()).isEqualTo(13.4050);
        assertThat(result.getVersion()).isEqualTo(2L);
    }

    @Test
    void findByUserId_WhenUserSettingsExist_ShouldReturnSettings() {
        // Create settings
        UserSettings newSettings = new UserSettings(testUserId1, true, "fr", UnitSystem.METRIC, 48.8566, 2.3522, null, TimeDisplayMode.DEFAULT, Instant.now(), null);
        userSettingsJdbcService.save(newSettings);
        
        Optional<UserSettings> result = userSettingsJdbcService.findByUserId(testUserId1);
        
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(testUserId1);
        assertThat(result.get().isPreferColoredMap()).isTrue();
        assertThat(result.get().getSelectedLanguage()).isEqualTo("fr");
        assertThat(result.get().getHomeLatitude()).isEqualTo(48.8566);
        assertThat(result.get().getHomeLongitude()).isEqualTo(2.3522);
    }

    @Test
    void getOrCreateDefaultSettings_WhenUserSettingsDoNotExist_ShouldCreateDefault() {
        UserSettings result = userSettingsJdbcService.getOrCreateDefaultSettings(testUserId1);
        
        assertThat(result.getUserId()).isEqualTo(testUserId1);
        assertThat(result.isPreferColoredMap()).isFalse();
        assertThat(result.getSelectedLanguage()).isEqualTo("en");
        assertThat(result.getUnitSystem()).isEqualTo(UnitSystem.METRIC);
        assertThat(result.getHomeLatitude()).isNull();
        assertThat(result.getHomeLongitude()).isNull();
        assertThat(result.getVersion()).isEqualTo(1L);
        
        // Verify it was actually saved to database
        Optional<UserSettings> fromDb = userSettingsJdbcService.findByUserId(testUserId1);
        assertThat(fromDb).isPresent();
    }

    @Test
    void getOrCreateDefaultSettings_WhenUserSettingsExist_ShouldReturnExisting() {
        // Create existing settings
        UserSettings existingSettings = new UserSettings(testUserId1, true, "fi", UnitSystem.METRIC, 60.1699, 24.9384, null, TimeDisplayMode.DEFAULT, Instant.now(), null);
        userSettingsJdbcService.save(existingSettings);
        
        UserSettings result = userSettingsJdbcService.getOrCreateDefaultSettings(testUserId1);
        
        assertThat(result.isPreferColoredMap()).isTrue();
        assertThat(result.getSelectedLanguage()).isEqualTo("fi");
        assertThat(result.getHomeLatitude()).isEqualTo(60.1699);
        assertThat(result.getHomeLongitude()).isEqualTo(24.9384);
    }

    @Test
    void defaultSettings_ShouldHaveCorrectValues() {
        UserSettings defaultSettings = UserSettings.defaultSettings(testUserId1);

        assertThat(defaultSettings.getUserId()).isEqualTo(testUserId1);
        assertThat(defaultSettings.isPreferColoredMap()).isFalse();
        assertThat(defaultSettings.getSelectedLanguage()).isEqualTo("en");
        assertThat(defaultSettings.getUnitSystem()).isEqualTo(UnitSystem.METRIC);
        assertThat(defaultSettings.getHomeLatitude()).isNull();
        assertThat(defaultSettings.getHomeLongitude()).isNull();
        assertThat(defaultSettings.getVersion()).isNull();
    }

    @Test
    void shouldUpdateNewestValue() {
        User user = userJdbcService.findById(testUserId1).orElseThrow();
        this.userSettingsJdbcService.save(UserSettings.defaultSettings(testUserId1));
        this.userSettingsJdbcService.updateNewestData(user, List.of());

        Instant latest = Instant.now();
        this.userSettingsJdbcService.updateNewestData(user, List.of(
                createLocationPoint(latest),
                createLocationPoint(latest.minus(1, ChronoUnit.MINUTES)),
                createLocationPoint(latest.minus(2, ChronoUnit.MINUTES))
        ));

        Optional<UserSettings> persisted = this.userSettingsJdbcService.findByUserId(user.getId());

        assertThat(persisted).isPresent();
        assertThat(persisted.get().getLatestData()).isEqualTo(latest.plusNanos(500).truncatedTo(ChronoUnit.MICROS));


        this.userSettingsJdbcService.updateNewestData(user, List.of(
                createLocationPoint(latest.minus(1, ChronoUnit.MINUTES)),
                createLocationPoint(latest.minus(1, ChronoUnit.MINUTES)),
                createLocationPoint(latest.minus(2, ChronoUnit.MINUTES))
        ));

        assertThat(this.userSettingsJdbcService.findByUserId(user.getId()).get().getLatestData()).isEqualTo(latest.plusNanos(500).truncatedTo(ChronoUnit.MICROS));
    }

    private LocationDataRequest.LocationPoint createLocationPoint(Instant timestamp) {
        LocationDataRequest.LocationPoint locationPoint = new LocationDataRequest.LocationPoint();
        locationPoint.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(timestamp));
        locationPoint.setLatitude(1.0);
        locationPoint.setLongitude(2.0);
        locationPoint.setAccuracyMeters(10.0);
        return locationPoint;
    }
}
