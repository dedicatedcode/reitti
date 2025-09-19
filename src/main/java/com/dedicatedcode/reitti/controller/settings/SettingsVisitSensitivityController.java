package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.ConfigurationForm;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ConfigurationJdbcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/settings/visit-sensitivity")
public class SettingsVisitSensitivityController {
    
    private final ConfigurationJdbcService configurationService;
    private final boolean dataManagementEnabled;

    public SettingsVisitSensitivityController(ConfigurationJdbcService configurationService,
                                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.configurationService = configurationService;
        this.dataManagementEnabled = dataManagementEnabled;
    }
    
    @GetMapping
    public String visitSensitivitySettings(@AuthenticationPrincipal User user, Model model) {
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        
        model.addAttribute("isAdmin", user.getRole() ==  Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("configurations", configurations);
        model.addAttribute("activeSection", "visit-sensitivity");
        return "settings/visit-sensitivity";
    }
    
    @GetMapping("/edit/{id}")
    public String editConfiguration(@PathVariable Long id,
                                    @RequestParam(required = false) String mode,
                                    @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                    @AuthenticationPrincipal User user, Model model) {
        ZoneId userTimezone = ZoneId.of(timezone);

        Configuration config = configurationService.findById(id, user).orElseThrow(() -> new IllegalArgumentException("Configuration not found"));

        ConfigurationForm form = ConfigurationForm.fromConfiguration(config, userTimezone);
        
        // Use the mode from the form if not explicitly specified in the request
        String effectiveMode = mode != null ? mode : form.getMode();
        
        model.addAttribute("configurationForm", form);
        model.addAttribute("mode", effectiveMode);
        model.addAttribute("isDefaultConfig", config.getValidSince() == null);
        
        return "fragments/configuration-form :: configuration-form";
    }
    
    @GetMapping("/new")
    public String newConfiguration(@RequestParam(defaultValue = "simple") String mode,
                                   @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                   Model model) {
        ConfigurationForm form = new ConfigurationForm();
        form.setValidSince(Instant.now().atZone(ZoneId.of(timezone)).toLocalDate());
        
        model.addAttribute("configurationForm", form);
        model.addAttribute("mode", mode);
        model.addAttribute("isDefaultConfig", false);
        
        return "fragments/configuration-form :: configuration-form";
    }
    
    @PostMapping("/save")
    public String saveConfiguration(@ModelAttribute ConfigurationForm form,
                                    @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                    @AuthenticationPrincipal User user,
                                    Model model) {
        try {
            Configuration config = form.toConfiguration(ZoneId.of(timezone));
            
            if (config.getId() == null) {
                configurationService.saveConfiguration(user, config);
                model.addAttribute("successMessage", "Configuration saved successfully");
            } else {
                configurationService.updateConfiguration(config);
                model.addAttribute("successMessage", "Configuration updated successfully");
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Failed to save configuration: " + e.getMessage());
        }
        
        // Return updated list
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", configurations);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() ==  Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        return "settings/visit-sensitivity";
    }
    
    @DeleteMapping("/{id}")
    public String deleteConfiguration(@PathVariable Long id, Authentication auth, Model model) {
        User user = (User) auth.getPrincipal();
        
        // Verify this is not the default configuration
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        Configuration config = configurations.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));
        
        if (config.getValidSince() == null) {
            throw new IllegalArgumentException("Cannot delete default configuration");
        }
        
        configurationService.delete(id);
        
        // Return updated list
        configurations = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", configurations);
        return "fragments/configuration-list :: configuration-list";
    }
    
    @PostMapping("/preview")
    public String previewConfiguration(@ModelAttribute ConfigurationForm form,
                                       @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                       @AuthenticationPrincipal User user,
                                       Model model) {
        Configuration config = form.toConfiguration(ZoneId.of(timezone));
        String previewId = java.util.UUID.randomUUID().toString();
        
        // TODO: Start async processing of preview data with the previewId
        // This would trigger background processing with the new configuration
        
        model.addAttribute("previewConfig", config);
        model.addAttribute("previewId", previewId);
        model.addAttribute("userId", user.getId());
        return "fragments/configuration-preview :: configuration-preview";
    }
}
