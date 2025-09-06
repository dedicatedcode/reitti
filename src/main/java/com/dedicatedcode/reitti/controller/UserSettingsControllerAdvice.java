package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.TilesCustomizationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class UserSettingsControllerAdvice {

    public static final double DEFAULT_HOME_LATITUDE = 60.1699;
    public static final double DEFAULT_HOME_LONGITUDE = 24.9384;
    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final TilesCustomizationProvider tilesCustomizationProvider;

    public UserSettingsControllerAdvice(UserJdbcService userJdbcService,
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
            return new UserSettingsDTO(false, "en", Instant.now(), UnitSystem.METRIC, DEFAULT_HOME_LATITUDE, DEFAULT_HOME_LONGITUDE, tilesCustomizationProvider.getTilesConfiguration(), UserSettingsDTO.UIMode.FULL);
        }
        
        String username = authentication.getName();
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        UserSettingsDTO.UIMode uiMode = mapUserToUiMode(authentication);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserSettings dbSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            return new UserSettingsDTO(dbSettings.isPreferColoredMap(),
                    dbSettings.getSelectedLanguage(),
                    dbSettings.getLatestData(),
                    dbSettings.getUnitSystem(),
                    dbSettings.getHomeLatitude(),
                    dbSettings.getHomeLongitude(),
                    tilesCustomizationProvider.getTilesConfiguration(),
                    uiMode);
        }
        
        // Fallback for authenticated users not found in database
        return new UserSettingsDTO(false, "en", Instant.now(), UnitSystem.METRIC, DEFAULT_HOME_LATITUDE, DEFAULT_HOME_LONGITUDE, tilesCustomizationProvider.getTilesConfiguration(), uiMode);
    }

    private UserSettingsDTO.UIMode mapUserToUiMode(Authentication authentication) {
        List<String> grantedRoles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        if (grantedRoles.contains("ROLE_ADMIN") || grantedRoles.contains("ROLE_USER") || grantedRoles.contains("ROLE_API_ACCESS")) {
            return UserSettingsDTO.UIMode.FULL;
        } else if (grantedRoles.contains("ROLE_MAGIC_LINK_FULL_ACCESS")) {
            return UserSettingsDTO.UIMode.SHARED_FULL;
        } else if (grantedRoles.contains("ROLE_MAGIC_LINK_ONLY_LIVE")) {
            return UserSettingsDTO.UIMode.SHARED_LIVE_MODE_ONLY;
        } else {
            throw new IllegalStateException("Invalid user authentication mode detected [" + grantedRoles + "]");
        }
    }

}
