package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.TilesCustomizationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {

    public static final double DEFAULT_HOME_LATITUDE = 60.1699;
    public static final double DEFAULT_HOME_LONGITUDE = 24.9384;
    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final TilesCustomizationProvider tilesCustomizationProvider;

    public GlobalControllerAdvice(UserJdbcService userJdbcService,
                                  UserSettingsJdbcService userSettingsJdbcService,
                                  TilesCustomizationProvider tilesCustomizationProvider) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.tilesCustomizationProvider = tilesCustomizationProvider;
    }
    
    @ModelAttribute("userSettings")
    public UserSettingsDTO getCurrentUserSettings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal())) {
            // Return default settings for anonymous users
            return new UserSettingsDTO(false, "en", Instant.now(), List.of(), UnitSystem.METRIC, DEFAULT_HOME_LATITUDE, DEFAULT_HOME_LONGITUDE, tilesCustomizationProvider.getTilesConfiguration());
        }
        
        String username = authentication.getName();
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            com.dedicatedcode.reitti.model.UserSettings dbSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            return new UserSettingsDTO(dbSettings.isPreferColoredMap(),
                    dbSettings.getSelectedLanguage(),
                    dbSettings.getLatestData(),
                    dbSettings.getConnectedUserAccounts(),
                    dbSettings.getUnitSystem(),
                    dbSettings.getHomeLatitude(),
                    dbSettings.getHomeLongitude(),
                    tilesCustomizationProvider.getTilesConfiguration());
        }
        
        // Fallback for authenticated users not found in database
        return new UserSettingsDTO(false, "en", Instant.now(), List.of(), UnitSystem.METRIC, DEFAULT_HOME_LATITUDE, DEFAULT_HOME_LONGITUDE, tilesCustomizationProvider.getTilesConfiguration());
    }
}
