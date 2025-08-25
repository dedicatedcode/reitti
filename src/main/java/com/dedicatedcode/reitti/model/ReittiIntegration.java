package com.dedicatedcode.reitti.model;

import java.time.LocalDateTime;

public class ReittiIntegration {
    private final Long id;
    private final String url;
    private final String token;
    private final boolean enabled;
    private final Status status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime lastUsed;
    private final Long version;
    private final String lastMessage;
    private final String color;

    public ReittiIntegration(Long id, String url, String token, boolean enabled, Status status, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastUsed, Long version, String lastMessage, String color) {
        this.id = id;
        this.url = url;
        this.token = token;
        this.enabled = enabled;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastUsed = lastUsed;
        this.version = version;
        this.lastMessage = lastMessage;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getToken() {
        return token;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public Long getVersion() {
        return version;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getColor() {
        return color;
    }

    public enum Status {
        ENABLED, DISABLED, FAILED
    }
}
