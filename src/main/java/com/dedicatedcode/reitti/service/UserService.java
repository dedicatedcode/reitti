package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.processing.RecalculationState;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;
    private final TransportModeJdbcService transportModeJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final GeocodingResponseJdbcService geocodingResponseJdbcService;
    private final ApiTokenJdbcService apiTokenJdbcService;
    private final MqttIntegrationJdbcService mqttIntegrationJdbcService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final ApiTokenService apiTokenService;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserJdbcService userJdbcService,
                       UserSettingsJdbcService userSettingsJdbcService,
                       VisitDetectionParametersJdbcService visitDetectionParametersJdbcService,
                       TransportModeJdbcService transportModeJdbcService,
                       RawLocationPointJdbcService rawLocationPointJdbcService,
                       SignificantPlaceJdbcService significantPlaceJdbcService, SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                       ProcessedVisitJdbcService processedVisitJdbcService,
                       GeocodingResponseJdbcService geocodingResponseJdbcService,
                       ApiTokenJdbcService apiTokenJdbcService,
                       MqttIntegrationJdbcService mqttIntegrationJdbcService,
                       PasswordEncoder passwordEncoder, UserMapStyleJdbcService userMapStyleJdbcService, DeviceJdbcService deviceJdbcService, ApiTokenService apiTokenService,
                       JdbcTemplate jdbcTemplate) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.visitDetectionParametersJdbcService = visitDetectionParametersJdbcService;
        this.transportModeJdbcService = transportModeJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.geocodingResponseJdbcService = geocodingResponseJdbcService;
        this.apiTokenJdbcService = apiTokenJdbcService;
        this.mqttIntegrationJdbcService = mqttIntegrationJdbcService;
        this.passwordEncoder = passwordEncoder;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.apiTokenService = apiTokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public User createNewUser(String username,
                              String displayName,
                              String externalId,
                              String profileUrl) {
        User createdUser = userJdbcService.createUser(new User(username, displayName)
                .withPassword("")
                .withRole(Role.USER)
                .withExternalId(externalId)
                .withProfileUrl(profileUrl));
        UserSettings userSettings = UserSettings.defaultSettings(createdUser.getId());
        userSettings = addRandomHomeLocation(userSettings);
        saveDefaultVisitDetectionParameters(createdUser);
        saveDefaultTransportationModeDetectionParameters(createdUser);
        setDefaultMapStyle(createdUser);
        createDefaultDeviceForUser(createdUser);
        this.userSettingsJdbcService.save(userSettings);
        return createdUser;
    }

    private void createDefaultDeviceForUser(User createdUser) {
        ApiToken token = this.apiTokenService.createToken(createdUser, "Default");
        Device saved = this.deviceJdbcService.save(new Device(null, "Default", true, true, "#f1ba63", true, Instant.now(), Instant.now(), 0L), createdUser);
        token = token.withDevice(saved);
        this.apiTokenJdbcService.save(token);
    }

    private void setDefaultMapStyle(User createdUser) {
        Long defaultStyleId = jdbcTemplate.queryForObject(
                "SELECT id FROM user_map_styles WHERE name = 'Reitti' LIMIT 1",
                Long.class);
        if (defaultStyleId != null) {
            userMapStyleJdbcService.setActiveStyleId(createdUser, defaultStyleId);
        }
    }

    public User createNewUser(String username,
                              String displayName,
                              String password,
                              Role role,
                              UnitSystem unitSystem,
                              Language preferredLanguage,
                              Double homeLatitude,
                              Double homeLongitude,
                              String timezoneOverride,
                              TimeDisplayMode timeDisplayMode,
                              TimeMode timeMode,
                              String color) {
        User createdUser = userJdbcService.createUser(new User(username, displayName)
                .withPassword(passwordEncoder.encode(password))
                .withRole(role));

        UserSettings userSettings = new UserSettings(createdUser.getId(),
                                                     preferredLanguage,
                                                     unitSystem,
                                                     homeLatitude,
                                                     homeLongitude,
                                                     StringUtils.hasText(timezoneOverride) ? ZoneId.of(timezoneOverride) : null,
                                                     timeDisplayMode,
                                                     timeMode,
                                                     null,
                                                     null,
                                                     color,
                                                     null);

        if (userSettings.getHomeLatitude() == null && userSettings.getHomeLongitude() == null) {
            userSettings = addRandomHomeLocation(userSettings);
        }
        setDefaultMapStyle(createdUser);
        saveDefaultVisitDetectionParameters(createdUser);
        saveDefaultTransportationModeDetectionParameters(createdUser);
        userSettingsJdbcService.save(userSettings);
        return createdUser;
    }

    private UserSettings addRandomHomeLocation(UserSettings userSettings) {
        Optional<GeoPoint> geoPoint = this.jdbcTemplate.query("SELECT latitude, longitude FROM random_cities ORDER BY random() LIMIT 1", (rs, rowNum) -> new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude"))).stream().findFirst();
        if (geoPoint.isPresent()) {
            userSettings = userSettings.withHomeCoordinates(geoPoint.get().latitude(), geoPoint.get().longitude());
        }
        return userSettings;
    }

    private void saveDefaultTransportationModeDetectionParameters(User createdUser) {
        this.transportModeJdbcService.setTransportModeConfigs(createdUser,
                List.of(
                        new TransportModeConfig(TransportMode.WALKING, 7.0),
                        new TransportModeConfig(TransportMode.CYCLING, 20.0),
                        new TransportModeConfig(TransportMode.DRIVING, 120.0),
                        new TransportModeConfig(TransportMode.TRANSIT, null)
                ));
    }

    private void saveDefaultVisitDetectionParameters(User createdUser) {
        visitDetectionParametersJdbcService.saveConfiguration(createdUser, new DetectionParameter(null,
                new DetectionParameter.VisitDetection(300, 300),
                new DetectionParameter.VisitMerging(48, 300, 100),
                new DetectionParameter.LocationDensity(50, 1440),
                null,
                RecalculationState.DONE)
        );
    }

    @Transactional
    public void deleteUser(User user) {
        this.visitDetectionParametersJdbcService.findAllConfigurationsForUser(user).forEach(detectionParameter -> this.visitDetectionParametersJdbcService.delete(detectionParameter.getId()));
        this.transportModeJdbcService.deleteAllForUser(user);
        this.userSettingsJdbcService.deleteFor(user);
        this.geocodingResponseJdbcService.deleteAllForUser(user);
        this.processedVisitJdbcService.deleteAllForUser(user);
        this.significantPlaceJdbcService.deleteForUser(user);
        this.significantPlaceOverrideJdbcService.deleteForUser(user);
        this.rawLocationPointJdbcService.deleteAllForUser(user);
        this.apiTokenJdbcService.deleteForUser(user);
        this.mqttIntegrationJdbcService.deleteForUser(user);
        // Delete the row in the map style settings table
        this.jdbcTemplate.update("DELETE FROM user_map_style_settings WHERE user_id = ?", user.getId());
        this.jdbcTemplate.update("DELETE FROM user_map_styles WHERE user_id = ?", user.getId());
        this.deviceJdbcService.deleteForUser(user);
        this.userJdbcService.deleteUser(user.getId());
    }

}
