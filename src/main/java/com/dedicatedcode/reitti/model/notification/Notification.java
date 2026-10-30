package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;
import java.util.Map;

/**
 * A single notification as it is stored in the in-app inbox.
 * <p>
 * The {@code title} and {@code message} are already translated in the language of the receiving
 * user at the time the notification is created. External channels receive exactly this text, so a
 * notification is never re-translated later on.
 */
public record Notification(Long id,
                           Long userId,
                           String typeKey,
                           NotificationSeverity severity,
                           String title,
                           String message,
                           Map<String, Object> params,
                           String actionUrl,
                           String dedupeKey,
                           Instant createdAt,
                           Instant readAt) {

    public boolean unread() {
        return readAt == null;
    }
}
