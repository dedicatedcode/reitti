package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/settings/devices")
public class DeviceSettingsController {

    private final DeviceJdbcService deviceJdbcService;
    private final MessageSource messageSource;

    public DeviceSettingsController(DeviceJdbcService deviceJdbcService,
                                    MessageSource messageSource) {
        this.deviceJdbcService = deviceJdbcService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                          Model model) {
        model.addAttribute("activeSection", "devices");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("devices", deviceJdbcService.getAll(user).stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), d.enabled(), d.showOnMap(), 
                        adjustInstant(d.createdAt(), timezone), adjustInstant(d.updatedAt(), timezone)))
                .toList());
        return "settings/devices";
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
            model.addAttribute("successMessage", getMessage("message.success.device.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.device.creation", e.getMessage()));
        }

        // Get updated device list and add to model
        List<Device> devices = deviceJdbcService.getAll(user);
        model.addAttribute("devices", devices.stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), d.enabled(), d.showOnMap(),
                        adjustInstant(d.createdAt(), timezone), adjustInstant(d.updatedAt(), timezone)))
                .toList());

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
            model.addAttribute("successMessage", getMessage("message.success.device.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("message.error.device.deletion", e.getMessage()));
        }

        // Get updated device list and add to model
        List<Device> devices = deviceJdbcService.getAll(user);
        model.addAttribute("devices", devices.stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), d.enabled(), d.showOnMap(),
                        adjustInstant(d.createdAt(), timezone), adjustInstant(d.updatedAt(), timezone)))
                .toList());

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    public record DeviceDTO(Long id, String name, String color, boolean enabled, boolean showOnMap,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private LocalDateTime adjustInstant(Instant instant, ZoneId zoneId) {
        if (instant == null || zoneId == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId).toLocalDateTime();
    }
}
