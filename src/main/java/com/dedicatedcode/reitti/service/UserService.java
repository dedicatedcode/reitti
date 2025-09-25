package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.repository.VisitDetectionParametersJdbcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;

@Service
public class UserService {
    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserJdbcService userJdbcService, UserSettingsJdbcService userSettingsJdbcService, VisitDetectionParametersJdbcService visitDetectionParametersJdbcService, PasswordEncoder passwordEncoder) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.visitDetectionParametersJdbcService = visitDetectionParametersJdbcService;
        this.passwordEncoder = passwordEncoder;
    }

    public User createNewUser(String username,
                              String displayName,
                              String externalId,
                              String profileUrl) {
        User createdUser = userJdbcService.createUser(new User(username, displayName)
                .withPassword("")
                .withRole(Role.USER)
                .withExternalId(externalId)
                .withProfileUrl(profileUrl));
        saveDefaultVisitDetectionParameters(createdUser);

        return createdUser;

    }
    public User createNewUser(String username,
                              String displayName,
                              String password,
                              Role role,
                              UnitSystem unitSystem,
                              boolean preferColoredMap,
                              String preferredLanguage,
                              Double homeLatitude,
                              Double homeLongitude,
                              String timezoneOverride,
                              TimeDisplayMode timeDisplayMode) {
        User createdUser = userJdbcService.createUser(new User(username, displayName)
                .withPassword(passwordEncoder.encode(password))
                .withRole(role));

        UserSettings userSettings = new UserSettings(createdUser.getId(),
                preferColoredMap,
                preferredLanguage,
                unitSystem,
                homeLatitude,
                homeLongitude,
                StringUtils.hasText(timezoneOverride) ? ZoneId.of(timezoneOverride) : null,
                timeDisplayMode,
                null,
                null);


        saveDefaultVisitDetectionParameters(createdUser);
        userSettingsJdbcService.save(userSettings);
        return createdUser;
    }

    private void saveDefaultVisitDetectionParameters(User createdUser) {
        visitDetectionParametersJdbcService.saveConfiguration(createdUser, new DetectionParameter(null,
                new DetectionParameter.VisitDetection(100, 5, 300, 330),
                new DetectionParameter.VisitMerging(48, 300, 200),
                null,
                false)
        );
    }


}
