package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ManifestController {
    private final ContextPathHolder contextPathHolder;

    public ManifestController(ContextPathHolder contextPathHolder) {
        this.contextPathHolder = contextPathHolder;
    }

    @GetMapping("/manifest.json")
    public ResponseEntity<Map<String, Object>> getManifest(@AuthenticationPrincipal User user) {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("name", "Reitti");
        manifest.put("short_name", "Reitti");
        manifest.put("display", "standalone");
        manifest.put("background_color", "#3b3b3b");
        manifest.put("theme_color", "#3b3b3b");
        List<Map<String, String>> icons = new ArrayList<>();

        icons.add(Map.of(
                "src", contextPathHolder.getContextPath() + "/img/logo-192x192.png",
                "sizes", "192x192",
                "type", "image/png"
        ));

        icons.add(Map.of(
                "src", contextPathHolder.getContextPath() + "/img/logo-512x512.png",
                "sizes", "512x512",
                "type", "image/png"
        ));
        manifest.put("icons", icons);
        if (user instanceof TokenUser) {
            String startUrl = contextPathHolder.getContextPath() + "/access?mt=" + ((TokenUser) user).getToken();
            manifest.put("start_url", startUrl);
        }
        return ResponseEntity.ok(manifest);
    }
}
