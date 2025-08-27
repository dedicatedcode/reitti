package com.dedicatedcode.reitti.model;

public class RemoteUser {
    private final Long remoteId;
    private final String displayName;
    private final String userName;
    private final long remoteVersion;

    public RemoteUser(Long remoteId, String displayName, String userName, long remoteVersion) {
        this.remoteId = remoteId;
        this.displayName = displayName;
        this.userName = userName;
        this.remoteVersion = remoteVersion;
    }

    public Long getRemoteId() {
        return remoteId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUserName() {
        return userName;
    }

    public Long getRemoteVersion() {
        return remoteVersion;
    }
}
