package com.dedicatedcode.reitti.service.integration;

import java.time.Instant;

public class ReittiSubscription {
    private final String subscriptionId;
    private final Long userId;
    private final String callbackUrl;
    private final Instant createdAt;

    public ReittiSubscription(String subscriptionId, Long userId, String callbackUrl, Instant createdAt) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.callbackUrl = callbackUrl;
        this.createdAt = createdAt;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
