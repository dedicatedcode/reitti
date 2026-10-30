package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationTestMessageFactory {

    private final NotificationRenderer renderer;
    private final I18nService i18n;

    public NotificationTestMessageFactory(NotificationRenderer renderer, I18nService i18n) {
        this.renderer = renderer;
        this.i18n = i18n;
    }

    public NotificationMessage create(User user) {
        Locale locale = renderer.localeFor(user);
        return new NotificationMessage(
                user.getId(),
                "TEST",
                NotificationSeverity.WARNING,
                i18n.translate("notifications.test.title", locale),
                i18n.translate("notifications.test.message", locale),
                Map.of(),
                "/settings/notifications",
                null,
                Instant.now());
    }
}
