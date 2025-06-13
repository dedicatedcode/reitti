package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "immich_integrations")
public class ImmichIntegration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "server_url", nullable = false)
    private String serverUrl;
    
    @Column(name = "api_token", nullable = false)
    private String apiToken;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    public ImmichIntegration() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public ImmichIntegration(User user, String serverUrl, String apiToken, boolean enabled) {
        this();
        this.user = user;
        this.serverUrl = serverUrl;
        this.apiToken = apiToken;
        this.enabled = enabled;
    }
    
    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = Instant.now();
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    public String getApiToken() {
        return apiToken;
    }
    
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
}
