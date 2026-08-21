package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.jobs.TransportModeRecalculationTask;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/transportation-modes")
public class TransportationModesController {

    private static final Logger log = LoggerFactory.getLogger(TransportationModesController.class);
    private final TransportModeJdbcService transportModeJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final JobDetail recalculationJobTask;
    private final JobSchedulingService jobSchedulingService;
    private final boolean dataManagementEnabled;

    public TransportationModesController(TransportModeJdbcService transportModeJdbcService,
                                         UserSettingsJdbcService userSettingsJdbcService,
                                         @Qualifier("transportModeRecalculationJob") JobDetail transportModeRecalculationTask,
                                         JobSchedulingService jobSchedulingService,
                                         @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.recalculationJobTask = transportModeRecalculationTask;
        this.jobSchedulingService = jobSchedulingService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String transportationModes(@AuthenticationPrincipal User user, Model model) {
        if (user.getUserType() == UserType.LIVE_DATA_ONLY) {
            model.addAttribute("activeSection", "transportation-modes");
            model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
            model.addAttribute("dataManagementEnabled", dataManagementEnabled);
            return "settings/unavailable";
        }
        addConfigsToModel(user, model);
        model.addAttribute("activeSection", "transportation-modes");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        
        return "settings/transportation-modes";
    }

    @GetMapping("/create-form")
    public String createForm(@AuthenticationPrincipal User user, Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        model.addAttribute("availableModes", getAvailableModesToAdd(configs));
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
        model.addAttribute("defaultColors", getDefaultColors());
        return "settings/fragments/transportation-modes :: transportation-mode-edit-form";
    }

    @GetMapping("/{mode}/edit")
    public String editTransportMode(@AuthenticationPrincipal User user,
                                    @PathVariable TransportMode mode,
                                    Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        TransportModeConfig config = transportModeJdbcService.getTransportModeConfigs(user).stream()
                .filter(c -> c.mode() == mode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Transport mode not found: " + mode));
        model.addAttribute("config", config);
        model.addAttribute("selectedColor", config.color());
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
        model.addAttribute("defaultColors", getDefaultColors());
        return "settings/fragments/transportation-modes :: transportation-mode-edit-form";
    }

    @PostMapping("/add")
    public String addTransportMode(@AuthenticationPrincipal User user,
                                   @RequestParam TransportMode mode,
                                   @RequestParam(required = false) Double maxSpeed,
                                   @RequestParam(required = false) UnitSystem unitSystem,
                                   @RequestParam(required = false) String color,
                                   @RequestParam(required = false) String icon,
                                   Model model) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            // Check if mode already exists
            boolean exists = configs.stream().anyMatch(config -> config.mode() == mode);
            if (exists) {
                model.addAttribute("errorMessage", "transportation.modes.error.already.exists");
            } else {
                // Convert to km/h if input was in mph
                Double maxKmh;
                if (maxSpeed != null && unitSystem == UnitSystem.IMPERIAL) {
                    maxKmh = mphToKmh(maxSpeed);
                } else {
                    maxKmh = maxSpeed;
                }

                // Check for duplicate maxKmh values (only if maxKmh is not null)
                boolean duplicateMaxKmh = configs.stream()
                        .anyMatch(config -> Objects.equals(config.maxKmh(), maxKmh));
                if (duplicateMaxKmh) {
                    model.addAttribute("errorMessage", "transportation.modes.error.duplicate.max.kmh");
                } else {
                    configs.add(new TransportModeConfig(mode, maxKmh, color, icon));
                    transportModeJdbcService.setTransportModeConfigs(user, configs);
                    model.addAttribute("successMessage", "transportation.modes.success.added");
                }
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "transportation.modes.error.add");
        }

        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
    }

