package com.dedicatedcode.reitti.service.integration;

import java.time.Instant;

public class ReittiSubscription {
    private final Long id;
    private final String subscriptionId;
    private final Long userId;
    private final String callbackUrl;
    private final String status;
    private final Instant createdAt;
    private final Instant lastNotifiedAt;
    private final Long version;

    public ReittiSubscription(Long id, String subscriptionId, Long userId, String callbackUrl,
                              String status, Instant createdAt, Instant lastNotifiedAt, Long version) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.callbackUrl = callbackUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.lastNotifiedAt = lastNotifiedAt;
        this.version = version;
    }

    // Constructor for new subscriptions
    public ReittiSubscription(String subscriptionId, Long userId, String callbackUrl) {
        this(null, subscriptionId, userId, callbackUrl, "active", Instant.now(), null, null);
    }

    // Getters
    public Long getId() {
        return id;
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

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public Long getVersion() {
        return version;
    }
}