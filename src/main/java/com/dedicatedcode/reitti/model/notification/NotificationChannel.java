package com.dedicatedcode.reitti.model.notification;

import com.dedicatedcode.reitti.model.security.User;

import java.util.List;
import java.util.Map;

/**
 * A delivery channel for notifications (e.g. the in-app inbox, ntfy or Apprise).
 * <p>
 * Implementations are Spring beans and get picked up automatically by the
 * {@code NotificationChannelRegistry}.
 */
public interface NotificationChannel {

    String key();

    String nameKey();

    String descriptionKey();

    /**
     * Built-in channels are always active and cannot be disabled by the user. Only the in-app
     * inbox is built-in.
     */
    default boolean builtIn() {
        return false;
    }

    /**
     * Whether the channel can currently be used at all (e.g. a required server wide setting).
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Validates the given configuration.
     *
     * @return a list of message keys describing the problems, empty when the config is usable.
     */
    default List<String> validate(Map<String, String> config) {
        return List.of();
    }

    /**
     * Sends a test notification through this channel using the given configuration.
     *
     * @return true when the channel accepted the message.
     */
    boolean test(User user, Map<String, String> config);

    /**
     * Delivers the notification.
     *
     * @return true when the channel accepted the message.
     */
    boolean send(NotificationMessage message, Map<String, String> config);
}