    @PostMapping("/{mode}/update")
    public String updateTransportMode(@AuthenticationPrincipal User user,
                                      @PathVariable TransportMode mode,
                                      @RequestParam(required = false) Double maxSpeed,
                                      @RequestParam(required = false) UnitSystem unitSystem,
                                      @RequestParam(required = false) String color,
                                      @RequestParam(required = false) String icon,
                                      Model model) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);

            List<TransportModeConfig> updatedConfigs = configs.stream()
                    .map(config -> config.mode() == mode ? updateConfig(config, maxSpeed, unitSystem, color, icon) : config)
                    .collect(Collectors.toList());

            transportModeJdbcService.setTransportModeConfigs(user, updatedConfigs);

            model.addAttribute("successMessage", "transportation.modes.success.updated");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "transportation.modes.error.update");
        }

        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
    }

    private TransportModeConfig updateConfig(TransportModeConfig config, Double maxSpeed, UnitSystem unitSystem, String color, String icon) {
        Double maxKmh;
        if (maxSpeed == null) {
            maxKmh = config.maxKmh();
        } else {
            maxKmh = unitSystem == UnitSystem.IMPERIAL ? mphToKmh(maxSpeed) : maxSpeed;
        }
        String newColor = color == null ? config.color() : (color.isEmpty() ? null : color);
        String newIcon = icon == null ? config.icon() : (icon.isEmpty() ? null : icon);
        return new TransportModeConfig(config.mode(), maxKmh, newColor, newIcon);
    }

    @PostMapping("/{mode}/delete")
    public String deleteTransportMode(@AuthenticationPrincipal User user,
                                      @PathVariable TransportMode mode,
                                      Model model) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            List<TransportModeConfig> filteredConfigs = configs.stream()
                    .filter(config -> config.mode() != mode)
                    .collect(Collectors.toList());
            
            transportModeJdbcService.setTransportModeConfigs(user, filteredConfigs);
            
            model.addAttribute("successMessage", "transportation.modes.success.deleted");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "transportation.modes.error.delete");
        }

        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
    }

    @PostMapping("/content")
    public String getTransportationModesContent(@AuthenticationPrincipal User user, Model model) {
        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
    }

    private void addConfigsToModel(User user, Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        model.addAttribute("configs", configs);
        model.addAttribute("availableModes", getAvailableModesToAdd(configs));
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
    }

    private List<TransportMode> getAvailableModesToAdd(List<TransportModeConfig> configs) {
        List<TransportMode> usedModes = configs.stream()
                .map(TransportModeConfig::mode)
                .toList();
        
        return Arrays.stream(TransportMode.values())
                .filter(mode -> !usedModes.contains(mode))
                .filter(mode -> mode != TransportMode.UNKNOWN)
                .collect(Collectors.toList());
    }
    
    private Double mphToKmh(Double mph) {
        return mph * 1.60934;
    }

    private Map<String, String> getDefaultColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("#f1ba63", "Orange");
        colors.put("#ff6b6b", "Red");
        colors.put("#4ecdc4", "Teal");
        colors.put("#45b7d1", "Blue");
        colors.put("#96ceb4", "Green");
        colors.put("#a29bfe", "Purple");
        return colors;
    }
    
    @PostMapping("/reclassify")
    public String reclassifyTrips(@AuthenticationPrincipal User user, Model model) {
        try {
            log.debug("Scheduling recalculation task");
            this.jobSchedulingService.enqueueTask(recalculationJobTask, new TransportModeRecalculationTask.TaskData(user),
                                          JobSchedulingService.Metadata.builder()
                                                  .user(user)
                                                  .friendlyName("Recalculation for changed Transportation Mode Settings")
                                                  .jobType(JobType.DATA_RECALCULATION).build());
            model.addAttribute("reclassifyStatus", "started");
            model.addAttribute("message", "transportation.modes.reclassify.started");
            
        } catch (Exception e) {
            model.addAttribute("reclassifyStatus", "error");
            model.addAttribute("message", "transportation.modes.reclassify.error");
        }
        
        return "settings/transportation-modes :: reclassify-status";
    }
}
