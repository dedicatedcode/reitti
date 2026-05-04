package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.devices.Device;

import java.time.Instant;
import java.util.UUID;

public class ApiToken {
    
    private final Long id;
    
    private final String token;

    private final Device device;

    private final User user;
    
    private final String name;
    
    private final Instant createdAt;
    
    private final Instant lastUsedAt;
    
    public ApiToken(User user, String name) {
        this(user, name, null);
    }

    public ApiToken(User user, String name, Device device) {
        this(null, null, user, device, name, null, null);
    }

    public ApiToken(Long id, String token, User user, Device device, String name, Instant createdAt, Instant lastUsedAt) {
        this.id = id;
        this.token = token != null ? token : UUID.randomUUID().toString();
        this.user = user;
        this.device = device;
        this.name = name;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.lastUsedAt = lastUsedAt;
    }

    public Long getId() {
        return id;
    }
    
    public String getToken() {
        return token;
    }
    
    public User getUser() {
        return user;
    }

    public Device getDevice() {
        return device;
    }

    public String getName() {
        return name;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
    
    // Wither method
    public ApiToken withLastUsedAt(Instant lastUsedAt) {
        return new ApiToken(this.id, this.token, this.user, this.device, this.name, this.createdAt, lastUsedAt);
    }
}
