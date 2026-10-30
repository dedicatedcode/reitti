package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;
import java.util.Map;

/**
 * Fully rendered notification that is handed to the channels.
 * <p>
 * {@code title} and {@code message} are already translated into the user's selected language. The
 * {@code actionUrl} is absolute when an advertise URI is configured so external channels can link
 * back into Reitti.
 * <p>
 * {@code message} is what the in-app inbox renders and may contain HTML when the originating
 * {@link NotificationType#htmlMessage()} opts in. {@code externalMessage} is always plain text and
 * is what external channels receive.
 */
public record NotificationMessage(Long userId,
                                  String typeKey,
                                  NotificationSeverity severity,
                                  String title,
                                  String message,
                                  String externalMessage,
                                  Map<String, Object> params,
                                  String actionUrl,
                                  String dedupeKey,
                                  Instant createdAt) {

    /**
     * Convenience constructor for plain-text messages where the in-app and external text are the
     * same.
     */
    public NotificationMessage(Long userId,
                               String typeKey,
                               NotificationSeverity severity,
                               String title,
                               String message,
                               Map<String, Object> params,
                               String actionUrl,
                               String dedupeKey,
                               Instant createdAt) {
        this(userId, typeKey, severity, title, message, message, params, actionUrl, dedupeKey, createdAt);
    }
}
