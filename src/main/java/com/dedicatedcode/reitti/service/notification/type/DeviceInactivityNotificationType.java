package com.dedicatedcode.reitti.service.notification.type;

import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeviceInactivityNotificationType implements NotificationType {

    public static final String KEY = "DEVICE_INACTIVITY";

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
        return "notifications.type.device-inactivity.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.type.device-inactivity.description";
    }

    @Override
    public String titleKey() {
        return "notifications.type.device-inactivity.title";
    }

    @Override
    public String messageKey() {
        return "notifications.type.device-inactivity.message";
    }

    @Override
    public Object[] messageArgs(Map<String, Object> params) {
        return new Object[]{
                params.getOrDefault("deviceName", ""),
                params.getOrDefault("hours", "")
        };
    }

    @Override
    public String actionUrl(Map<String, Object> params) {
        return "/settings/devices";
    }
}
