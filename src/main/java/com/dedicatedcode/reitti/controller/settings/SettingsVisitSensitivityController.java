package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.ConfigurationForm;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.VisitDetectionParametersJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionPreviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/settings/visit-sensitivity")
public class SettingsVisitSensitivityController {
    
    private final VisitDetectionParametersJdbcService configurationService;
    private final VisitDetectionPreviewService visitDetectionPreviewService;
    private final boolean dataManagementEnabled;

    public SettingsVisitSensitivityController(VisitDetectionParametersJdbcService configurationService,
                                              VisitDetectionPreviewService visitDetectionPreviewService,
                                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.configurationService = configurationService;
        this.visitDetectionPreviewService = visitDetectionPreviewService;
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
                                    @RequestParam(required = false, name = "new-mode") String mode,
                                    @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                    @RequestParam(required = false) Integer sensitivityLevel,
                                    @AuthenticationPrincipal User user,
                                    Model model) {
        ZoneId userTimezone = ZoneId.of(timezone);

        Configuration config = configurationService.findById(id, user).orElseThrow(() -> new IllegalArgumentException("Configuration not found"));

        ConfigurationForm form = ConfigurationForm.fromConfiguration(config, userTimezone);
        
        String effectiveMode = mode != null ? mode : form.getMode();

        if (sensitivityLevel != null && "advanced".equals(effectiveMode)) {
            form.applySensitivityLevel(sensitivityLevel);
        }

        model.addAttribute("configurationForm", form);
        model.addAttribute("mode", effectiveMode);
        model.addAttribute("isDefaultConfig", config.getValidSince() == null);

        return "fragments/configuration-form :: configuration-form";
    }
    
    @GetMapping("/new")
    public String newConfiguration(@RequestParam(defaultValue = "simple", name = "new-mode") String mode,
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
            boolean recalculationNeeded = false;
            
            if (config.getId() == null) {
                // New configuration
                configurationService.saveConfiguration(user, config);
                recalculationNeeded = true;
            } else {
                // Existing configuration - check if it has changed
                Configuration originalConfig = configurationService.findById(config.getId(), user)
                    .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));
                
                recalculationNeeded = form.hasConfigurationChanged(originalConfig);
                configurationService.updateConfiguration(config);
            }
            
            // Set appropriate success message based on whether recalculation is needed
            if (recalculationNeeded) {
                model.addAttribute("successMessage", "visit.sensitivity.recalculation.advised");
            } else {
                model.addAttribute("successMessage", "visit.sensitivity.recalculation.not.needed");
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Failed to save configuration: " + e.getMessage());
        }
        
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", configurations);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() ==  Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("configurationForm", null);
        return "settings/visit-sensitivity";
    }
    
    @DeleteMapping("/{id}")
    public String deleteConfiguration(@PathVariable Long id, Authentication auth, Model model) {
        User user = (User) auth.getPrincipal();
        
        List<Configuration> configurations = configurationService.findAllConfigurationsForUser(user);
        Configuration config = configurations.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));
        
        if (config.getValidSince() == null) {
            throw new IllegalArgumentException("Cannot delete default configuration");
        }
        
        configurationService.delete(id);
        
        // Return updated list with recalculation message
        configurations = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", configurations);
        model.addAttribute("successMessage", "visit.sensitivity.recalculation.advised");
        return "fragments/configuration-list :: configuration-list";
    }
    
    @PostMapping("/preview")
    public String previewConfiguration(@ModelAttribute ConfigurationForm form,
                                       @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                       @RequestParam(required = false) String previewDate,
                                       @AuthenticationPrincipal User user,
                                       Model model) {
        Configuration config = form.toConfiguration(ZoneId.of(timezone));

        Instant date = previewDate != null ? ZonedDateTime.of(LocalDate.parse(previewDate).atStartOfDay(), ZoneId.of(timezone)).toInstant() : Instant.now().truncatedTo(ChronoUnit.DAYS);
        String effectivePreviewDate = previewDate != null ? previewDate :
            Instant.now().atZone(ZoneId.of(timezone)).toLocalDate().toString();

        String previewId = this.visitDetectionPreviewService.startPreview(user, config, date);
        
        model.addAttribute("previewConfig", config);
        model.addAttribute("previewId", previewId);
        model.addAttribute("previewDate", effectivePreviewDate);
        model.addAttribute("userId", user.getId());
        return "fragments/configuration-preview :: configuration-preview";
    }
}
