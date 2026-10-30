package com.dedicatedcode.reitti.service.notification;

import com.dedicatedcode.reitti.model.notification.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationChannelRegistry {

    private final Map<String, NotificationChannel> channels;

    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        Map<String, NotificationChannel> registered = new LinkedHashMap<>();
        for (NotificationChannel channel : channels) {
            NotificationChannel previous = registered.put(channel.key(), channel);
            if (previous != null) {
                throw new IllegalStateException("Duplicate notification channel key [" + channel.key() + "]");
            }
        }
        this.channels = Map.copyOf(registered);
    }

    public Optional<NotificationChannel> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(channels.get(key));
    }

    public List<NotificationChannel> all() {
        return List.copyOf(channels.values());
    }

    public List<NotificationChannel> external() {
        return channels.values().stream().filter(channel -> !channel.builtIn()).toList();
    }

    public List<NotificationChannel> builtIn() {
        return channels.values().stream().filter(NotificationChannel::builtIn).toList();
    }
}
