package com.dedicatedcode.reitti.service.notification.type;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.notification.NotificationPresentation;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewVersionNotificationType implements NotificationType {

    public static final String KEY = "NEW_VERSION_AVAILABLE";

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
        return "notifications.type.new-version.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.type.new-version.description";
    }

    @Override
    public String titleKey() {
        return "notifications.type.new-version.title";
    }

    @Override
    public String messageKey() {
        return "notifications.type.new-version.message";
    }

    @Override
    public Object[] messageArgs(Map<String, Object> params) {
        return new Object[]{params.getOrDefault("version", "")};
    }

    @Override
    public NotificationPresentation presentation() {
        return NotificationPresentation.DIALOG;
    }

    @Override
    public boolean htmlMessage() {
        return true;
    }

    @Override
    public String externalMessageKey() {
        return "notifications.type.new-version.external-message";
    }

    @Override
    public boolean appliesTo(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    @Override
    public String actionUrl(Map<String, Object> params) {
        Object version = params == null ? null : params.get("version");
        if (version == null || String.valueOf(version).isBlank()) {
            return "https://github.com/dedicatedcode/reitti/releases";
        }
        return "https://github.com/dedicatedcode/reitti/releases/tag/" + String.valueOf(version).trim();
    }

    @Override
    public String dedupeKey(Map<String, Object> params) {
        Object version = params.get("version");
        return version == null ? null : String.valueOf(version);
    }
}
