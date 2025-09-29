package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class UserCssController {

    private final UserSettingsJdbcService userSettingsJdbcService;

    public UserCssController(UserSettingsJdbcService userSettingsJdbcService) {
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping("/user-css/{userId}")
    public ResponseEntity<String> getUserCss(@PathVariable Long userId) {
        UserSettings userSettings = userSettingsJdbcService.findByUserId(userId).orElse(null);
        
        if (userSettings == null || !StringUtils.hasText(userSettings.getCustomCss())) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/css"));
        headers.setCacheControl("public, max-age=3600"); // Cache for 1 hour
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(userSettings.getCustomCss());
    }
}
