package com.dedicatedcode.reitti.service.notification.channel;

import com.dedicatedcode.reitti.model.notification.NotificationChannel;
import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.notification.NotificationTestMessageFactory;
import com.dedicatedcode.reitti.service.notification.NotificationUrlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NtfyNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationChannel.class);

    public static final String KEY = "NTFY";
    public static final String DEFAULT_SERVER_URL = "https://ntfy.sh";

    private final RestTemplate restTemplate;
    private final NotificationUrlBuilder urlBuilder;
    private final NotificationTestMessageFactory testMessageFactory;
    private final ObjectMapper objectMapper;

    public NtfyNotificationChannel(@Qualifier("notificationRestTemplate") RestTemplate restTemplate,
                                   NotificationUrlBuilder urlBuilder,
                                   NotificationTestMessageFactory testMessageFactory,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.urlBuilder = urlBuilder;
        this.testMessageFactory = testMessageFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String nameKey() {
        return "notifications.channel.ntfy.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.channel.ntfy.description";
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        String serverUrl = config.get("serverUrl");
        if (!StringUtils.hasText(serverUrl)) {
            errors.add("notifications.channel.ntfy.server.url.required");
        } else if (!isHttpUrl(serverUrl)) {
            errors.add("notifications.channel.ntfy.server.url.invalid");
        }
        if (!StringUtils.hasText(config.get("topic"))) {
            errors.add("notifications.channel.ntfy.topic.required");
        }
        String priority = config.get("priority");
        if (StringUtils.hasText(priority) && parsePriority(priority, NotificationSeverity.INFO) == null) {
            errors.add("notifications.channel.ntfy.priority.invalid");
        }
        return errors;
    }

    @Override
    public boolean test(User user, Map<String, String> config) {
        return send(testMessageFactory.create(user), config);
    }

    @Override
    public boolean send(NotificationMessage message, Map<String, String> config) {
        String serverUrl = StringUtils.hasText(config.get("serverUrl")) ? config.get("serverUrl") : DEFAULT_SERVER_URL;
        String topic = config.get("topic");
        if (!StringUtils.hasText(topic)) {
            log.warn("ntfy channel is enabled for user [{}] without a topic, skipping", message.userId());
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topic", topic);
        payload.put("title", message.title());
        payload.put("message", message.message());
        Integer priority = parsePriority(config.get("priority"), message.severity());
        if (priority != null) {
            payload.put("priority", priority);
        }
        List<String> tags = tagsFor(message.severity());
        if (!tags.isEmpty()) {
            payload.put("tags", tags);
        }
        String click = urlBuilder.absolute(message.actionUrl());
        if (StringUtils.hasText(click)) {
            payload.put("click", click);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(config.get("token"))) {
            headers.setBearerAuth(config.get("token").trim());
        }

        try {
            // Pre-serialize instead of letting RestTemplate stream the Map so a Content-Length
            // is always sent (see AppriseNotificationChannel.send for the reason).
            byte[] body = objectMapper.writeValueAsBytes(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    trimTrailingSlash(serverUrl),
                    new HttpEntity<>(body, headers),
                    String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            if (!success) {
                log.warn("ntfy returned status [{}] for user [{}]", response.getStatusCode(), message.userId());
            }
            return success;
        } catch (Exception e) {
            log.warn("Could not deliver notification through ntfy for user [{}]: {}", message.userId(), e.getMessage());
            return false;
        }
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Integer parsePriority(String configured, NotificationSeverity severity) {
        if (StringUtils.hasText(configured)) {
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed < 1 || parsed > 5) {
                    return null;
                }
                return parsed;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (severity) {
            case ERROR -> 5;
            case WARNING -> 4;
            case SUCCESS -> 3;
            case INFO -> 3;
        };
    }

    private List<String> tagsFor(NotificationSeverity severity) {
        return switch (severity) {
            case ERROR -> List.of("rotating_light");
            case WARNING -> List.of("warning");
            case SUCCESS -> List.of("white_check_mark");
            case INFO -> List.of("information_source");
        };
    }
}
