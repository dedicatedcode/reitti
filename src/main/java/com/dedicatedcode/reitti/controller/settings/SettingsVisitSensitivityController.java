package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.dto.ConfigurationForm;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.VisitDetectionPreviewService;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/settings/visit-sensitivity")
public class SettingsVisitSensitivityController {

    private static final Logger log = LoggerFactory.getLogger(SettingsVisitSensitivityController.class);
    private final VisitDetectionParametersJdbcService configurationService;
    private final VisitDetectionPreviewService visitDetectionPreviewService;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final VisitJdbcService visitJdbcService;
    private final MessageSource messageSource;
    private final boolean dataManagementEnabled;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public SettingsVisitSensitivityController(VisitDetectionParametersJdbcService configurationService,
                                              VisitDetectionPreviewService visitDetectionPreviewService,
                                              ProcessingPipelineTrigger processingPipelineTrigger,
                                              TripJdbcService tripJdbcService,
                                              ProcessedVisitJdbcService processedVisitJdbcService,
                                              VisitJdbcService visitJdbcService,
                                              MessageSource messageSource,
                                              @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled, RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.configurationService = configurationService;
        this.visitDetectionPreviewService = visitDetectionPreviewService;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.visitJdbcService = visitJdbcService;
        this.messageSource = messageSource;
        this.dataManagementEnabled = dataManagementEnabled;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }
    
