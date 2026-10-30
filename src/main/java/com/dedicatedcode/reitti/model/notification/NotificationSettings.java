package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;

public record NotificationSettings(Long userId,
                                   boolean enabled,
                                   boolean showBadge,
                                   Instant createdAt,
                                   Instant updatedAt) {

    public static NotificationSettings defaultSettings(Long userId) {
        return new NotificationSettings(userId, true, true, null, null);
    }

    public NotificationSettings withEnabled(boolean enabled) {
        return new NotificationSettings(userId, enabled, showBadge, createdAt, updatedAt);
    }

    public NotificationSettings withShowBadge(boolean showBadge) {
        return new NotificationSettings(userId, enabled, showBadge, createdAt, updatedAt);
    }
}
