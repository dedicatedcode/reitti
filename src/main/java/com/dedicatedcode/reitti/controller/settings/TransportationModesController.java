package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.processing.TransportModeJdbcService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings/transportation-modes")
public class TransportationModesController {

    private final TransportModeJdbcService transportModeJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public TransportationModesController(TransportModeJdbcService transportModeJdbcService, 
                                       UserSettingsJdbcService userSettingsJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping
    public String transportationModes(@AuthenticationPrincipal User user, Model model) {
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        List<TransportMode> availableModes = getAvailableModesToAdd(configs);
        
        model.addAttribute("configs", configs);
        model.addAttribute("availableModes", availableModes);
        model.addAttribute("activeSection", "transportation-modes");
        model.addAttribute("dataManagementEnabled", true);
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("unitSystem", userSettings.getUnitSystem());
        model.addAttribute("isImperial", userSettings.getUnitSystem() == UnitSystem.IMPERIAL);
        
        return "settings/transportation-modes";
    }

    @PostMapping("/add")
    public String addTransportMode(@AuthenticationPrincipal User user,
                                   @RequestParam TransportMode mode,
                                   @RequestParam(required = false) Double maxSpeed,
                                   @RequestParam(required = false) String unitSystem,
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
            Double maxKmh = maxSpeed;
            if (maxSpeed != null && "IMPERIAL".equals(unitSystem)) {
                maxKmh = mphToKmh(maxSpeed);
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
                                      @RequestParam(required = false) String unitSystem,
                                      RedirectAttributes redirectAttributes) {
        try {
            List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
            
            // Convert to km/h if input was in mph
            Double maxKmh = maxSpeed;
            if (maxSpeed != null && "IMPERIAL".equals(unitSystem)) {
                maxKmh = mphToKmh(maxSpeed);
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
    
    private Double kmhToMph(Double kmh) {
        return kmh / 1.60934;
    }
}
