package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;

/**
 * Per user override for a single notification type. A missing row means "use the type default".
 */
public record NotificationTypePreference(Long userId,
                                         String typeKey,
                                         boolean enabled,
                                         Instant updatedAt) {
}
