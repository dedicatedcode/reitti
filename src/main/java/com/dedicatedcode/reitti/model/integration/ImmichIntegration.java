package com.dedicatedcode.reitti.model.integration;

import java.time.Instant;

public class ImmichIntegration {
    
    private final Long id;
    
    private final String serverUrl;
    
    private final String apiToken;
    
    private final boolean enabled;
    
    private final Instant createdAt;
    
    private final Instant updatedAt;
    
    private final Long version;
    

    public ImmichIntegration(String serverUrl, String apiToken, boolean enabled) {
        this(null, serverUrl, apiToken, enabled, null, null, 1L);
    }
    
    public ImmichIntegration(Long id, String serverUrl, String apiToken, boolean enabled, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.serverUrl = serverUrl;
        this.apiToken = apiToken;
        this.enabled = enabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.version = version;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public String getApiToken() {
        return apiToken;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    // Wither methods
    public ImmichIntegration withEnabled(boolean enabled) {
        return new ImmichIntegration(this.id, this.serverUrl, this.apiToken, enabled, this.createdAt, Instant.now(), this.version);
    }

    public ImmichIntegration withServerUrl(String serverUrl) {
        return new ImmichIntegration(this.id, serverUrl, this.apiToken, this.enabled, this.createdAt, this.updatedAt, version);
    }

    public ImmichIntegration withApiToken(String apiToken) {
        return new ImmichIntegration(this.id, this.serverUrl, apiToken, this.enabled, this.createdAt, Instant.now(), this.version);
    }

    public ImmichIntegration withId(Long id) {
        return new ImmichIntegration(id, this.serverUrl, this.apiToken, this.enabled, this.createdAt, this.updatedAt, version);
    }
}
