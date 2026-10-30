package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.notification.NotificationPresentation;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NotificationJdbcService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationPresentationService {

    private final NotificationTypeRegistry typeRegistry;
    private final NotificationJdbcService notificationJdbcService;

    public NotificationPresentationService(NotificationTypeRegistry typeRegistry,
                                           NotificationJdbcService notificationJdbcService) {
        this.typeRegistry = typeRegistry;
        this.notificationJdbcService = notificationJdbcService;
    }

    public Optional<PendingNotification> findLatestPresentable(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        List<String> typeKeys = typeRegistry.all().stream()
                .filter(type -> type.presentation() != NotificationPresentation.INBOX)
                .map(NotificationType::key)
                .toList();
        if (typeKeys.isEmpty()) {
            return Optional.empty();
        }
        return notificationJdbcService.findLatestUnreadByTypeKeys(user.getId(), typeKeys)
                .flatMap(notification -> typeRegistry.find(notification.typeKey())
                        .filter(type -> type.presentation() != NotificationPresentation.INBOX)
                        .map(type -> new PendingNotification(
                                notification,
                                type.presentation(),
                                type.htmlMessage(),
                                isAutoHide(type.presentation(), notification.severity()))));
    }

    private boolean isAutoHide(NotificationPresentation presentation, NotificationSeverity severity) {
        return presentation == NotificationPresentation.TOAST
                && (severity == NotificationSeverity.INFO || severity == NotificationSeverity.SUCCESS);
    }

    public record PendingNotification(Notification notification,
                                      NotificationPresentation presentation,
                                      boolean html,
                                      boolean autoHide) {
    }
}
