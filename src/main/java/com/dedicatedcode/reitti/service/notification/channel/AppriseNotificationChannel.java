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
public class AppriseNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(AppriseNotificationChannel.class);

    public static final String KEY = "APPRISE";

    private final RestTemplate restTemplate;
    private final NotificationUrlBuilder urlBuilder;
    private final NotificationTestMessageFactory testMessageFactory;
    private final ObjectMapper objectMapper;

    public AppriseNotificationChannel(@Qualifier("notificationRestTemplate") RestTemplate restTemplate,
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
        return "notifications.channel.apprise.name";
    }

    @Override
    public String descriptionKey() {
        return "notifications.channel.apprise.description";
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        String baseUrl = config.get("baseUrl");
        if (!StringUtils.hasText(baseUrl)) {
            errors.add("notifications.channel.apprise.base.url.required");
        } else if (!isHttpUrl(baseUrl)) {
            errors.add("notifications.channel.apprise.base.url.invalid");
        }
        if (!StringUtils.hasText(config.get("key"))) {
            errors.add("notifications.channel.apprise.key.required");
        }
        return errors;
    }

    @Override
    public boolean test(User user, Map<String, String> config) {
        return send(testMessageFactory.create(user), config);
    }

    @Override
    public boolean send(NotificationMessage message, Map<String, String> config) {
        String baseUrl = config.get("baseUrl");
        String key = config.get("key");
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(key)) {
            log.warn("Apprise channel is enabled for user [{}] without base URL or key, skipping", message.userId());
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", message.title());
        payload.put("body", message.message());
        payload.put("type", appriseType(message.severity()));
        String click = urlBuilder.absolute(message.actionUrl());
        if (StringUtils.hasText(click)) {
            payload.put("url", click);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String notifyUrl = trimTrailingSlash(baseUrl) + "/notify/" + key.trim();
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    notifyUrl,
                    new HttpEntity<>(body, headers),
                    String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            if (!success) {
                log.warn("Apprise returned status [{}] for user [{}]", response.getStatusCode(), message.userId());
            }
            return success;
        } catch (Exception e) {
            log.warn("Could not deliver notification through Apprise for user [{}]: {}", message.userId(), e.getMessage());
            return false;
        }
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String appriseType(NotificationSeverity severity) {
        return switch (severity) {
            case ERROR -> "failure";
            case WARNING -> "warning";
            case SUCCESS -> "success";
            case INFO -> "info";
        };
    }
}
