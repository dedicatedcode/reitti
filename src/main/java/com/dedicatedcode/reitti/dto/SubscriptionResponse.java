package com.dedicatedcode.reitti.dto;

import java.time.Instant;

public class SubscriptionResponse {
    private final String subscriptionId;
    private final String status;
    private final Instant createdAt;

    public SubscriptionResponse(String subscriptionId, String status, Instant createdAt) {
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
