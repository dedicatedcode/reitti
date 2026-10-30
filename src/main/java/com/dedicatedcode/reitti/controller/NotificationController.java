package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.notification.NotificationPresentationService;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.NotificationTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The notification history / message center. It lives inside the settings layout and is reached
 * from the Status section of the settings navigation; the per-user configuration lives under
 * {@code /settings/notifications}.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private static final int PAGE_SIZE = 50;

    private final NotificationJdbcService notificationJdbcService;
    private final NotificationTypeRegistry typeRegistry;
    private final NotificationService notificationService;
    private final NotificationPresentationService presentationService;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;

    public NotificationController(NotificationJdbcService notificationJdbcService,
                                  NotificationTypeRegistry typeRegistry,
                                  NotificationService notificationService,
                                  NotificationPresentationService presentationService,
                                  I18nService i18n,
                                  @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.notificationJdbcService = notificationJdbcService;
        this.typeRegistry = typeRegistry;
        this.notificationService = notificationService;
        this.presentationService = presentationService;
        this.i18n = i18n;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "notifications");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        addInboxToModel(user, model);
        return "notifications";
    }

    @GetMapping("/inbox")
    public String inbox(@AuthenticationPrincipal User user, Model model) {
        addInboxToModel(user, model);
        return "fragments/notification-inbox :: inbox-section";
    }

    @PostMapping("/{id}/read")
    public String markRead(@AuthenticationPrincipal User user,
                           @PathVariable Long id,
                           Model model) {
        notificationJdbcService.markRead(user.getId(), id);
        broadcastUnreadCount(user);
        addInboxToModel(user, model);
        return "fragments/notification-inbox :: inbox-section";
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal User user, Model model) {
        notificationJdbcService.markAllRead(user.getId());
        broadcastUnreadCount(user);
        addInboxToModel(user, model);
        return "fragments/notification-inbox :: inbox-section";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal User user,
                         @PathVariable Long id,
                         Model model) {
        notificationJdbcService.delete(user.getId(), id);
        broadcastUnreadCount(user);
        addInboxToModel(user, model);
        return "fragments/notification-inbox :: inbox-section";
    }

    @PostMapping("/clear-all")
    public String clearAll(@AuthenticationPrincipal User user, Model model) {
        notificationJdbcService.clearAll(user.getId());
        broadcastUnreadCount(user);
        addInboxToModel(user, model);
        return "fragments/notification-inbox :: inbox-section";
    }

    @GetMapping("/pending")
    public String pending(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("pending", presentationService.findLatestPresentable(user).orElse(null));
        return "fragments/notification-presentation :: presentation";
    }

    /**
     * Dismisses a dialog/toast by marking it read and returns the next pending notification, if any.
     */
    @PostMapping("/{id}/dismiss")
    public String dismiss(@AuthenticationPrincipal User user,
                          @PathVariable Long id,
                          Model model) {
        notificationJdbcService.markRead(user.getId(), id);
        broadcastUnreadCount(user);
        model.addAttribute("pending", presentationService.findLatestPresentable(user).orElse(null));
        return "fragments/notification-presentation :: presentation";
    }

    @PostMapping("/{id}/open")
    public String open(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Optional<Notification> notification = notificationJdbcService.findForUser(user.getId(), id);
        if (notification.isEmpty()) {
            return "redirect:/notifications";
        }
        notificationJdbcService.markRead(user.getId(), id);
        broadcastUnreadCount(user);
        String actionUrl = notification.get().actionUrl();
        return "redirect:" + (actionUrl == null || actionUrl.isBlank() ? "/notifications" : actionUrl);
    }

    private void addInboxToModel(User user, Model model) {
        List<Notification> notifications = notificationJdbcService.findForUser(user.getId(), false, PAGE_SIZE, 0);
        Map<String, String> typeNames = new LinkedHashMap<>();
        for (Notification notification : notifications) {
            typeNames.computeIfAbsent(notification.typeKey(), typeKey ->
                    typeRegistry.find(typeKey)
                            .map(type -> i18n.translate(type.nameKey()))
                            .orElse(typeKey));
        }
        Set<String> htmlTypeKeys = typeRegistry.forUser(user).stream()
                .filter(NotificationType::htmlMessage)
                .map(NotificationType::key)
                .collect(Collectors.toSet());
        model.addAttribute("notifications", notifications);
        model.addAttribute("notificationTypeNames", typeNames);
        model.addAttribute("notificationHtmlTypes", htmlTypeKeys);
        model.addAttribute("unreadCount", notificationJdbcService.countUnread(user.getId()));
    }

    private void broadcastUnreadCount(User user) {
        try {
            notificationService.broadcastUnreadCount(user, null);
        } catch (Exception e) {
            log.debug("Could not broadcast notification count", e);
        }
    }
}
