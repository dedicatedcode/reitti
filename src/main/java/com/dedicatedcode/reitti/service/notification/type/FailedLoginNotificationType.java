package com.dedicatedcode.reitti.service.notification.type;

import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FailedLoginNotificationType implements NotificationType {

    public static final String KEY = "FAILED_LOGIN_ATTEMPTS";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public NotificationSeverity severity() {
        return NotificationSeverity.WARNING;
    }

    @Override
    public String nameKey() {
        return "notifications.type.failed-login.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.type.failed-login.description";
    }

    @Override
    public String titleKey() {
        return "notifications.type.failed-login.title";
    }

    @Override
    public String messageKey() {
        return "notifications.type.failed-login.message";
    }

    @Override
    public Object[] messageArgs(Map<String, Object> params) {
        return new Object[]{params.getOrDefault("username", "")};
    }

    @Override
    public boolean appliesTo(User user) {
        return user != null;
    }

    @Override
    public String actionUrl(Map<String, Object> params) {
        return "/settings/user-management";
    }

    @Override
    public String dedupeKey(Map<String, Object> params) {
        Object username = params.get("username");
        Object window = params.get("window");
        return username == null ? null : username + ":" + window;
    }
}
