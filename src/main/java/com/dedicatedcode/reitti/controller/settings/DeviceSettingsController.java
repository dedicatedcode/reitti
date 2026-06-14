package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ApiTokenJdbcService;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.I18nService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings/devices")
public class DeviceSettingsController {
    // Avatar constraints
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB
    private static final String[] ALLOWED_CONTENT_TYPES = {
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    };
    private static final List<String> DEFAULT_AVATARS = Arrays.asList(
            "gps.jpg", "smartwatch.jpg", "phone.jpg"
    );

    private final DeviceJdbcService deviceJdbcService;
    private final ApiTokenJdbcService apiTokenJdbcService;
    private final AvatarService avatarService;
    private final I18nService i18n;
    private final ContextPathHolder contextPathHolder;

    public DeviceSettingsController(DeviceJdbcService deviceJdbcService,
                                    ApiTokenJdbcService apiTokenJdbcService,
                                    AvatarService avatarService,
                                    I18nService i18n, ContextPathHolder contextPathHolder) {
        this.deviceJdbcService = deviceJdbcService;
        this.apiTokenJdbcService = apiTokenJdbcService;
        this.avatarService = avatarService;
        this.i18n = i18n;
        this.contextPathHolder = contextPathHolder;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                          Model model) {
        model.addAttribute("activeSection", "devices");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("defaultColors", getDefaultColors());
        model.addAttribute("defaultAvatars", DEFAULT_AVATARS);
        model.addAttribute("devices", deviceJdbcService.getAll(user).stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(),
                                        createAvatarUrl(user, d), avatarService.generateInitials(d.name()), d.enabled(), d.showOnMap(), d.defaultDevice(),
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
        model.addAttribute("defaultAvatars", DEFAULT_AVATARS);
        model.addAttribute("selectedColor", device.color());
        model.addAttribute("device",
                           new DeviceDTO(device.id(), device.name(), device.color(),
                                         createAvatarUrl(user, device), avatarService.generateInitials(device.name()),
                                         device.enabled(), device.showOnMap(), device.defaultDevice(),
                                         adjustInstant(device.createdAt(), timezone), adjustInstant(device.updatedAt(), timezone)));
        boolean hasAvatar = this.avatarService.getInfo(user.getId(), device.id()).isPresent();
        model.addAttribute("hasAvatar", hasAvatar);

        return "settings/devices :: device-edit-form";
    }

    @PostMapping
    public String createDevice(@AuthenticationPrincipal User user,
                               @RequestParam String name,
                               @RequestParam String color,
                               @RequestParam(required = false, defaultValue = "false") boolean enabled,
                               @RequestParam(required = false, defaultValue = "false") boolean showOnMap,
                               @RequestParam(required = false) String defaultAvatar,
                               @RequestParam(required = false) String removeAvatar,
                               @RequestParam(required = false) MultipartFile avatar,
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
                    false,
                    now,
                    now,
                    1L
            );
            Device saved = deviceJdbcService.save(device, user);
            ApiToken apiToken = new ApiToken(user, saved.name(), saved);
            this.apiTokenJdbcService.save(apiToken);

            // Handle avatar operations
            if (avatar != null && !avatar.isEmpty()) {
                handleAvatarUpload(avatar, user.getId(), saved.id(), model);
            } else if (StringUtils.hasText(defaultAvatar)) {
                handleDefaultAvatarSelection(defaultAvatar, user.getId(), saved.id(), model);
            }

            model.addAttribute("successMessage", i18n.translate("message.success.device.created"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.creation", e.getMessage()));
        }

        addDevicesToModel(user, timezone, model);

        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}")
    public String updateDevice(@PathVariable Long deviceId,
                               @AuthenticationPrincipal User user,
                               @RequestParam String name,
                               @RequestParam String color,
                               @RequestParam(required = false, defaultValue = "false") boolean enabled,
                               @RequestParam(required = false, defaultValue = "false") boolean showOnMap,
                               @RequestParam(required = false) String defaultAvatar,
                               @RequestParam(required = false) String removeAvatar,
                               @RequestParam(required = false) MultipartFile avatar,
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
                    existingDevice.defaultDevice(),
                    existingDevice.createdAt(),
                    Instant.now(),
                    existingDevice.version() + 1
            );
            deviceJdbcService.update(updatedDevice, user);


            // Handle avatar operations
            if ("true".equals(removeAvatar)) {
                avatarService.deleteAvatar(user.getId(), deviceId);
            } else if (avatar != null && !avatar.isEmpty()) {
                handleAvatarUpload(avatar, user.getId(), deviceId, model);
            } else if (StringUtils.hasText(defaultAvatar)) {
                handleDefaultAvatarSelection(defaultAvatar, user.getId(), deviceId, model);
            }

            model.addAttribute("successMessage", i18n.translate("message.success.device.updated"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.update", e.getMessage()));
        }

        addDevicesToModel(user, timezone, model);

        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}/toggle")
    public String toggleDevice(@PathVariable Long deviceId, 
                              @AuthenticationPrincipal User user,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {
        try {
            Device device = this.deviceJdbcService.find(user, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
            Device updatedDevice = new Device(
                    device.id(),
                    device.name(),
                    !device.enabled(),
                    device.showOnMap(),
                    device.color(),
                    device.defaultDevice(),
                    device.createdAt(),
                    Instant.now(),
                    device.version() + 1
            );
            this.deviceJdbcService.update(updatedDevice, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.toggled"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.toggle", e.getMessage()));
        }

        addDevicesToModel(user, timezone, model);

        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}/set-default")
    @Transactional
    public String setToDefault(@PathVariable Long deviceId,
                              @AuthenticationPrincipal User user,
                              @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                              Model model) {
        try {

            Device device = this.deviceJdbcService.find(user, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
            Device oldDefaultDevice = this.deviceJdbcService.getDefaultDevice(user);

            oldDefaultDevice = oldDefaultDevice.withDefaultDevice(false);
            device = device.withDefaultDevice(true);
            this.deviceJdbcService.update(oldDefaultDevice, user);
            this.deviceJdbcService.update(device, user);
            model.addAttribute("successMessage", i18n.translate("message.success.device.default-device", device.name()));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.toggle", e.getMessage()));
        }

        addDevicesToModel(user, timezone, model);

        return "settings/devices :: devices-content";
    }

    @PostMapping("/{deviceId}/delete")
    public String deleteDevice(@PathVariable Long deviceId, @AuthenticationPrincipal User user,
                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                               Model model) {
        try {
            Device deviceToDelete = this.deviceJdbcService.find(user, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

            if (deviceToDelete.defaultDevice()) {
                model.addAttribute("errorMessage", i18n.translate("message.error.device.deletion.default"));
            } else {
                deviceJdbcService.delete(deviceToDelete, user);
                model.addAttribute("successMessage", i18n.translate("message.success.device.deleted"));
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("message.error.device.deletion", e.getMessage()));
        }

        addDevicesToModel(user, timezone, model);

        // Return the devices-content fragment
        return "settings/devices :: devices-content";
    }

    private void handleAvatarUpload(MultipartFile avatar, Long userId, Long deviceId, Model model) {
        if (avatar != null && !avatar.isEmpty()) {
            try {
                // Validate file size
                if (avatar.getSize() > MAX_AVATAR_SIZE) {
                    model.addAttribute("avatarError", i18n.translate("users.avatar.error.to-large"));
                    return;
                }

                // Validate content type
                String contentType = avatar.getContentType();
                if (contentType == null || !isAllowedContentType(contentType)) {
                    model.addAttribute("avatarError", i18n.translate("users.avatar.error.invalid-file-type"));
                    return;
                }

                byte[] imageData = avatar.getBytes();
                this.avatarService.updateAvatar(userId, deviceId, contentType, imageData);

            } catch (IOException e) {
                model.addAttribute("avatarError", i18n.translate("devices.avatar.error.generic", e.getMessage()));
            }
        }
    }

    private void handleDefaultAvatarSelection(String defaultAvatar, Long userId, Long deviceId, Model model) {
        try {
            if (!DEFAULT_AVATARS.contains(defaultAvatar)) {
                model.addAttribute("avatarError", "Invalid default avatar selection.");
                return;
            }
            ClassPathResource resource = new ClassPathResource("static/img/avatars/default/" + defaultAvatar);
            if (!resource.exists()) {
                model.addAttribute("avatarError", "Default avatar file not found.");
                return;
            }

            byte[] imageData = resource.getInputStream().readAllBytes();
            String mimeType = "image/jpeg";

            this.avatarService.updateAvatar(userId, deviceId, mimeType, imageData);

        } catch (IOException e) {
            model.addAttribute("avatarError", "Error processing default avatar: " + e.getMessage());
        }
    }

    private String createAvatarUrl(User user, Device device) {
        if (this.avatarService.getInfo(user.getId(), device.id()).isPresent()) {
            return String.format(contextPathHolder.getContextPath() + "/avatars/%d/%d?ts=%s", user.getId(), device.id(), Instant.now().toEpochMilli());
        } else {
            return null;
        }
    }

    private boolean isAllowedContentType(String contentType) {
        for (String allowed : ALLOWED_CONTENT_TYPES) {
            if (allowed.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    public record DeviceDTO(Long id, String name, String color, String avatarUrl, String avatarFallback,
                            boolean enabled, boolean showOnMap,
                            boolean defaultDevice,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private void addDevicesToModel(User user, ZoneId timezone, Model model) {
        List<Device> devices = deviceJdbcService.getAll(user);
        model.addAttribute("devices", devices.stream()
                .map(d -> new DeviceDTO(d.id(), d.name(), d.color(), createAvatarUrl(user, d), avatarService.generateInitials(d.name()), d.enabled(), d.showOnMap(), d.defaultDevice(),
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
