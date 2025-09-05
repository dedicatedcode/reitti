package com.dedicatedcode.reitti.model.geocoding;

import java.time.Instant;

public class GeocodingResponse {
    private final Long id;
    private final Long significantPlaceId;
    private final String rawData; // JSON blob
    private final String providerName;
    private final Instant fetchedAt;
    private final GeocodingStatus status;
    private final String errorDetails;

    public GeocodingResponse(Long significantPlaceId, String rawData, String providerName, Instant fetchedAt, GeocodingStatus status, String errorDetails) {
        this(null, significantPlaceId, rawData, providerName, fetchedAt, status, errorDetails);
    }

    public GeocodingResponse(Long id, Long significantPlaceId, String rawData, String providerName, Instant fetchedAt, GeocodingStatus status, String errorDetails) {
        this.id = id;
        this.significantPlaceId = significantPlaceId;
        this.rawData = rawData;
        this.providerName = providerName;
        this.fetchedAt = fetchedAt;
        this.status = status;
        this.errorDetails = errorDetails;
    }

    public Long getId() {
        return id;
    }

    public Long getSignificantPlaceId() {
        return significantPlaceId;
    }

    public String getRawData() {
        return rawData;
    }

    public String getProviderName() {
        return providerName;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public GeocodingStatus getStatus() {
        return status;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

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
}
