package com.dedicatedcode.reitti.model.integration;

import java.time.Instant;

public class OwnTracksRecorderIntegration {
    
    private final Long id;
    private final String baseUrl;
    private final String username;
    private final String deviceId;
    private final boolean enabled;
    private final Instant lastSuccessfulFetch;
    private final Long version;

    public OwnTracksRecorderIntegration(String baseUrl, String username, String deviceId, boolean enabled) {
        this(null, baseUrl, username, deviceId, enabled, null, null);
    }

    public OwnTracksRecorderIntegration(Long id, String baseUrl, String username, String deviceId, boolean enabled, Instant lastSuccessfulFetch, Long version) {
        this.id = id;
        this.baseUrl = baseUrl;
        this.username = username;
        this.deviceId = deviceId;
        this.enabled = enabled;
        this.lastSuccessfulFetch = lastSuccessfulFetch;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastSuccessfulFetch() {
        return lastSuccessfulFetch;
    }

    public Long getVersion() {
        return version;
    }

    public OwnTracksRecorderIntegration withEnabled(boolean enabled) {
        return new OwnTracksRecorderIntegration(this.id, this.baseUrl, this.username, this.deviceId, enabled, this.lastSuccessfulFetch, this.version);
    }

    public OwnTracksRecorderIntegration withId(Long id) {
        return new OwnTracksRecorderIntegration(id, this.baseUrl, this.username, this.deviceId, this.enabled, this.lastSuccessfulFetch, this.version);
    }

    public OwnTracksRecorderIntegration withVersion(Long version) {
        return new OwnTracksRecorderIntegration(this.id, this.baseUrl, this.username, this.deviceId, this.enabled, this.lastSuccessfulFetch, version);
    }

    public OwnTracksRecorderIntegration withLastSuccessfulFetch(Instant lastSuccessfulFetch) {
        return new OwnTracksRecorderIntegration(this.id, this.baseUrl, this.username, this.deviceId, this.enabled, lastSuccessfulFetch, this.version);
    }
}
