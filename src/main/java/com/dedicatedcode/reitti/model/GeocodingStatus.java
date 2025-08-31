package com.dedicatedcode.reitti.model;

public enum GeocodingStatus {
    SUCCESS("geocoding.status.success"),
    ERROR("geocoding.status.error"),
    ZERO_RESULTS("geocoding.status.zero_results"),
    RATE_LIMITED("geocoding.status.rate_limited"),
    INVALID_REQUEST("geocoding.status.invalid_request");

    private final String messageKey;

    GeocodingStatus(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}