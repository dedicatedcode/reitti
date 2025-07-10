package com.dedicatedcode.reitti.dto;

import java.util.Objects;

public class UserSettings {
    
    private final String username;
    private final String displayName;
    private final Long userId;
    private final boolean isAuthenticated;
    
    public UserSettings(String username, String displayName, Long userId, boolean isAuthenticated) {
        this.username = username;
        this.displayName = displayName;
        this.userId = userId;
        this.isAuthenticated = isAuthenticated;
    }
    
    public static UserSettings anonymous() {
        return new UserSettings(null, null, null, false);
    }
    
    public static UserSettings authenticated(String username, String displayName, Long userId) {
        return new UserSettings(username, displayName, userId, true);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSettings that = (UserSettings) o;
        return isAuthenticated == that.isAuthenticated &&
                Objects.equals(username, that.username) &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(userId, that.userId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(username, displayName, userId, isAuthenticated);
    }
    
    @Override
    public String toString() {
        return "UserSettings{" +
                "username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", userId=" + userId +
                ", isAuthenticated=" + isAuthenticated +
                '}';
    }
}
