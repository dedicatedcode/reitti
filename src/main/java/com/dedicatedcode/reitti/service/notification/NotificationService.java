package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.event.NotificationSseEvent;
import com.dedicatedcode.reitti.model.notification.NotificationChannel;
import com.dedicatedcode.reitti.model.notification.NotificationChannelConfig;
import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.notification.NotificationPresentation;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationChannelJdbcService;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.repository.NotificationSettingsJdbcService;
import com.dedicatedcode.reitti.repository.NotificationTypePreferenceJdbcService;
import com.dedicatedcode.reitti.service.UserSseEmitterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationTypeRegistry typeRegistry;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationSettingsJdbcService settingsJdbcService;
    private final NotificationTypePreferenceJdbcService preferenceJdbcService;
    private final NotificationChannelJdbcService channelJdbcService;
    private final NotificationJdbcService notificationJdbcService;
    private final NotificationRenderer renderer;
    private final ObjectProvider<UserSseEmitterService> sseEmitterService;
    private final TaskExecutor notificationExecutor;
    private final boolean globallyEnabled;

    public NotificationService(NotificationTypeRegistry typeRegistry,
                               NotificationChannelRegistry channelRegistry,
                               NotificationSettingsJdbcService settingsJdbcService,
                               NotificationTypePreferenceJdbcService preferenceJdbcService,
                               NotificationChannelJdbcService channelJdbcService,
                               NotificationJdbcService notificationJdbcService,
                               NotificationRenderer renderer,
                               ObjectProvider<UserSseEmitterService> sseEmitterService,
                               @Qualifier("notificationExecutor") TaskExecutor notificationExecutor,
                               @Value("${reitti.notifications.enabled:true}") boolean globallyEnabled) {
        this.typeRegistry = typeRegistry;
        this.channelRegistry = channelRegistry;
        this.settingsJdbcService = settingsJdbcService;
        this.preferenceJdbcService = preferenceJdbcService;
        this.channelJdbcService = channelJdbcService;
        this.notificationJdbcService = notificationJdbcService;
        this.renderer = renderer;
        this.sseEmitterService = sseEmitterService;
        this.notificationExecutor = notificationExecutor;
        this.globallyEnabled = globallyEnabled;
    }

    public boolean notify(User user, String typeKey) {
        return notify(user, typeKey, Map.of());
    }

    /**
     * Creates a notification for the given user.
     *
     * @return true when the notification was stored in the inbox.
     */
    public boolean notify(User user, String typeKey, Map<String, Object> params) {
        if (!globallyEnabled) {
            return false;
        }
        if (user == null || user.getId() == null || typeKey == null) {
            return false;
        }

        NotificationType type = typeRegistry.find(typeKey).orElse(null);
        if (type == null) {
            log.warn("Ignoring notification for unknown type [{}]", typeKey);
            return false;
        }
        if (!type.appliesTo(user)) {
            return false;
        }
        if (!settingsJdbcService.isEnabled(user.getId())) {
            return false;
        }
        if (!preferenceJdbcService.isEnabled(user.getId(), typeKey, type.defaultEnabled())) {
            return false;
        }

        String dedupeKey = type.dedupeKey(params == null ? Map.of() : params);
        if (dedupeKey != null && notificationJdbcService.existsUnread(user.getId(), typeKey, dedupeKey)) {
            log.debug("Skipping duplicate notification [{}] for user [{}]", typeKey, user.getId());
            return false;
        }

        NotificationMessage message;
        try {
            message = renderer.render(user, type, params);
        } catch (Exception e) {
            log.error("Could not render notification [{}] for user [{}]", typeKey, user.getId(), e);
            return false;
        }

        boolean stored = false;
        for (NotificationChannel channel : channelRegistry.builtIn()) {
            try {
                if (channel.send(message, Map.of())) {
                    stored = true;
                }
            } catch (Exception e) {
                log.error("Built-in notification channel [{}] failed for user [{}]", channel.key(), user.getId(), e);
            }
        }
        if (!stored) {
            // in-app inbox is the canonical store; without it we do not notify anybody
            return false;
        }

        broadcastUnreadCount(user, message.title(), type.key(), type.presentation());
        dispatchExternal(user, externalMessage(message));
        return true;
    }

    /**
     * Notifies every user that the type applies to.
     *
     * @return the number of users that actually received the notification.
     */
    public int notifyAll(Collection<User> users, String typeKey, Map<String, Object> params) {
        if (users == null) {
            return 0;
        }
        int delivered = 0;
        for (User user : users) {
            try {
                if (notify(user, typeKey, params)) {
                    delivered++;
                }
            } catch (Exception e) {
                log.warn("Could not notify user [{}] for type [{}]", user != null ? user.getId() : null, typeKey, e);
            }
        }
        return delivered;
    }

    /**
     * Pushes the current number of unread notifications to all open browser connections of the
     * user so the settings icon updates without a page reload.
     */
    public void broadcastUnreadCount(User user, String title) {
        broadcastUnreadCount(user, title, null, null);
    }

    public void broadcastUnreadCount(User user, String title, String typeKey, NotificationPresentation presentation) {
        if (user == null || user.getId() == null) {
            return;
        }
        afterCommit(() -> {
            try {
                long unread = notificationJdbcService.countUnread(user.getId());
                boolean showBadge = settingsJdbcService.isBadgeVisible(user.getId());
                UserSseEmitterService emitters = sseEmitterService.getIfAvailable();
                if (emitters != null) {
                    emitters.sendEventToUser(user, new NotificationSseEvent(unread, showBadge, title, typeKey, presentation));
                }
            } catch (Exception e) {
                log.debug("Could not broadcast unread notification count for user [{}]", user.getId(), e);
            }
        });
    }

    private NotificationMessage externalMessage(NotificationMessage message) {
        if (message.externalMessage() == null || message.externalMessage().equals(message.message())) {
            return message;
        }
        return new NotificationMessage(
                message.userId(),
                message.typeKey(),
                message.severity(),
                message.title(),
                message.externalMessage(),
                message.externalMessage(),
                message.params(),
                message.actionUrl(),
                message.dedupeKey(),
                message.createdAt());
    }

    private void dispatchExternal(User user, NotificationMessage message) {
        afterCommit(() -> {
            for (NotificationChannelConfig config : channelJdbcService.findEnabledForUser(user.getId())) {
                NotificationChannel channel = channelRegistry.find(config.channelKey()).orElse(null);
                if (channel == null || channel.builtIn()) {
                    continue;
                }
                notificationExecutor.execute(() -> {
                    try {
                        if (!channel.send(message, config.config())) {
                            log.warn("Notification channel [{}] did not accept the message for user [{}]",
                                    channel.key(), user.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Notification channel [{}] failed for user [{}]", channel.key(), user.getId(), e);
                    }
                });
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
