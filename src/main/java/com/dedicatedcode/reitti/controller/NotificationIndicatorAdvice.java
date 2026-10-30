package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.repository.NotificationSettingsJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the unread notification count to every view so the navigation can render the bell badge
 * without each controller having to care about notifications.
 */
@ControllerAdvice
public class NotificationIndicatorAdvice {
    private static final Logger log = LoggerFactory.getLogger(NotificationIndicatorAdvice.class);

    private final NotificationJdbcService notificationJdbcService;
    private final NotificationSettingsJdbcService settingsJdbcService;

    public NotificationIndicatorAdvice(NotificationJdbcService notificationJdbcService,
                                       NotificationSettingsJdbcService settingsJdbcService) {
        this.notificationJdbcService = notificationJdbcService;
        this.settingsJdbcService = settingsJdbcService;
    }

    @ModelAttribute("notificationIndicator")
    public NotificationIndicator notificationIndicator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())
                || !(authentication.getPrincipal() instanceof User user)) {
            return NotificationIndicator.hidden();
        }
        try {
            boolean showBadge = settingsJdbcService.isBadgeVisible(user.getId());
            long unread = notificationJdbcService.countUnread(user.getId());
            return new NotificationIndicator(showBadge, unread);
        } catch (Exception e) {
            log.debug("Could not load notification indicator for user [{}]", user.getId(), e);
            return NotificationIndicator.hidden();
        }
    }

    public record NotificationIndicator(boolean showBadge, long unreadCount) {
        public static NotificationIndicator hidden() {
            return new NotificationIndicator(false, 0);
        }

        public boolean hasUnread() {
            return showBadge && unreadCount > 0;
        }
    }
}
