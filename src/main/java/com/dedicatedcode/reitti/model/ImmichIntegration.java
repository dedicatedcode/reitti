package com.dedicatedcode.reitti.model;

import java.time.Instant;

public class ImmichIntegration {
    
    private final Long id;
    
    private final User user;
    
    private final String serverUrl;
    
    private final String apiToken;
    
    private final boolean enabled;
    
    private final Instant createdAt;
    
    private final Instant updatedAt;
    
    private final Long version;
    
    public ImmichIntegration() {
        this(null, null, null, null, false, null, null, null);
    }
    
    public ImmichIntegration(User user, String serverUrl, String apiToken, boolean enabled) {
        this(null, user, serverUrl, apiToken, enabled, null, null, null);
    }
    
    public ImmichIntegration(Long id, User user, String serverUrl, String apiToken, boolean enabled, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.user = user;
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
    
    public User getUser() {
        return user;
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
        return new ImmichIntegration(this.id, this.user, this.serverUrl, this.apiToken, enabled, this.createdAt, Instant.now(), this.version);
    }

    public ImmichIntegration withApiToken(String apiToken) {
        return new ImmichIntegration(this.id, this.user, this.serverUrl, apiToken, this.enabled, this.createdAt, Instant.now(), this.version);
    }
    
}
