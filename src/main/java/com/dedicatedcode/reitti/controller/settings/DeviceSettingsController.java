package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings/devices")
public class DeviceSettingsController {

    private final DeviceJdbcService deviceJdbcService;
    private final I18nService i18n;

    public DeviceSettingsController(DeviceJdbcService deviceJdbcService,
                                    I18nService i18n) {
        this.deviceJdbcService = deviceJdbcService;
        this.i18n = i18n;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                          Model model) {
        model.addAttribute("activeSection", "devices");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("defaultColors", getDefaultColors());
        model.addAttribute("devices", deviceJdbcService.getAll(user).stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), d.enabled(), d.showOnMap(), 
                        adjustInstant(d.createdAt(), timezone), adjustInstant(d.updatedAt(), timezone)))
                .toList());
        return "settings/devices";
    }

    @GetMapping("/edit/{deviceId}")
    public String editDevice(@PathVariable Long deviceId,
                            @AuthenticationPrincipal User user,
                            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                            Model model) {
        List<Device> devices = deviceJdbcService.getAll(user);
        Device device = devices.stream()
                .filter(d -> d.id().equals(deviceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        model.addAttribute("defaultColors", getDefaultColors());
        model.addAttribute("device", new DeviceDTO(device.id(), device.name(), device.color(), 
                device.enabled(), device.showOnMap(),
                adjustInstant(device.createdAt(), timezone), adjustInstant(device.updatedAt(), timezone)));
        
        return "settings/devices :: device-edit-form";
    }

    @PostMapping
    public String createDevice(@AuthenticationPrincipal User user,
                               @RequestParam String name,
                               @RequestParam String color,
                               @RequestParam(required = false, defaultValue = "true") boolean enabled,
                               @RequestParam(required = false, defaultValue = "true") boolean showOnMap,
                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                               Model model) {
        try {
            Instant now = Instant.now();
            Device device = new Device(
                    null,
                    name,
                    enabled,
                    showOnMap,
                    color,
                    now,
                    now,
                    1L
            );
            deviceJdbcService.save(device, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.creation", e.getMessage()));
        }

        // Get updated device list and add to model
        addDevicesToModel(user, timezone, model);

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}")
    public String updateDevice(@PathVariable Long deviceId,
                               @AuthenticationPrincipal User user,
                               @RequestParam String name,
                               @RequestParam String color,
                               @RequestParam(required = false, defaultValue = "true") boolean enabled,
                               @RequestParam(required = false, defaultValue = "true") boolean showOnMap,
                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                               Model model) {
        try {
            List<Device> devices = deviceJdbcService.getAll(user);
            Device existingDevice = devices.stream()
                    .filter(d -> d.id().equals(deviceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Device not found"));

            Device updatedDevice = new Device(
                    deviceId,
                    name,
                    enabled,
                    showOnMap,
                    color,
                    existingDevice.createdAt(),
                    Instant.now(),
                    existingDevice.version() + 1
            );
            deviceJdbcService.update(updatedDevice, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.updated"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.update", e.getMessage()));
        }

        // Get updated device list and add to model
        addDevicesToModel(user, timezone, model);

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}/toggle")
    public String toggleDevice(@PathVariable Long deviceId, 
                              @AuthenticationPrincipal User user,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {
        try {
            List<Device> devices = deviceJdbcService.getAll(user);
            Device device = devices.stream()
                    .filter(d -> d.id().equals(deviceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Device not found"));

            Device updatedDevice = new Device(
                    device.id(),
                    device.name(),
                    !device.enabled(),
                    device.showOnMap(),
                    device.color(),
                    device.createdAt(),
                    Instant.now(),
                    device.version() + 1
            );
            deviceJdbcService.update(updatedDevice, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.toggled"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.toggle", e.getMessage()));
        }

        // Get updated device list and add to model
        addDevicesToModel(user, timezone, model);

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}/delete")
    public String deleteDevice(@PathVariable Long deviceId, @AuthenticationPrincipal User user,
                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                               Model model) {
        try {
            List<Device> devices = deviceJdbcService.getAll(user);
            Device deviceToDelete = devices.stream()
                    .filter(d -> d.id().equals(deviceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Device not found"));

            deviceJdbcService.delete(deviceToDelete, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.deletion", e.getMessage()));
        }

        // Get updated device list and add to model
        addDevicesToModel(user, timezone, model);

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    public record DeviceDTO(Long id, String name, String color, boolean enabled, boolean showOnMap,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private void addDevicesToModel(User user, ZoneId timezone, Model model) {
        List<Device> devices = deviceJdbcService.getAll(user);
        model.addAttribute("devices", devices.stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), d.enabled(), d.showOnMap(),
                        adjustInstant(d.createdAt(), timezone), adjustInstant(d.updatedAt(), timezone)))
                .toList());
        model.addAttribute("defaultColors", getDefaultColors());
    }

    private Map<String, String> getDefaultColors() {
        return Map.of(
                "#f1ba63", "Orange",
                "#ff6b6b", "Red",
                "#4ecdc4", "Teal",
                "#45b7d1", "Blue",
                "#96ceb4", "Green",
                "#ffeaa7", "Yellow",
                "#dfe6e9", "Gray",
                "#a29bfe", "Purple"
        );
    }

    private LocalDateTime adjustInstant(Instant instant, ZoneId zoneId) {
        if (instant == null || zoneId == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId).toLocalDateTime();
    }
}
