package com.dedicatedcode.reitti.model.security;

import java.io.Serializable;
import java.time.Instant;

public class MagicLinkToken implements Serializable {
    private final Long id;
    private final String name; // Store a hash of the token, not the raw token
    private final String tokenHash; // Store a hash of the token, not the raw token
    private final MagicLinkAccessLevel accessLevel; // The type of content (e.g., "DOCUMENT", "PROJECT")
    private final Instant expiryDate;
    private final Instant createdAt;
    private final Instant lastUsed;
    private final boolean isUsed; // To enforce one-time use if needed

    public MagicLinkToken(Long id, String name, String tokenHash, MagicLinkAccessLevel accessLevel, Instant expiryDate, Instant createdAt, Instant lastUsed, boolean isUsed) {
        this.id = id;
        this.name = name;
        this.tokenHash = tokenHash;
        this.accessLevel = accessLevel;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
        this.lastUsed = lastUsed;
        this.isUsed = isUsed;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public MagicLinkAccessLevel getAccessLevel() {
        return accessLevel;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
