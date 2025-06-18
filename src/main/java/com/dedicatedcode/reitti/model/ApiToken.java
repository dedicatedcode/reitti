package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_tokens")
public class ApiToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;
    
    @Column(nullable = false, unique = true)
    private final String token;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private final User user;
    
    @Column(nullable = false)
    private final String name;
    
    @Column(nullable = false)
    private final Instant createdAt;
    
    @Column
    private final Instant lastUsedAt;
    
    // Constructors
    public ApiToken() {
        this(null, null, null, null, null, null);
    }
    
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
