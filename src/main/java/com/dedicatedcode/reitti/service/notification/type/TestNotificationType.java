package com.dedicatedcode.reitti.service.notification.type;

import com.dedicatedcode.reitti.model.notification.NotificationPresentation;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Hidden type used by the "Send test" buttons in the settings. It never shows up in the list of
 * configurable types.
 */
@Component
public class TestNotificationType implements NotificationType {

    public static final String KEY = "TEST";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public NotificationSeverity severity() {
        return NotificationSeverity.INFO;
    }

    @Override
    public String nameKey() {
        return "notifications.type.test.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.type.test.description";
    }

    @Override
    public String titleKey() {
        return "notifications.test.title";
    }

    @Override
    public String messageKey() {
        return "notifications.test.message";
    }

    @Override
    public NotificationPresentation presentation() {
        return NotificationPresentation.TOAST;
    }

    @Override
    public boolean hidden() {
        return true;
    }

    @Override
    public String actionUrl(java.util.Map<String, Object> params) {
        return "/settings/notifications";
    }
}
