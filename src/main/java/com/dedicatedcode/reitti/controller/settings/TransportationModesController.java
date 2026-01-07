package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.processing.TransportModeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/transportation-modes")
public class TransportationModesController {

    private static final Logger log = LoggerFactory.getLogger(TransportationModesController.class);
    private final TripJdbcService tripJdbcService;
    private final TransportModeJdbcService transportModeJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final TransportModeService transportModeService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final boolean dataManagementEnabled;

    public TransportationModesController(TripJdbcService tripJdbcService,
                                         TransportModeJdbcService transportModeJdbcService,
                                         UserSettingsJdbcService userSettingsJdbcService,
                                         TransportModeService transportModeService,
                                         RawLocationPointJdbcService rawLocationPointJdbcService,
                                         @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.tripJdbcService = tripJdbcService;
        this.transportModeJdbcService = transportModeJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.transportModeService = transportModeService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String transportationModes(@AuthenticationPrincipal User user, Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        List<TransportMode> availableModes = getAvailableModesToAdd(configs);
        
        model.addAttribute("configs", configs);
        model.addAttribute("availableModes", availableModes);
        model.addAttribute("activeSection", "transportation-modes");
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
        
        return "settings/transportation-modes";
    }

    @PostMapping("/add")
    public String addTransportMode(@AuthenticationPrincipal User user,
                                   @RequestParam TransportMode mode,
                                   @RequestParam(required = false) Double maxSpeed,
                                   @RequestParam(required = false) UnitSystem unitSystem,
                                   RedirectAttributes redirectAttributes) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            // Check if mode already exists
            boolean exists = configs.stream().anyMatch(config -> config.mode() == mode);
            if (exists) {
                redirectAttributes.addFlashAttribute("errorMessage", "transportation.modes.error.already.exists");
                return "redirect:/settings/transportation-modes";
            }
            
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
                redirectAttributes.addFlashAttribute("errorMessage", "transportation.modes.error.duplicate.max.kmh");
                return "redirect:/settings/transportation-modes";
            }

            configs.add(new TransportModeConfig(mode, maxKmh));
            transportModeJdbcService.setTransportModeConfigs(user, configs);
            
            redirectAttributes.addFlashAttribute("successMessage", "transportation.modes.success.added");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "transportation.modes.error.add");
        }
        
        return "redirect:/settings/transportation-modes";
    }

    @PostMapping("/{mode}/update")
    public String updateTransportMode(@AuthenticationPrincipal User user,
                                      @PathVariable TransportMode mode,
                                      @RequestParam(required = false) Double maxSpeed,
                                      @RequestParam(required = false) UnitSystem unitSystem,
                                      RedirectAttributes redirectAttributes) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            // Convert to km/h if input was in mph
            Double maxKmh;
            if (maxSpeed != null && unitSystem == UnitSystem.IMPERIAL) {
                maxKmh = mphToKmh(maxSpeed);
            } else {
                maxKmh = maxSpeed;
            }
            
            List<TransportModeConfig> updatedConfigs = configs.stream()
                    .map(config -> config.mode() == mode ? new TransportModeConfig(mode, maxKmh) : config)
                    .collect(Collectors.toList());
            
            transportModeJdbcService.setTransportModeConfigs(user, updatedConfigs);
            
            redirectAttributes.addFlashAttribute("successMessage", "transportation.modes.success.updated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "transportation.modes.error.update");
        }
        
        return "redirect:/settings/transportation-modes";
    }

    @PostMapping("/{mode}/delete")
    public String deleteTransportMode(@AuthenticationPrincipal User user,
                                      @PathVariable TransportMode mode,
                                      RedirectAttributes redirectAttributes) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            List<TransportModeConfig> filteredConfigs = configs.stream()
                    .filter(config -> config.mode() != mode)
                    .collect(Collectors.toList());
            
            transportModeJdbcService.setTransportModeConfigs(user, filteredConfigs);
            
            redirectAttributes.addFlashAttribute("successMessage", "transportation.modes.success.deleted");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "transportation.modes.error.delete");
        }
        
        return "redirect:/settings/transportation-modes";
    }

    @PostMapping("/content")
    public String getTransportationModesContent(@AuthenticationPrincipal User user, Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        List<TransportMode> availableModes = getAvailableModesToAdd(configs);
        
        model.addAttribute("configs", configs);
        model.addAttribute("availableModes", availableModes);
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
        
        return "settings/transportation-modes :: transportation-modes-content";
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
    
    @PostMapping("/reclassify")
    public String reclassifyTrips(@AuthenticationPrincipal User user, Model model) {
        try {
            // Start async reclassification
            CompletableFuture.runAsync(() -> {
                tripJdbcService.findByUser(user).forEach(trip -> {
                    Instant startTime = trip.getStartTime();
                    Instant endTime = trip.getEndTime();
                    List<RawLocationPoint> tripPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startTime, endTime.plus(1, ChronoUnit.MILLIS));
                    TransportMode transportMode = this.transportModeService.inferTransportMode(user, tripPoints, startTime, endTime);
                    if (transportMode != trip.getTransportModeInferred()) {
                        log.trace("Reclassified trip {} from {} to {} to mode {}", trip.getId(), startTime, endTime, transportMode);
                        trip = trip.withTransportMode(transportMode);
                        this.tripJdbcService.update(trip);
                    }
                });
            });
            
            model.addAttribute("reclassifyStatus", "started");
            model.addAttribute("message", "transportation.modes.reclassify.started");
            
        } catch (Exception e) {
            model.addAttribute("reclassifyStatus", "error");
            model.addAttribute("message", "transportation.modes.reclassify.error");
        }
        
        return "settings/transportation-modes :: reclassify-status";
    }
}
