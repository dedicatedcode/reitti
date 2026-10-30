package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.notification.NotificationChannel;
import com.dedicatedcode.reitti.model.notification.NotificationChannelConfig;
import com.dedicatedcode.reitti.model.notification.NotificationSettings;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationChannelJdbcService;
import com.dedicatedcode.reitti.repository.NotificationSettingsJdbcService;
import com.dedicatedcode.reitti.repository.NotificationTypePreferenceJdbcService;
import com.dedicatedcode.reitti.service.notification.NotificationChannelRegistry;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.NotificationTypeRegistry;
import com.dedicatedcode.reitti.service.notification.type.TestNotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings/notifications")
public class NotificationSettingsController {
    private static final Logger log = LoggerFactory.getLogger(NotificationSettingsController.class);

    private final NotificationSettingsJdbcService settingsJdbcService;
    private final NotificationTypePreferenceJdbcService typePreferenceJdbcService;
    private final NotificationChannelJdbcService channelJdbcService;
    private final NotificationTypeRegistry typeRegistry;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationService notificationService;
    private final boolean dataManagementEnabled;

    public NotificationSettingsController(NotificationSettingsJdbcService settingsJdbcService,
                                          NotificationTypePreferenceJdbcService typePreferenceJdbcService,
                                          NotificationChannelJdbcService channelJdbcService,
                                          NotificationTypeRegistry typeRegistry,
                                          NotificationChannelRegistry channelRegistry,
                                          NotificationService notificationService,
                                          @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.settingsJdbcService = settingsJdbcService;
        this.typePreferenceJdbcService = typePreferenceJdbcService;
        this.channelJdbcService = channelJdbcService;
        this.typeRegistry = typeRegistry;
        this.channelRegistry = channelRegistry;
        this.notificationService = notificationService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "notifications");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        addSettingsToModel(user, model);
        addChannelsToModel(user, model);
        return "settings/notifications";
    }

    @PostMapping("/settings")
    public String saveSettings(@AuthenticationPrincipal User user,
                               @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
                               @RequestParam(value = "showBadge", defaultValue = "false") boolean showBadge,
                               RedirectAttributes redirectAttributes) {
        NotificationSettings current = settingsJdbcService.getOrCreate(user.getId());
        settingsJdbcService.save(current.withEnabled(enabled).withShowBadge(showBadge));
        redirectAttributes.addFlashAttribute("successMessage", "notifications.settings.saved");
        return "redirect:/settings/notifications";
    }

    @PostMapping("/types")
    public String saveTypes(@AuthenticationPrincipal User user,
                            @RequestParam(value = "typeKeys", required = false) List<String> enabledTypeKeys,
                            RedirectAttributes redirectAttributes) {
        List<String> enabled = enabledTypeKeys == null ? List.of() : enabledTypeKeys;
        for (NotificationType type : typeRegistry.forUser(user)) {
            typePreferenceJdbcService.save(user.getId(), type.key(), enabled.contains(type.key()));
        }
        redirectAttributes.addFlashAttribute("successMessage", "notifications.types.saved");
        return "redirect:/settings/notifications";
    }

    @PostMapping("/channels/{key}")
    public String saveChannel(@AuthenticationPrincipal User user,
                              @PathVariable String key,
                              @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
                              @RequestParam Map<String, String> params,
                              RedirectAttributes redirectAttributes) {
        NotificationChannel channel = channelRegistry.find(key).orElse(null);
        if (channel == null || channel.builtIn()) {
            redirectAttributes.addFlashAttribute("errorMessage", "notifications.channels.unknown");
            return "redirect:/settings/notifications";
        }
        Map<String, String> config = extractConfig(params);
        List<String> errors = channel.validate(config);
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", errors.getFirst());
            return "redirect:/settings/notifications";
        }
        NotificationChannelConfig existing = channelJdbcService.findByUserAndKey(user.getId(), key)
                .orElse(NotificationChannelConfig.empty(user.getId(), key));
        channelJdbcService.save(new NotificationChannelConfig(
                existing.id(), user.getId(), key, enabled, config,
                existing.createdAt(), existing.updatedAt(), existing.version()));
        redirectAttributes.addFlashAttribute("successMessage", "notifications.channels.saved");
        return "redirect:/settings/notifications";
    }

    @PostMapping("/channels/{key}/test")
    public String testChannel(@AuthenticationPrincipal User user,
                              @PathVariable String key,
                              @RequestParam Map<String, String> params,
                              RedirectAttributes redirectAttributes) {
        NotificationChannel channel = channelRegistry.find(key).orElse(null);
        if (channel == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "notifications.channels.unknown");
            return "redirect:/settings/notifications";
        }
        try {
            boolean success;
            if (channel.builtIn()) {
                success = channel.test(user, Map.of());
                if (success) {
                    NotificationType testType = typeRegistry.find(TestNotificationType.KEY).orElse(null);
                    notificationService.broadcastUnreadCount(user, null,
                            testType != null ? testType.key() : null,
                            testType != null ? testType.presentation() : null);
                }
            } else {
                Map<String, String> config = extractConfig(params);
                List<String> errors = channel.validate(config);
                if (!errors.isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage", errors.getFirst());
                    return "redirect:/settings/notifications";
                }
                success = channel.test(user, config);
            }
            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "notifications.channels.test.sent");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "notifications.channels.test.failed");
            }
        } catch (Exception e) {
            log.warn("Notification channel test failed for [{}]", key, e);
            redirectAttributes.addFlashAttribute("errorMessage", "notifications.channels.test.failed");
        }
        return "redirect:/settings/notifications";
    }

    private void addSettingsToModel(User user, Model model) {
        NotificationSettings settings = settingsJdbcService.getOrCreate(user.getId());
        List<NotificationType> types = typeRegistry.forUser(user);
        Map<String, Boolean> preferences = typePreferenceJdbcService.findPreferencesForUser(user.getId());
        Map<String, Boolean> effective = new LinkedHashMap<>();
        for (NotificationType type : types) {
            effective.put(type.key(), preferences.getOrDefault(type.key(), type.defaultEnabled()));
        }
        model.addAttribute("notificationSettings", settings);
        model.addAttribute("notificationTypes", types);
        model.addAttribute("notificationTypeEnabled", effective);
    }

    private void addChannelsToModel(User user, Model model) {
        List<ChannelView> channels = new ArrayList<>();
        for (NotificationChannel channel : channelRegistry.all()) {
            NotificationChannelConfig config = channelJdbcService
                    .findByUserAndKey(user.getId(), channel.key())
                    .orElse(NotificationChannelConfig.empty(user.getId(), channel.key()));
            channels.add(new ChannelView(channel, config));
        }

        channels.sort(Comparator.comparing((ChannelView view) -> !view.channel().builtIn())
                .thenComparing(view -> view.channel().key()));
        model.addAttribute("notificationChannels", channels);
    }

    private Map<String, String> extractConfig(Map<String, String> params) {
        Map<String, String> config = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("cfg.")) {
                config.put(key.substring(4), value);
            }
        });
        return config;
    }

    public record ChannelView(NotificationChannel channel, NotificationChannelConfig config) {
        public boolean enabled() {
            return config.enabled();
        }

        public String value(String key) {
            return config.value(key, "");
        }
    }
}
