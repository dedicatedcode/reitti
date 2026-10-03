package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.SubscriptionResponse;
import com.dedicatedcode.reitti.model.NotificationData;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.security.OutboundHttp;
import com.dedicatedcode.reitti.service.security.OutboundUrlValidator;
import com.dedicatedcode.reitti.service.security.UnsafeUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReittiSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(ReittiSubscriptionService.class);
    // Remote instances subscribe whenever one of their users opens the map, so subscriptions are short-lived and
    // abandoned ones are expired. The per-user cap bounds the memory a single token holder can use.
    static final int MAX_SUBSCRIPTIONS_PER_USER = 20;
    static final Duration SUBSCRIPTION_TTL = Duration.ofHours(24);

    private final Map<String, ReittiSubscription> subscriptions = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final OutboundUrlValidator outboundUrlValidator;

    public ReittiSubscriptionService(OutboundUrlValidator outboundUrlValidator) {
        this.outboundUrlValidator = outboundUrlValidator;
        this.restTemplate = new RestTemplate(OutboundHttp.noRedirectRequestFactory());
    }

    public SubscriptionResponse createSubscription(User user, String callbackUrl) {
        try {
            outboundUrlValidator.validate(callbackUrl);
        } catch (UnsafeUrlException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback URL is not allowed: " + e.getMessage());
        }

        String subscriptionId = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();

        synchronized (subscriptions) {
            removeExpired(now);
            // keep the newest subscriptions of the user, making room for the new one
            subscriptions.values().stream()
                    .filter(subscription -> subscription.getUserId().equals(user.getId()))
                    .sorted(Comparator.comparing(ReittiSubscription::getCreatedAt).reversed())
                    .skip(MAX_SUBSCRIPTIONS_PER_USER - 1)
                    .map(ReittiSubscription::getSubscriptionId)
                    .toList()
                    .forEach(subscriptions::remove);
            subscriptions.put(subscriptionId, new ReittiSubscription(subscriptionId, user.getId(), callbackUrl, now));
        }

        return new SubscriptionResponse(subscriptionId, "active", now);
    }

    public ReittiSubscription getSubscription(String subscriptionId) {
        ReittiSubscription subscription = subscriptions.get(subscriptionId);
        if (subscription != null && isExpired(subscription, Instant.now())) {
            subscriptions.remove(subscriptionId);
            return null;
        }
        return subscription;
    }

    public void notifyAllSubscriptions(User user, NotificationData notificationData) {
        removeExpired(Instant.now());
        subscriptions.values().stream()
                .filter(subscription -> subscription.getUserId().equals(user.getId()))
                .forEach(subscription -> sendNotificationToCallback(subscription, notificationData));
    }

    private void removeExpired(Instant now) {
        subscriptions.values().removeIf(subscription -> isExpired(subscription, now));
    }

    private static boolean isExpired(ReittiSubscription subscription, Instant now) {
        return subscription.getCreatedAt().plus(SUBSCRIPTION_TTL).isBefore(now);
    }

    private void sendNotificationToCallback(ReittiSubscription subscription, NotificationData notificationData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> request = new HttpEntity<>(notificationData, headers);

            String notifyUrl = subscription.getCallbackUrl().endsWith("/") ?
                    subscription.getCallbackUrl() + "api/v1/reitti-integration/notify/" + subscription.getSubscriptionId() :
                    subscription.getCallbackUrl() + "/api/v1/reitti-integration/notify/" + subscription.getSubscriptionId();
            outboundUrlValidator.validate(notifyUrl);
            restTemplate.postForEntity(notifyUrl, request, String.class);
            log.debug("Notification sent successfully to subscription: {}", subscription.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to send notification to subscription: {}, callback URL: {}",
                    subscription.getSubscriptionId(), subscription.getCallbackUrl(), e);
            this.subscriptions.remove(subscription.getSubscriptionId());
        }
    }
}
