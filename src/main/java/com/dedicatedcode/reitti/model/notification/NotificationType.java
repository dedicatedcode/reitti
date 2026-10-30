package com.dedicatedcode.reitti.model.notification;

import com.dedicatedcode.reitti.model.security.User;

import java.util.Map;

/**
 * Describes one kind of notification (e.g. "a job failed").
 * <p>
 * Implementations are Spring beans and get picked up automatically by the
 * {@code NotificationTypeRegistry}. Adding a new type means adding a bean and its message keys,
 * nothing else.
 */
public interface NotificationType {

    /**
     * Stable identifier that is persisted in the database. Never change it once shipped.
     */
    String key();

    NotificationSeverity severity();

    /**
     * Message key for the name shown in the settings.
     */
    String nameKey();

    /**
     * Message key for the description shown in the settings.
     */
    String descriptionKey();

    /**
     * Message key for the rendered title.
     */
    String titleKey();

    /**
     * Message key for the rendered body.
     */
    String messageKey();

    default Object[] titleArgs(Map<String, Object> params) {
        return new Object[0];
    }

    default Object[] messageArgs(Map<String, Object> params) {
        return new Object[0];
    }

    /**
     * How the notification should be presented in the UI. The default is the in-app inbox only.
     */
    default NotificationPresentation presentation() {
        return NotificationPresentation.INBOX;
    }

    /**
     * Whether the in-app message body may contain HTML. External channels always receive the
     * plain-text external message.
     */
    default boolean htmlMessage() {
        return false;
    }

    /**
     * Message key for the plain-text version delivered to external channels. Defaults to the
     * in-app message key so existing types keep working unchanged.
     */
    default String externalMessageKey() {
        return messageKey();
    }

    default Object[] externalMessageArgs(Map<String, Object> params) {
        return messageArgs(params);
    }

    /**
     * Whether the type is enabled for a user that has not expressed a preference yet.
     */
    default boolean defaultEnabled() {
        return true;
    }

    /**
     * Hidden types are not offered in the settings, they are only used internally (e.g. the test
     * notification triggered from the settings page).
     */
    default boolean hidden() {
        return false;
    }

    /**
     * Whether this type is relevant for the given user at all.
     */
    default boolean appliesTo(User user) {
        return true;
    }

    /**
     * Optional deep link. A relative URL is kept relative for the in-app inbox and turned into an
     * absolute URL for external channels when an advertise URI is configured.
     */
    default String actionUrl(Map<String, Object> params) {
        return null;
    }

    /**
     * Optional key used to collapse repeated events into a single unread notification.
     */
    default String dedupeKey(Map<String, Object> params) {
        return null;
    }
}
