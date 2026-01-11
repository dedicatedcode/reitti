package com.dedicatedcode.reitti.service.integration.mqtt;

import java.time.Instant;

public class MqttIntegration {
    private final Long id;
    private final String host;
    private final int port;
    private final boolean useTLS;
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

    public static MqttIntegration empty() {
        return new MqttIntegration(null, null, 0, false, null, null, null, null, null, false, null, null, null, null);
    }
    
    public MqttIntegration(Long id, String host, int port, boolean useTLS, String identifier, String topic, String username, String password, PayloadType payloadType, boolean enabled, Instant createdAt, Instant updatedAt, Instant lastUsed, Long version) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.useTLS = useTLS;
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

    public boolean isUseTLS() {
        return useTLS;
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

    public MqttIntegration withId(Long id) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withHost(String host) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withPort(int port) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withUseTLS(boolean useTLS) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withIdentifier(String identifier) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withTopic(String topic) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withUsername(String username) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withPassword(String password) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withPayloadType(PayloadType payloadType) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withEnabled(boolean enabled) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withCreatedAt(Instant createdAt) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withUpdatedAt(Instant updatedAt) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withLastUsed(Instant lastUsed) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }

    public MqttIntegration withVersion(Long version) {
        return new MqttIntegration(id, host, port, useTLS, identifier, topic, username, password, payloadType, enabled, createdAt, updatedAt, lastUsed, version);
    }
}
