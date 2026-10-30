package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.model.notification.NotificationType;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationTypeRegistry {

    private final Map<String, NotificationType> types;

    public NotificationTypeRegistry(List<NotificationType> types) {
        Map<String, NotificationType> registered = new LinkedHashMap<>();
        for (NotificationType type : types) {
            NotificationType previous = registered.put(type.key(), type);
            if (previous != null) {
                throw new IllegalStateException("Duplicate notification type key [" + type.key() + "]");
            }
        }
        this.types = Map.copyOf(registered);
    }

    public Optional<NotificationType> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(types.get(key));
    }

    public List<NotificationType> forUser(User user) {
        return types.values().stream()
                .filter(type -> !type.hidden())
                .filter(type -> type.appliesTo(user))
                .sorted(Comparator.comparing(NotificationType::nameKey))
                .toList();
    }

    public List<NotificationType> all() {
        return List.copyOf(types.values());
    }
}
