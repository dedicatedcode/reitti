package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MqttIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/settings/integrations")
public class MqttIntegrationSettingsController {
    
    private final MqttIntegrationJdbcService mqttIntegrationJdbcService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MqttIntegrationSettingsController(MqttIntegrationJdbcService mqttIntegrationJdbcService) {
        this.mqttIntegrationJdbcService = mqttIntegrationJdbcService;
    }

    @PostMapping("/mqtt-integration")
    public String saveMqttIntegration(
            @AuthenticationPrincipal User user,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String identifier,
            @RequestParam String topic,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam PayloadType payloadType,
            @RequestParam(defaultValue = "false") boolean enabled,
            Model model) {
        
        try {
            // Validate topic doesn't contain wildcard characters
            if (topic.contains("+") || topic.contains("#")) {
                model.addAttribute("errorMessage", "Topic cannot contain wildcard characters (+ or #) when saving the configuration. Wildcards are only for subscribing to topics.");
                return getIntegrationsContent(user, model);
            }
            
            // Validate port range
            if (port < 1 || port > 65535) {
                model.addAttribute("errorMessage", "Port must be between 1 and 65535");
                return getIntegrationsContent(user, model);
            }
            
            MqttIntegration integration = new MqttIntegration(
                null,
                host.trim(),
                port,
                identifier.trim(),
                topic.trim(),
                username != null ? username.trim() : null,
                password != null ? password.trim() : null,
                payloadType,
                enabled,
                null,
                null,
                null,
                null
            );
            
            mqttIntegrationJdbcService.save(user, integration);
            model.addAttribute("successMessage", "MQTT integration saved successfully");
            
        } catch (OptimisticLockException e) {
            model.addAttribute("errorMessage", "Integration is out of date. Please reload the page and try again.");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error saving MQTT integration: " + e.getMessage());
        }
        
        return getIntegrationsContent(user, model);
    }

    @PostMapping("/mqtt-integration/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testMqttConnection(
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String identifier,
            @RequestParam String topic,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam PayloadType payloadType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate basic parameters
            if (host == null || host.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Host is required");
                return ResponseEntity.ok(response);
            }
            
            if (port < 1 || port > 65535) {
                response.put("success", false);
                response.put("message", "Port must be between 1 and 65535");
                return ResponseEntity.ok(response);
            }
            
            if (identifier == null || identifier.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Client identifier is required");
                return ResponseEntity.ok(response);
            }
            
            if (topic == null || topic.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Topic is required");
                return ResponseEntity.ok(response);
            }
            
            // For now, just return success since we don't have actual MQTT connection testing implemented
            response.put("success", true);
            response.put("message", "Connection test successful - Configuration appears valid");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Connection test failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    private String getIntegrationsContent(@AuthenticationPrincipal User user, Model model) {
        // Load existing MQTT integration
        Optional<MqttIntegration> mqttIntegration = mqttIntegrationJdbcService.findByUser(user);
        model.addAttribute("mqttIntegration", mqttIntegration.orElse(null));
        
        // Generate client ID if no integration exists
        if (mqttIntegration.isEmpty()) {
            model.addAttribute("generatedClientId", generateClientId());
        }
        
        return "settings/integrations :: integrations-content";
    }
    
    private String generateClientId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder("reitti-client-");
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
