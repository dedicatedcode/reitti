package com.dedicatedcode.reitti.service.notification.channel;

import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.notification.NotificationChannel;
import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import com.dedicatedcode.reitti.service.notification.NotificationTestMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class InAppNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(InAppNotificationChannel.class);

    public static final String KEY = "IN_APP";

    private final NotificationJdbcService notificationJdbcService;
    private final NotificationTestMessageFactory testMessageFactory;

    public InAppNotificationChannel(NotificationJdbcService notificationJdbcService,
                                    NotificationTestMessageFactory testMessageFactory) {
        this.notificationJdbcService = notificationJdbcService;
        this.testMessageFactory = testMessageFactory;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String nameKey() {
        return "notifications.channel.in-app.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.channel.in-app.description";
    }

    @Override
    public boolean builtIn() {
        return true;
    }

    @Override
    public boolean test(User user, Map<String, String> config) {
        return send(testMessageFactory.create(user), config);
    }

    @Override
    public boolean send(NotificationMessage message, Map<String, String> config) {
        Optional<Long> id = notificationJdbcService.insert(message);
        if (id.isEmpty()) {
            log.debug("Notification [{}] for user [{}] was collapsed into an existing unread one",
                    message.typeKey(), message.userId());
        }
        return id.isPresent();
    }

    /**
     * Convenience accessor so callers do not need the repository directly.
     */
    public Optional<Notification> find(Long userId, Long notificationId) {
        return notificationJdbcService.findForUser(userId, notificationId);
    }
}
