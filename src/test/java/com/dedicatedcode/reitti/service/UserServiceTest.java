package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;

    @Autowired
    private VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;

    @Autowired
    private TransportModeJdbcService transportModeJdbcService;

    @Autowired
    private MqttIntegrationJdbcService mqttIntegrationJdbcService;

    @Autowired
    private SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;

    @Autowired
    private TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    @Autowired
    private OwnTracksRecorderIntegrationJdbcService ownTracksRecorderIntegrationJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private TripJdbcService tripJdbcService;

    @Autowired
    private GeocodingResponseJdbcService geocodingResponseJdbcService;

    @Autowired
    private TestingService testingService;

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


        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings).isNotNull();
        assertThat(settings.getHomeLatitude()).isNotNull().isNotEqualTo(0.0);
        assertThat(settings.getHomeLongitude()).isNotNull().isNotEqualTo(0.0);

        // Verify default visit detection parameters are created
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).hasSize(1);

        // Verify default transport mode configurations are created
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).hasSize(4);

        //Verify default MapStyleSetting
        assertEquals("Reitti",
                     this.jdbcTemplate.queryForObject("""
                                                              SELECT name FROM user_map_styles
                                                                          WHERE id = (SELECT active_style_id FROM user_map_style_settings WHERE user_id = ?)
                                                              """, String.class, user.getId()));


        assertEquals(1, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM devices WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(1, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM api_tokens WHERE user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void shouldSelectRandomCityOfHomeLatitudeAndLongitudeNotSpecified() {
        // When
        User user = userService.createNewUser(
                "adminuser3",
                "Admin User",
                "password123",
                Role.ADMIN,
                UnitSystem.IMPERIAL,
                Language.EN,
                null,
                null,
                "Europe/Berlin",
                TimeDisplayMode.DEFAULT,
                TimeMode.TWENTY_FOUR_HOUR,
                "#f1ba63"
        );

        // Verify user settings were created
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings).isNotNull();
        assertThat(settings.getHomeLatitude()).isNotNull().isNotEqualTo(0.0);
        assertThat(settings.getHomeLongitude()).isNotNull().isNotEqualTo(0.0);
    }

    @Test
    void shouldCreateUserWithPasswordAndCustomSettings() {
        // When
        User user = userService.createNewUser(
                "adminuser2",
                "Admin User",
                "password123",
                Role.ADMIN,
                UnitSystem.IMPERIAL,
                Language.EN,
                52.5200,
                13.4050,
                "Europe/Berlin",
                TimeDisplayMode.DEFAULT,
                TimeMode.TWENTY_FOUR_HOUR,
                "#f1ba63"
        );

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("adminuser2");
        assertThat(user.getDisplayName()).isEqualTo("Admin User");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPassword()).isNotEmpty();
        assertThat(user.getPassword()).isNotEqualTo("password123"); // Should be encoded

        // Verify user settings were created
        UserSettings settings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        assertThat(settings.getUnitSystem()).isEqualTo(UnitSystem.IMPERIAL);
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
            Language.DE,
            null,
            null,
            null,
            TimeDisplayMode.DEFAULT,
            TimeMode.TWENTY_FOUR_HOUR,
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
        Device device = testingService.findDefaultDevice(user);
        // Verify user has default data
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(detectionParams).isNotEmpty();
        assertThat(transportConfigs).isNotEmpty();

        this.mqttIntegrationJdbcService.save(user, MqttIntegration.empty()
                .withHost("localhost")
                .withIdentifier("identifier")
                .withTopic( "topic")
                .withPayloadType(PayloadType.OWNTRACKS)
                .withDeviceId(device.id()));
        SignificantPlace significantPlace = this.testingService.newSignificantPlace(user);
        this.significantPlaceOverrideJdbcService.insertOverride(user, significantPlace);

        this.transportModeOverrideJdbcService.addTransportModeOverride(user, TransportMode.WALKING, Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now());
        this.ownTracksRecorderIntegrationJdbcService.save(user,new OwnTracksRecorderIntegration(
                "http://localhost",
                "daniel",
                "1111",
                true,
                null,
                null,
                1L));
        // When
        userService.deleteUser(user);

        // Then - all related data should be deleted
        assertEquals(0, visitDetectionParametersJdbcService.findAllConfigurationsForUser(user).size());
        assertEquals(0, transportModeJdbcService.getTransportModeConfigs(user).size());

        assertEquals(0, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_map_style_settings WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mqtt_integrations WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owntracks_recorder_integration WHERE user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void shouldCreateLiveDataOnlyUserWithoutProcessingDefaults() {
        // When
        User user = userService.createNewUser(
                "livedataonlycreate",
                "Live Data Only Create",
                "password123",
                Role.USER,
                UnitSystem.METRIC,
                Language.EN,
                null,
                null,
                null,
                TimeDisplayMode.DEFAULT,
                TimeMode.TWENTY_FOUR_HOUR,
                "#f1ba63",
                UserType.LIVE_DATA_ONLY
        );

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUserType()).isEqualTo(UserType.LIVE_DATA_ONLY);

        // No processing defaults should be created for live-data-only users
        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).isEmpty();
        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).isEmpty();

        // Device and map style are still created
        assertEquals(1, this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM devices WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals("Reitti",
                     this.jdbcTemplate.queryForObject("""
                                                              SELECT name FROM user_map_styles
                                                                          WHERE id = (SELECT active_style_id FROM user_map_style_settings WHERE user_id = ?)
                                                              """, String.class, user.getId()));
    }

    @Test
    void shouldSwitchToLiveDataOnlyAndDeleteAllData() {
        User user = userService.createNewUser(
                "livedatauser",
                "Live Data User",
                "external789",
                null
        );

        SignificantPlace place = testingService.newSignificantPlace(user);
        SignificantPlace place2 = testingService.newSignificantPlace(user, "Home");
        significantPlaceOverrideJdbcService.insertOverride(user, place);

        ProcessedVisit startVisit = testingService.createVisit(user, place, Instant.now().minus(10, ChronoUnit.HOURS), Instant.now().minus(9, ChronoUnit.HOURS));
        ProcessedVisit endVisit = testingService.createVisit(user, place2, Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        testingService.createTrip(user, startVisit, endVisit);

        RawLocationPoint point = new RawLocationPoint(null, null, Instant.now(),
                new GeoPoint(52.52, 13.40), 5.0, 10.0, false, false, 1L);
        rawLocationPointJdbcService.create(user, point);

        Device device = testingService.findDefaultDevice(user);
        LocationPoint locationPoint = new LocationPoint();
        locationPoint.setLatitude(52.52);
        locationPoint.setLongitude(13.40);
        locationPoint.setTimestamp(Instant.now());
        locationPoint.setAccuracyMeters(5.0);
        sourceLocationPointJdbcService.bulkInsert(user, device, java.util.List.of(locationPoint));

        memoryJdbcService.create(user, new Memory(null, "Test Memory", "desc",
                Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(),
                HeaderType.IMAGE, null,
                Instant.now(), Instant.now(), 1L));

        memoryJdbcService.create(user, new Memory(null, "Another Memory", "desc2",
                Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().minus(3, ChronoUnit.DAYS),
                HeaderType.MAP, null,
                Instant.now(), Instant.now(), 1L));

        jdbcTemplate.update("INSERT INTO h3_cells_stats (user_id, device_id, h3_index, last_visited_at, first_visited_at, point_count) VALUES (?, ?, ?, NOW(), NOW(), 1)", user.getId(), device.id(), 123456L);
        jdbcTemplate.update("INSERT INTO h3_area_coverage_stats (user_id, device_id, osm_id, h3_resolution, visited_cell_count, total_cell_count) VALUES (?, ?, ?, ?, 5, 10)", user.getId(), device.id(), 1L, 9);

        userService.switchToLiveDataOnly(user);

        assertEquals(0, visitDetectionParametersJdbcService.findAllConfigurationsForUser(user).size());
        assertEquals(0, transportModeJdbcService.getTransportModeConfigs(user).size());
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM processed_visits WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM trips WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM raw_source_points WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM significant_places WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM memory WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM h3_cells_stats WHERE user_id = ?", Integer.class, user.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT count(*) FROM h3_area_coverage_stats WHERE user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void shouldSwitchToNormalAndRecreateDefaults() {
        User user = userService.createNewUser(
                "switchbackuser",
                "Switch Back User",
                "external999",
                null
        );

        userService.switchToLiveDataOnly(user);

        assertEquals(0, visitDetectionParametersJdbcService.findAllConfigurationsForUser(user).size());
        assertEquals(0, transportModeJdbcService.getTransportModeConfigs(user).size());

        userService.switchToNormal(user);

        List<DetectionParameter> detectionParams = visitDetectionParametersJdbcService.findAllConfigurationsForUser(user);
        assertThat(detectionParams).hasSize(1);

        List<TransportModeConfig> transportConfigs = transportModeJdbcService.getTransportModeConfigs(user);
        assertThat(transportConfigs).hasSize(4);
    }
}
