package com.dedicatedcode.reitti.model.security;

import java.time.Instant;

public class UserSharing {
    private final Long id;
    private final Long sharingUserId;
    private final Long sharedWithUserId;
    private final Instant createdAt;
    private final Long version;

    public UserSharing(Long id, Long sharingUserId, Long sharedWithUserId, Instant createdAt, Long version) {
        this.id = id;
        this.sharingUserId = sharingUserId;
        this.sharedWithUserId = sharedWithUserId;
        this.createdAt = createdAt;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Long getSharingUserId() {
        return sharingUserId;
    }

    public Long getSharedWithUserId() {
        return sharedWithUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSharing that = (UserSharing) o;
        return java.util.Objects.equals(id, that.id) &&
               java.util.Objects.equals(sharingUserId, that.sharingUserId) &&
               java.util.Objects.equals(sharedWithUserId, that.sharedWithUserId) &&
               java.util.Objects.equals(createdAt, that.createdAt) &&
               java.util.Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, sharingUserId, sharedWithUserId, createdAt, version);
    }
}
