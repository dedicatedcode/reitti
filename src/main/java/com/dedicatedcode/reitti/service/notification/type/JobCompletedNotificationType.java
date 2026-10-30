package com.dedicatedcode.reitti.service.notification.type;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JobCompletedNotificationType implements NotificationType {

    public static final String KEY = "JOB_COMPLETED";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public NotificationSeverity severity() {
        return NotificationSeverity.SUCCESS;
    }

    @Override
    public String nameKey() {
        return "notifications.type.job-completed.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.type.job-completed.description";
    }

    @Override
    public String titleKey() {
        return "notifications.type.job-completed.title";
    }

    @Override
    public String messageKey() {
        return "notifications.type.job-completed.message";
    }

    @Override
    public Object[] messageArgs(Map<String, Object> params) {
        return new Object[]{
                params.getOrDefault("friendlyName", ""),
                params.getOrDefault("duration", "")
        };
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean appliesTo(User user) {
        return user != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.USER);
    }

    @Override
    public String actionUrl(Map<String, Object> params) {
        return "/settings/job-status";
    }

    @Override
    public String dedupeKey(Map<String, Object> params) {
        Object jobId = params.get("jobId");
        return jobId == null ? null : jobId + ":" + KEY;
    }
}
