package com.dedicatedcode.reitti.model;

import java.time.Instant;
import java.util.UUID;

public class ApiToken {
    
    private final Long id;
    
    private final String token;
    
    private final User user;
    
    private final String name;
    
    private final Instant createdAt;
    
    private final Instant lastUsedAt;
    
    public ApiToken(User user, String name) {
        this(null, null, user, name, null, null);
    }
    
    public ApiToken(Long id, String token, User user, String name, Instant createdAt, Instant lastUsedAt) {
        this.id = id;
        this.token = token != null ? token : UUID.randomUUID().toString();
        this.user = user;
        this.name = name;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.lastUsedAt = lastUsedAt;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getToken() {
        return token;
    }
    
    public User getUser() {
        return user;
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
        return new ApiToken(this.id, this.token, this.user, this.name, this.createdAt, lastUsedAt);
    }
}
