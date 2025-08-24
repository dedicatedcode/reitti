package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.VersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/api/v1/reitti-integration")
public class ReittiIntegrationApiController {
    private static final Logger log = LoggerFactory.getLogger(ReittiIntegrationApiController.class);
    private final VersionService versionService;

    public ReittiIntegrationApiController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new HashMap<>();
        
        // User information
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("displayName", user.getDisplayName());

        // Server instance information
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Reitti");
        serverInfo.put("version", this.versionService.getVersion()); // TODO: Get from application properties or build info
        serverInfo.put("systemTime", LocalDateTime.now());
        
        response.put("user", userInfo);
        response.put("server", serverInfo);
        
        return ResponseEntity.ok(response);
    }
}
