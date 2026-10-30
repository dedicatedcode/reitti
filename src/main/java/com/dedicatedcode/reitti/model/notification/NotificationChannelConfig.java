package com.dedicatedcode.reitti.model.notification;

import java.time.Instant;
import java.util.Map;

/**
 * Stored configuration of a delivery channel for a single user. The {@code config} map holds the
 * channel specific settings, its exact shape is defined by the matching {@link NotificationChannel}
 * implementation.
 */
public record NotificationChannelConfig(Long id,
                                        Long userId,
                                        String channelKey,
                                        boolean enabled,
                                        Map<String, String> config,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        Long version) {

    public NotificationChannelConfig {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public static NotificationChannelConfig empty(Long userId, String channelKey) {
        return new NotificationChannelConfig(null, userId, channelKey, false, Map.of(), null, null, null);
    }

    public String value(String key) {
        return config.get(key);
    }

    public String value(String key, String defaultValue) {
        String value = config.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public boolean isTrue(String key) {
        String value = config.get(key);
        return value != null && (value.equalsIgnoreCase("true") || value.equals("on") || value.equals("1"));
    }
}
