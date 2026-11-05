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

import java.util.*;
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
                                   @RequestParam(required = false) Double minSpeed,
                                   @RequestParam(required = false) Double maxSpeed,
                                   @RequestParam(required = false) Double preferenceWeight,
                                   @RequestParam(required = false) Boolean enabled,
                                   @RequestParam(required = false) UnitSystem unitSystem,
                                   @RequestParam(required = false) String color,
                                   @RequestParam(required = false) String icon,
                                   Model model) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);

            boolean exists = configs.stream().anyMatch(config -> config.mode() == mode);
            if (exists) {
                model.addAttribute("errorMessage", "transportation.modes.error.already.exists");
            } else {
                Double minKmh = toKmh(minSpeed, unitSystem);
                Double maxKmh = toKmh(maxSpeed, unitSystem);
                if (minKmh == null || maxKmh == null) {
                    model.addAttribute("errorMessage", "transportation.modes.error.range.required");
                } else if (minKmh >= maxKmh) {
                    model.addAttribute("errorMessage", "transportation.modes.error.invalid.range");
                } else {
                    double preference = preferenceWeight == null ? 1.0 : preferenceWeight;
                    boolean isEnabled = enabled == null || enabled;
                    configs.add(new TransportModeConfig(mode, minKmh, maxKmh, preference, isEnabled, color, icon));
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
                                      @RequestParam(required = false) Double minSpeed,
                                      @RequestParam(required = false) Double maxSpeed,
                                      @RequestParam(required = false) Double preferenceWeight,
                                      @RequestParam(required = false) Boolean enabled,
                                      @RequestParam(required = false) UnitSystem unitSystem,
                                      @RequestParam(required = false) String color,
                                      @RequestParam(required = false) String icon,
                                      Model model) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);

            List<TransportModeConfig> updatedConfigs = configs.stream()
                    .map(config -> config.mode() == mode
                            ? updateConfig(config, minSpeed, maxSpeed, preferenceWeight, enabled, unitSystem, color, icon)
                            : config)
                    .collect(Collectors.toList());

            transportModeJdbcService.setTransportModeConfigs(user, updatedConfigs);
            model.addAttribute("successMessage", "transportation.modes.success.updated");
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", "transportation.modes.error.invalid.range");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "transportation.modes.error.update");
        }

        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
    }

    private TransportModeConfig updateConfig(TransportModeConfig config, Double minSpeed, Double maxSpeed,
                                             Double preferenceWeight, Boolean enabled, UnitSystem unitSystem,
                                             String color, String icon) {
        double minKmh = minSpeed == null ? config.minKmh() : toKmh(minSpeed, unitSystem);
        double maxKmh = maxSpeed == null ? config.maxKmh() : toKmh(maxSpeed, unitSystem);
        if (minKmh >= maxKmh) {
            throw new IllegalArgumentException("Minimum speed must be lower than maximum speed");
        }
        double preference = preferenceWeight == null ? config.preferenceWeight() : preferenceWeight;
        boolean isEnabled = enabled == null ? config.enabled() : enabled;
        String newColor = color == null ? config.color() : (color.isEmpty() ? null : color);
        String newIcon = icon == null ? config.icon() : (icon.isEmpty() ? null : icon);
        return new TransportModeConfig(config.mode(), minKmh, maxKmh, preference, isEnabled, newColor, newIcon);
    }

    @PostMapping("/sensitivity")
    public String updateSensitivity(@AuthenticationPrincipal User user,
                                    @RequestParam double modeChangeSensitivity,
                                    Model model) {
        transportModeJdbcService.setModeChangeSensitivity(user, modeChangeSensitivity);
        model.addAttribute("successMessage", "transportation.modes.success.sensitivity");
        addConfigsToModel(user, model);
        return "settings/transportation-modes :: transportation-modes-content";
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
        model.addAttribute("modeChangeSensitivity", transportModeJdbcService.getModeChangeSensitivity(user));
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

    private Double toKmh(Double speed, UnitSystem unitSystem) {
        if (speed == null) {
            return null;
        }
        return unitSystem == UnitSystem.IMPERIAL ? mphToKmh(speed) : speed;
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
            this.jobSchedulingService.enqueueTask(recalculationJobTask, new TransportModeRecalculationTask.TaskData(user.getId()),
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