    @GetMapping
    public String visitSensitivitySettings(@AuthenticationPrincipal User user, Model model) {
        List<DetectionParameter> detectionParameters = configurationService.findAllConfigurationsForUser(user);
        
        model.addAttribute("isAdmin", user.getRole() ==  Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("configurations", detectionParameters);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("recalculationAdvised", detectionParameters.stream().anyMatch(this::calculateNeedsConfiguration));
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

        DetectionParameter config = configurationService.findById(id, user).orElseThrow(() -> new IllegalArgumentException("Configuration not found"));

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
            DetectionParameter config = form.toConfiguration(ZoneId.of(timezone));

            String validationError = validateConfiguration(config, user);
            if (validationError != null) {
                model.addAttribute("errorMessage", validationError);
            } else {
                if (config.getId() == null) {
                    config = config.withNeedsRecalculation(this.rawLocationPointJdbcService.containsDataAfter(user, config.getValidSince()));
                    configurationService.saveConfiguration(user, config);
                } else {
                    // Existing configuration - check if it has changed
                    DetectionParameter originalConfig = configurationService.findById(config.getId(), user)
                            .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));

                    config = config.withNeedsRecalculation(form.hasConfigurationChanged(originalConfig));
                    configurationService.updateConfiguration(config);
                }
                model.addAttribute("successMessage", "Configuration saved successfully. Changes will apply to new incoming data.");
            }
        } catch (Exception e) {
            String errorMessage = messageSource.getMessage("visit.sensitivity.validation.save.error",
                new Object[]{e.getMessage()}, LocaleContextHolder.getLocale());
            model.addAttribute("errorMessage", errorMessage);
        }

        List<DetectionParameter> detectionParameters = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", detectionParameters);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() ==  Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("configurationForm", null);
        model.addAttribute("recalculationAdvised", detectionParameters.stream().anyMatch(this::calculateNeedsConfiguration));

        return "settings/visit-sensitivity";
    }

    private boolean calculateNeedsConfiguration(DetectionParameter config) {
        return config.needsRecalculation();
    }

    @DeleteMapping("/{id}")
    public String deleteConfiguration(@PathVariable Long id, Authentication auth, Model model) {
        User user = (User) auth.getPrincipal();
        
        List<DetectionParameter> detectionParameters = configurationService.findAllConfigurationsForUser(user);
        DetectionParameter config = detectionParameters.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Configuration not found"));
        
        if (config.getValidSince() == null) {
            throw new IllegalArgumentException("Cannot delete default configuration");
        }

        configurationService.delete(id);

        DetectionParameter newLatest = this.configurationService.findCurrent(user, config.getValidSince());
        if (this.rawLocationPointJdbcService.containsData(user, newLatest.getValidSince(), config.getValidSince())) {
            this.configurationService.updateConfiguration(newLatest.withNeedsRecalculation(true));
        }
        detectionParameters = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", detectionParameters);
        model.addAttribute("successMessage", "Configuration deleted successfully.");
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("recalculationAdvised", detectionParameters.stream().anyMatch(this::calculateNeedsConfiguration));

        return "settings/visit-sensitivity";
    }
    
    @PostMapping("/recalculate")
    public String startRecalculation(@AuthenticationPrincipal User user, Model model) {
        try {
            clearTimeRange(user);
            model.addAttribute("successMessage", messageSource.getMessage("visit.sensitivity.recalculation.started", null, LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            model.addAttribute("errorMessage", messageSource.getMessage("visit.sensitivity.recalculation.error", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
        }

        List<DetectionParameter> detectionParameters = this.configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", detectionParameters);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("recalculationAdvised", detectionParameters.stream().anyMatch(this::calculateNeedsConfiguration));
        
        return "settings/visit-sensitivity";
    }
    
    @PostMapping("/dismiss-recalculation")
    public String dismissRecalculation(@AuthenticationPrincipal User user, Model model) {
        try {
            this.configurationService.findAllConfigurationsForUser(user)
                    .forEach(config -> this.configurationService.updateConfiguration(config.withNeedsRecalculation(false)));
            model.addAttribute("successMessage", messageSource.getMessage("visit.sensitivity.recalculation.dismissed", null, LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            model.addAttribute("errorMessage", messageSource.getMessage("visit.sensitivity.recalculation.error", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
        }
        
        List<DetectionParameter> detectionParameters = configurationService.findAllConfigurationsForUser(user);
        model.addAttribute("configurations", detectionParameters);
        model.addAttribute("activeSection", "visit-sensitivity");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("recalculationAdvised", detectionParameters.stream().anyMatch(this::calculateNeedsConfiguration));
        
        return "settings/visit-sensitivity";
    }
    
    @PostMapping("/preview")
    public String previewConfiguration(@ModelAttribute ConfigurationForm form,
                                       @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                       @RequestParam(required = false) String previewDate,
                                       @AuthenticationPrincipal User user,
                                       Model model) {
        DetectionParameter config = form.toConfiguration(ZoneId.of(timezone));

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

    private void clearTimeRange(User user) {
        List<DetectionParameter> allConfigurationsForUser = this.configurationService.findAllConfigurationsForUser(user);

        List<DetectionParameter> needsRecalculation = allConfigurationsForUser.stream()
                .filter(DetectionParameter::needsRecalculation).toList().reversed();

        if (needsRecalculation.isEmpty()) {
            throw new IllegalArgumentException("No configuration needs recalculation");
        }

        DetectionParameter earliest = needsRecalculation.getFirst();
        DetectionParameter latest = needsRecalculation.getLast();

        boolean recalculateAll = earliest.getValidSince() == null && latest.getValidSince() == null;
        if (recalculateAll) {
            log.debug("Clearing all time range");
            tripJdbcService.deleteAllForUser(user);
            processedVisitJdbcService.deleteAllForUser(user);
            visitJdbcService.deleteAllForUser(user);
            rawLocationPointJdbcService.markAllAsUnprocessedForUser(user);
        } else {
            Optional<DetectionParameter> parametersAfterEarliest = allConfigurationsForUser.stream().filter(p -> p.getValidSince() != null && p.getValidSince().isAfter(latest.getValidSince())).findFirst();

            if (parametersAfterEarliest.isPresent()) {
                log.debug("Clearing time range between {} and {}", earliest.getValidSince(), parametersAfterEarliest.get().getValidSince());
                this.tripJdbcService.deleteAllForUserBetween(user, earliest.getValidSince(), parametersAfterEarliest.get().getValidSince());
                this.processedVisitJdbcService.deleteAllForUserBetween(user, earliest.getValidSince(), parametersAfterEarliest.get().getValidSince());
                this.visitJdbcService.deleteAllForUserBetween(user, earliest.getValidSince(), parametersAfterEarliest.get().getValidSince());

                this.rawLocationPointJdbcService.markAllAsUnprocessedForUserBetween(user, earliest.getValidSince(), parametersAfterEarliest.get().getValidSince());
            } else {
                log.debug("Clearing time range after {}", earliest.getValidSince());
                this.tripJdbcService.deleteAllForUserAfter(user, earliest.getValidSince());
                this.processedVisitJdbcService.deleteAllForUserAfter(user, earliest.getValidSince());
                this.visitJdbcService.deleteAllForUserAfter(user, earliest.getValidSince());

                this.rawLocationPointJdbcService.markAllAsUnprocessedForUserAfter(user, earliest.getValidSince());
            }
        }
        allConfigurationsForUser.forEach(config -> this.configurationService.updateConfiguration(config.withNeedsRecalculation(false)));
        processingPipelineTrigger.start();
        log.debug("Recalculation of all configurations completed");
    }

    private String validateConfiguration(DetectionParameter config, User user) {
        if (config.getValidSince() == null) {
            return null;
        }

        if (config.getId() == null) {
            List<DetectionParameter> existingConfigs = configurationService.findAllConfigurationsForUser(user);
            boolean dateExists = existingConfigs.stream()
                .anyMatch(existing -> existing.getValidSince() != null && 
                         existing.getValidSince().equals(config.getValidSince()));
            
            if (dateExists) {
                return messageSource.getMessage("visit.sensitivity.validation.date.duplicate", null, LocaleContextHolder.getLocale());
            }
        } else {
            List<DetectionParameter> existingConfigs = configurationService.findAllConfigurationsForUser(user);
            boolean dateExists = existingConfigs.stream()
                .anyMatch(existing -> existing.getValidSince() != null && 
                         existing.getValidSince().equals(config.getValidSince()) &&
                         !existing.getId().equals(config.getId()));
            
            if (dateExists) {
                return messageSource.getMessage("visit.sensitivity.validation.date.duplicate", null, LocaleContextHolder.getLocale());
            }
        }
        return null;
    }
}
