package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reitti-integration")
public class ReittiIntegrationApiController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new HashMap<>();
        
        // User information
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("displayName", user.getDisplayName());
        userInfo.put("role", user.getRole().name());
        
        // Server instance information
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Reitti");
        serverInfo.put("version", "1.0.0"); // TODO: Get from application properties or build info
        serverInfo.put("timestamp", Instant.now());
        
        response.put("user", userInfo);
        response.put("server", serverInfo);
        
        return ResponseEntity.ok(response);
    }
}
