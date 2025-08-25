package com.dedicatedcode.reitti.model;

import java.time.Instant;
import java.util.Objects;

public class UserSettings {
    
    private final Long userId;
    private final boolean preferColoredMap;
    private final String selectedLanguage;
    private final UnitSystem unitSystem;
    private final Double homeLatitude;
    private final Double homeLongitude;
    private final Instant latestData;
    private final Long version;

    public UserSettings(Long userId, boolean preferColoredMap, String selectedLanguage, UnitSystem unitSystem, Double homeLatitude, Double homeLongitude, Instant latestData, Long version) {
        this.userId = userId;
        this.preferColoredMap = preferColoredMap;
        this.selectedLanguage = selectedLanguage;
        this.unitSystem = unitSystem;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
        this.latestData = latestData;
        this.version = version;
    }
    public UserSettings(Long userId, boolean preferColoredMap, String selectedLanguage, UnitSystem unitSystem, Double homeLatitude, Double homeLongitude, Instant latestData) {
        this(userId, preferColoredMap, selectedLanguage, unitSystem, homeLatitude, homeLongitude, latestData, null);
    }

    public static UserSettings defaultSettings(Long userId) {
        return new UserSettings(userId, false, "en", UnitSystem.METRIC, null, null, null, null);
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
    
    public Long getVersion() {
        return version;
    }

    public UnitSystem getUnitSystem() {
        return unitSystem;
    }

    public Double getHomeLatitude() {
        return homeLatitude;
    }

    public Double getHomeLongitude() {
        return homeLongitude;
    }

    public Instant getLatestData() {
        return latestData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSettings that = (UserSettings) o;
        return preferColoredMap == that.preferColoredMap &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(selectedLanguage, that.selectedLanguage) &&
                Objects.equals(unitSystem, that.unitSystem) &&
                Objects.equals(homeLatitude, that.homeLatitude) &&
                Objects.equals(homeLongitude, that.homeLongitude) &&
                Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, preferColoredMap, selectedLanguage, unitSystem, homeLatitude, homeLongitude, version);
    }
    
    @Override
    public String toString() {
        return "UserSettings{" +
                "userId=" + userId +
                ", preferColoredMap=" + preferColoredMap +
                ", selectedLanguage='" + selectedLanguage + '\'' +
                ", unitSystem=" + unitSystem +
                ", homeLatitude=" + homeLatitude +
                ", homeLongitude=" + homeLongitude +
                ", latestData=" + latestData +
                ", version=" + version +
                '}';
    }
}
