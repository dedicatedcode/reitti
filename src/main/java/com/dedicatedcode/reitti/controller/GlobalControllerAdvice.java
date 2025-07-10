package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.UserSettings;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class GlobalControllerAdvice {
    
    private final UserJdbcService userJdbcService;
    
    public GlobalControllerAdvice(UserJdbcService userJdbcService) {
        this.userJdbcService = userJdbcService;
    }
    
    @ModelAttribute("currentUserSettings")
    public UserSettings getCurrentUserSettings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            // Return default settings for anonymous users
            return new UserSettings(false, "en", List.of());
        }
        
        String username = authentication.getName();
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        
        if (userOptional.isPresent()) {
            // TODO: Load actual user preferences from database
            // For now, return default settings
            return new UserSettings(false, "en", List.of());
        }
        
        // Fallback for authenticated users not found in database
        return new UserSettings(false, "en", List.of());
    }
}
