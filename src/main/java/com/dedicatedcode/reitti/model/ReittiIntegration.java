package com.dedicatedcode.reitti.model;

import java.time.LocalDateTime;
import java.util.Optional;

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

    public static ReittiIntegration create(String url, String token, boolean enabled, String color) {
        return new ReittiIntegration(-1L, url, token, enabled, enabled ? Status.ACTIVE : Status.RECOVERABLE, LocalDateTime.now(), null, null, 1L, null, color);
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

    public Optional<LocalDateTime> getLastUsed() {
        return Optional.ofNullable(lastUsed);
    }

    public Long getVersion() {
        return version;
    }

    public Optional<String> getLastMessage() {
        return Optional.ofNullable(lastMessage);
    }

    public String getColor() {
        return color;
    }

    public ReittiIntegration withEnabled(boolean enabled) {
        return new ReittiIntegration(this.id, this.url, this.token, enabled, this.status, this.createdAt, this.updatedAt, this.lastUsed, this.version, this.lastMessage, this.color);
    }

    public ReittiIntegration withLastUsed(LocalDateTime lastUsed) {
        return new ReittiIntegration(this.id, this.url, this.token, this.enabled, this.status, this.createdAt, this.updatedAt, lastUsed, this.version, this.lastMessage, this.color);
    }

    public ReittiIntegration withStatus(Status status) {
        return new ReittiIntegration(this.id, this.url, this.token, this.enabled, status, this.createdAt, this.updatedAt, this.lastUsed, this.version, this.lastMessage, this.color);
    }

    public enum Status {
        ACTIVE, RECOVERABLE, DISABLED, FAILED
    }

    @Override
    public String toString() {
        return "ReittiIntegration{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", token='" + token + '\'' +
                ", enabled=" + enabled +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", lastUsed=" + lastUsed +
                ", version=" + version +
                ", lastMessage='" + lastMessage + '\'' +
                ", color='" + color + '\'' +
                '}';
    }
}
