package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.dedicatedcode.reitti.repository.ReittiIntegrationJdbcService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class ReittiIntegrationController {
    private final ReittiIntegrationJdbcService jdbcService;

    public ReittiIntegrationController(ReittiIntegrationJdbcService jdbcService) {
        this.jdbcService = jdbcService;
    }

    @GetMapping("/shared-instances-content")
    public String getSharedInstancesContent(@AuthenticationPrincipal User user, Model model) {
        // TODO: Replace with actual service call when JDBC is implemented
        List<ReittiIntegration> integrations = jdbcService.findAllByUser(user);
        
        model.addAttribute("reittiIntegrations", integrations);
        return "fragments/settings :: shared-instances-content";
    }

    @PostMapping("/reitti-integrations")
    public String createReittiIntegration(
            @AuthenticationPrincipal User user,
            @RequestParam String url,
            @RequestParam String token,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(defaultValue = "#3498db") String color,
            Model model) {
        
        try {
            this.jdbcService.create(user, url, token, color, enabled);
            model.addAttribute("successMessage", "Reitti integration saved successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error saving configuration: " + e.getMessage());
        }
        
        // Reload the content
        return getSharedInstancesContent(user, model);
    }

    @PostMapping("/reitti-integrations/{id}/update")
    public String updateReittiIntegration(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String url,
            @RequestParam String token,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(defaultValue = "#3498db") String color,
            @RequestParam Long version,
            Model model) {
        
        try {
            this.jdbcService.findByIdAndUser(id, user).ifPresentOrElse(integration -> {
                try {
                    ReittiIntegration updatedIntegration = new ReittiIntegration(
                        integration.getId(),
                        url,
                        token,
                        enabled,
                        enabled ? ReittiIntegration.Status.ENABLED : ReittiIntegration.Status.DISABLED,
                        integration.getCreatedAt(),
                        integration.getUpdatedAt(),
                        integration.getLastUsed(),
                        version,
                        integration.getLastMessage(),
                        color
                    );
                    this.jdbcService.update(user, updatedIntegration);
                    model.addAttribute("successMessage", "Reitti integration updated successfully");
                } catch (OptimisticLockException e) {
                    model.addAttribute("errorMessage", "Integration is out of date. Please reload the page and try again.");
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Error updating configuration: " + e.getMessage());
                }
            }, () -> model.addAttribute("errorMessage", "Integration not found!"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error updating configuration: " + e.getMessage());
        }
        
        // Reload the content
        return getSharedInstancesContent(user, model);
    }

    @PostMapping("/reitti-integrations/{id}/toggle")
    public String toggleReittiIntegration(@AuthenticationPrincipal User user, @PathVariable Long id, Model model) {
        try {
            this.jdbcService.findByIdAndUser(id, user).ifPresentOrElse(integration -> {
                try {
                    this.jdbcService.toggleEnabled(user, integration);
                    model.addAttribute("successMessage", "Integration status updated successfully");
                } catch (OptimisticLockException e) {
                    model.addAttribute("errorMessage", "Integration is out of date. Please reload the page and try again.");
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Error updating integration: " + e.getMessage());
                }
            }, () -> model.addAttribute("errorMessage", "Integration not found!"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error updating integration: " + e.getMessage());
        }

        return getSharedInstancesContent(user, model);
    }

    @PostMapping("/reitti-integrations/{id}/delete")
    public String deleteReittiIntegration(@AuthenticationPrincipal User user, @PathVariable Long id, Model model) {
        try {
            this.jdbcService.findByIdAndUser(id, user).ifPresent(integration -> {
                try {
                    this.jdbcService.delete(user, integration);
                } catch (OptimisticLockException e) {
                    model.addAttribute("errorMessage", "Integration is out of date. Please reload the page and try again.");
                }
            });
            model.addAttribute("successMessage", "Reitti integration deleted successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error deleting configuration: " + e.getMessage());
        }

        return getSharedInstancesContent(user, model);
    }

    @PostMapping("/reitti-integrations/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testReittiConnection(
            @RequestParam String url,
            @RequestParam(name = "foreign_token") String token) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-TOKEN", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String infoUrl = url.endsWith("/") ? url + "api/v1/reitti-integration/info" : url + "/api/v1/reitti-integration/info";
            
            ResponseEntity<Map> remoteResponse = restTemplate.exchange(
                infoUrl,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (remoteResponse.getStatusCode().is2xxSuccessful() && remoteResponse.getBody() != null) {
                Map<String, Object> remoteData = remoteResponse.getBody();
                response.put("success", true);
                response.put("message", "Connection successful - Connected to Reitti instance");
                response.put("remoteInfo", remoteData);
            } else {
                response.put("success", false);
                response.put("message", "Connection failed: Invalid response from remote instance");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Connection failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
