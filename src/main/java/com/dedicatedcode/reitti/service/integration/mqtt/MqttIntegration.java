package com.dedicatedcode.reitti.service.integration.mqtt;

import java.time.Instant;

public class MqttIntegration {
    private final Long id;
    private final String host;
    private final int port;
    private final String identifier;
    private final String topic;
    private final String username;
    private final String password;
    private final PayloadType payloadType;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastUsed;
    private final Long version;

    public MqttIntegration(Long id, String host, int port, String identifier, String topic, String username, String password, PayloadType payloadType, boolean enabled, Instant createdAt, Instant updatedAt, Instant lastUsed, Long version) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.identifier = identifier;
        this.topic = topic;
        this.username = username;
        this.password = password;
        this.payloadType = payloadType;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastUsed = lastUsed;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getTopic() {
        return topic;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public MqttIntegration withEnabled(boolean enabled) {
        return new MqttIntegration(id, host, port, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }
}
