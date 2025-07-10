package com.dedicatedcode.reitti.model;

import java.util.List;
import java.util.Objects;

public class UserSettings {
    
    private final Long id;
    private final Long userId;
    private final boolean preferColoredMap;
    private final String selectedLanguage;
    private final List<Long> connectedUserAccounts;
    private final Long version;
    
    public UserSettings(Long id, Long userId, boolean preferColoredMap, String selectedLanguage, List<Long> connectedUserAccounts, Long version) {
        this.id = id;
        this.userId = userId;
        this.preferColoredMap = preferColoredMap;
        this.selectedLanguage = selectedLanguage;
        this.connectedUserAccounts = connectedUserAccounts;
        this.version = version;
    }
    
    public UserSettings(Long userId, boolean preferColoredMap, String selectedLanguage, List<Long> connectedUserAccounts) {
        this(null, userId, preferColoredMap, selectedLanguage, connectedUserAccounts, null);
    }
    
    public static UserSettings defaultSettings(Long userId) {
        return new UserSettings(userId, false, "en", List.of());
    }
    
    public Long getId() {
        return id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public boolean isPreferColoredMap() {
        return preferColoredMap;
    }
    
    public String getSelectedLanguage() {
        return selectedLanguage;
    }
    
    public List<Long> getConnectedUserAccounts() {
        return connectedUserAccounts;
    }
    
    public Long getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSettings that = (UserSettings) o;
        return preferColoredMap == that.preferColoredMap &&
                Objects.equals(id, that.id) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(selectedLanguage, that.selectedLanguage) &&
                Objects.equals(connectedUserAccounts, that.connectedUserAccounts) &&
                Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, userId, preferColoredMap, selectedLanguage, connectedUserAccounts, version);
    }
    
    @Override
    public String toString() {
        return "UserSettings{" +
                "id=" + id +
                ", userId=" + userId +
                ", preferColoredMap=" + preferColoredMap +
                ", selectedLanguage='" + selectedLanguage + '\'' +
                ", connectedUserAccounts=" + connectedUserAccounts +
                ", version=" + version +
                '}';
    }
}
