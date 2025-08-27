package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.dedicatedcode.reitti.repository.ReittiIntegrationJdbcService;
import com.dedicatedcode.reitti.service.RequestFailedException;
import com.dedicatedcode.reitti.service.RequestTemporaryFailedException;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class ReittiIntegrationSettingsController {
    private final ReittiIntegrationJdbcService jdbcService;
    private final ReittiIntegrationService reittiIntegrationService;

    public ReittiIntegrationSettingsController(ReittiIntegrationJdbcService jdbcService,
                                               ReittiIntegrationService reittiIntegrationService) {
        this.jdbcService = jdbcService;
        this.reittiIntegrationService = reittiIntegrationService;
    }

    @GetMapping("/shared-instances-content")
    public String getSharedInstancesContent(@AuthenticationPrincipal User user, Model model) {
        List<ReittiIntegration> integrations = jdbcService.findAllByUser(user);
        model.addAttribute("reittiIntegrations", integrations);
        return "fragments/settings :: shared-instances-content";
    }

    @PostMapping("/reitti-integrations")
    public String createReittiIntegration(
            @AuthenticationPrincipal User user,
            @RequestParam String url,
            @RequestParam(name = "remote_token") String token,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(defaultValue = "#3498db") String color,
            Model model) {
        
        try {
            this.jdbcService.create(user, ReittiIntegration.create(url, token, enabled, color));
            model.addAttribute("successMessage", "Reitti integration saved successfully");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error saving configuration: " + e.getMessage());
        }
        
        return getSharedInstancesContent(user, model);
    }

    @PostMapping("/reitti-integrations/{id}/update")
    public String updateReittiIntegration(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String url,
            @RequestParam(name = "remote_token") String token,
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
                        enabled ? ReittiIntegration.Status.ACTIVE : ReittiIntegration.Status.RECOVERABLE,
                        integration.getCreatedAt(),
                        integration.getUpdatedAt(),
                        integration.getLastUsed().orElse(null),
                        version,
                        integration.getLastMessage().orElse(null),
                        color
                    );
                    this.jdbcService.update(updatedIntegration);
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
                    ReittiIntegration updated = integration.withEnabled(!integration.isEnabled());
                    if (!updated.isEnabled()) {
                        updated = updated.withStatus(ReittiIntegration.Status.DISABLED);
                    } else {
                        updated = updated.withStatus(ReittiIntegration.Status.ACTIVE);
                    }
                    this.jdbcService.update(updated);
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
                    this.jdbcService.delete(integration);
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

    @GetMapping("/reitti-integrations/{id}/info")
    public String getReittiIntegrationInfo(@AuthenticationPrincipal User user, @PathVariable Long id, Model model) {
        try {
            this.jdbcService.findByIdAndUser(id, user).ifPresentOrElse(integration -> {
                try {
                    model.addAttribute("remoteInfo", this.reittiIntegrationService.getInfo(integration));
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Connection failed: " + e.getMessage());
                } catch (RequestFailedException | RequestTemporaryFailedException e) {
                    model.addAttribute("errorMessage", "Failed to fetch information from remote instance");
                }
            }, () -> model.addAttribute("errorMessage", "Integration not found"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error fetching integration info: " + e.getMessage());
        }
        return "fragments/settings :: reitti-info-content";
    }

    @PostMapping("/reitti-integrations/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testReittiConnection(
            @RequestParam String url,
            @RequestParam(name = "foreign_token") String token) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            ReittiRemoteInfo info = this.reittiIntegrationService.getInfo(url, token);
            response.put("success", true);
            response.put("message", "Connection successful - Connected to Reitti instance");
            response.put("remoteInfo", info);
        } catch (RequestFailedException | RequestTemporaryFailedException e) {
            response.put("success", false);
            response.put("message", "Connection failed: Invalid response from remote instance");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Connection failed: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}
