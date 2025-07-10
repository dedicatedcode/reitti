package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.UserSettings;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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
            return UserSettings.anonymous();
        }
        
        String username = authentication.getName();
        Optional<User> userOptional = userJdbcService.findByUsername(username);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            return UserSettings.authenticated(user.getUsername(), user.getDisplayName(), user.getId());
        }
        
        // Fallback if user not found in database but authenticated
        return UserSettings.authenticated(username, username, null);
    }
}
