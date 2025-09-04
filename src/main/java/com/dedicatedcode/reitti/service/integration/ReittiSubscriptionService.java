package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.SubscriptionResponse;
import com.dedicatedcode.reitti.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReittiSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(ReittiSubscriptionService.class);
    private final Map<String, ReittiSubscription> subscriptions = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;

    public ReittiSubscriptionService() {
        this.restTemplate = new RestTemplate();
    }

    public SubscriptionResponse createSubscription(User user, String callbackUrl) {
        String subscriptionId = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();
        
        ReittiSubscription subscription = new ReittiSubscription(subscriptionId, user.getId(), callbackUrl);
        subscriptions.put(subscriptionId, subscription);
        
        return new SubscriptionResponse(subscriptionId, "active", now);
    }
    
    public ReittiSubscription getSubscription(String subscriptionId) {
        return subscriptions.get(subscriptionId);
    }
    
    public void removeSubscription(String subscriptionId) {
        subscriptions.remove(subscriptionId);
    }

    public void sendNotification(String subscriptionId, Object notificationData) {
        ReittiSubscription subscription = subscriptions.get(subscriptionId);
        if (subscription == null) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> request = new HttpEntity<>(notificationData, headers);
            
            restTemplate.postForEntity(subscription.getCallbackUrl(), request, String.class);
            log.debug("Notification sent successfully to subscription: {}", subscriptionId);
        } catch (Exception e) {
            log.error("Failed to send notification to subscription: {}, callback URL: {}", 
                     subscriptionId, subscription.getCallbackUrl(), e);
            throw e;
        }
    }

    public void notifyAllSubscriptions(User user, Object notificationData) {
        subscriptions.values().stream()
                .filter(subscription -> subscription.getUserId().equals(user.getId()))
                .forEach(subscription -> {
                    try {
                        sendNotification(subscription.getSubscriptionId(), notificationData);
                    } catch (Exception e) {
                        log.error("Failed to notify subscription: {}", subscription.getSubscriptionId(), e);
                    }
                });
    }
}
