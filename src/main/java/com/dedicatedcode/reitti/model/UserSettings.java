package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.dto.ConnectedUserAccount;

import java.util.List;
import java.util.Objects;

public class UserSettings {
    
    private final Long userId;
    private final boolean preferColoredMap;
    private final String selectedLanguage;
    private final List<ConnectedUserAccount> connectedUserAccounts;
    private final Long version;

    public UserSettings(Long userId, boolean preferColoredMap, String selectedLanguage, List<ConnectedUserAccount> connectedUserAccounts, Long version) {
        this.userId = userId;
        this.preferColoredMap = preferColoredMap;
        this.selectedLanguage = selectedLanguage;
        this.connectedUserAccounts = connectedUserAccounts;
        this.version = version;
    }
    public UserSettings(Long userId, boolean preferColoredMap, String selectedLanguage, List<ConnectedUserAccount> connectedUserAccounts) {
        this(userId, preferColoredMap, selectedLanguage, connectedUserAccounts, null);
    }

    public static UserSettings defaultSettings(Long userId) {
        return new UserSettings(userId, false, "en", List.of(), null);
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
    
    public List<ConnectedUserAccount> getConnectedUserAccounts() {
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
                Objects.equals(userId, that.userId) &&
                Objects.equals(selectedLanguage, that.selectedLanguage) &&
                Objects.equals(connectedUserAccounts, that.connectedUserAccounts) &&
                Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, preferColoredMap, selectedLanguage, connectedUserAccounts, version);
    }
    
    @Override
    public String toString() {
        return "UserSettings{" +
                "userId=" + userId +
                ", preferColoredMap=" + preferColoredMap +
                ", selectedLanguage='" + selectedLanguage + '\'' +
                ", connectedUserAccounts=" + connectedUserAccounts +
                ", version=" + version +
                '}';
    }
}
