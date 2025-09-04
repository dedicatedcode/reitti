package com.dedicatedcode.reitti.service.integration;

public class ReittiSubscription {
    private final String subscriptionId;
    private final Long userId;
    private final String callbackUrl;

    public ReittiSubscription(String subscriptionId, Long userId, String callbackUrl) {
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.callbackUrl = callbackUrl;
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
}
