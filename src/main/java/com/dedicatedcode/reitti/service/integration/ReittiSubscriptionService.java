package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.SubscriptionResponse;
import com.dedicatedcode.reitti.model.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReittiSubscriptionService {
    private final Map<String, ReittiSubscription> subscriptions = new ConcurrentHashMap<>();

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
}
