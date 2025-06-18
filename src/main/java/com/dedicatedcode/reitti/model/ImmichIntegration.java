package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "immich_integrations")
public class ImmichIntegration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private final User user;
    
    @Column(name = "server_url", nullable = false)
    private final String serverUrl;
    
    @Column(name = "api_token", nullable = false)
    private final String apiToken;
    
    @Column(name = "enabled", nullable = false)
    private final boolean enabled;
    
    @Column(name = "created_at", nullable = false)
    private final Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private final Instant updatedAt;
    
    @Version
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
