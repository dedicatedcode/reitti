package com.dedicatedcode.reitti.dto;

import java.util.List;
import java.util.Objects;

public class UserSettings {
    
    private final boolean preferColoredMap;
    private final String selectedLanguage;
    private final List<Long> connectedUserAccounts;
    
    public UserSettings(boolean preferColoredMap, String selectedLanguage, List<Long> connectedUserAccounts) {
        this.preferColoredMap = preferColoredMap;
        this.selectedLanguage = selectedLanguage;
        this.connectedUserAccounts = connectedUserAccounts;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSettings that = (UserSettings) o;
        return preferColoredMap == that.preferColoredMap &&
                Objects.equals(selectedLanguage, that.selectedLanguage) &&
                Objects.equals(connectedUserAccounts, that.connectedUserAccounts);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(preferColoredMap, selectedLanguage, connectedUserAccounts);
    }
    
    @Override
    public String toString() {
        return "UserSettings{" +
                "preferColoredMap=" + preferColoredMap +
                ", selectedLanguage='" + selectedLanguage + '\'' +
                ", connectedUserAccounts=" + connectedUserAccounts +
                '}';
    }
}
