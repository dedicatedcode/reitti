package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.UserSettingsDTO;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {
    
    private final UserJdbcService userJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    
    public GlobalControllerAdvice(UserJdbcService userJdbcService, UserSettingsJdbcService userSettingsJdbcService) {
        this.userJdbcService = userJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }
    
    @ModelAttribute("userSettings")
    public UserSettingsDTO getCurrentUserSettings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            // Return default settings for anonymous users
            return new UserSettingsDTO(false, "en", List.of(), UnitSystem.METRIC);
        }
        
        String username = authentication.getName();
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            com.dedicatedcode.reitti.model.UserSettings dbSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
            return new UserSettingsDTO(dbSettings.isPreferColoredMap(), dbSettings.getSelectedLanguage(), dbSettings.getConnectedUserAccounts(), dbSettings.getUnitSystem());
        }
        
        // Fallback for authenticated users not found in database
        return new UserSettingsDTO(false, "en", List.of(),  UnitSystem.METRIC);
    }
}
