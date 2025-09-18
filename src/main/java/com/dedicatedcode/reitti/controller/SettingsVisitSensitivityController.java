package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.ConfigurationForm;
import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ConfigurationJdbcService;
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
    
    public SettingsVisitSensitivityController(ConfigurationJdbcService configurationService) {
        this.configurationService = configurationService;
    }
    
    @GetMapping
    public String visitSensitivitySettings(Authentication auth, Model model) {
        User user = (User) auth.getPrincipal();
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        
        model.addAttribute("configurations", configurations);
        model.addAttribute("activeSection", "visit-sensitivity");
        return "settings/visit-sensitivity";
    }
    
    @GetMapping("/edit/{id}")
    public String editConfiguration(@PathVariable Long id,
                                    @RequestParam(defaultValue = "simple") String mode,
                                    @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                    @AuthenticationPrincipal User user, Model model) {
        ZoneId userTimezone = ZoneId.of(timezone);

        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        Configuration config = configurations.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));

        ConfigurationForm form = ConfigurationForm.fromConfiguration(config, userTimezone);
        model.addAttribute("configurationForm", form);
        model.addAttribute("mode", mode);
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
        Configuration config = form.toConfiguration(ZoneId.of(timezone));
        
        if (config.getId() == null) {
            configurationService.saveConfiguration(user, config);
        } else {
            configurationService.updateConfiguration(config);
        }
        
        // Return updated list
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", configurations);
        return "fragments/configuration-list :: configuration-list";
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
                                       Model model) {
        Configuration config = form.toConfiguration(ZoneId.of(timezone));
        model.addAttribute("previewConfig", config);
        return "fragments/configuration-preview :: configuration-preview";
    }
}
