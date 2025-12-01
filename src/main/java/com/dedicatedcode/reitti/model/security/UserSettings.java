package com.dedicatedcode.reitti.model.security;

import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public class UserSettings {
    
    private final Long userId;
    private final boolean preferColoredMap;
    private final Language selectedLanguage;
    private final UnitSystem unitSystem;
    private final Double homeLatitude;
    private final Double homeLongitude;
    private final ZoneId timeZoneOverride;
    private final TimeDisplayMode timeDisplayMode;
    private final String customCss;
    private final Instant latestData;
    private final String color;
    private final Long version;

    public UserSettings(Long userId, boolean preferColoredMap, Language selectedLanguage, UnitSystem unitSystem, Double homeLatitude, Double homeLongitude, ZoneId timeZoneOverride, TimeDisplayMode timeDisplayMode, String customCss, Instant latestData, String color, Long version) {
        this.userId = userId;
        this.preferColoredMap = preferColoredMap;
        this.selectedLanguage = selectedLanguage;
        this.unitSystem = unitSystem;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
        this.timeZoneOverride = timeZoneOverride;
        this.timeDisplayMode = timeDisplayMode;
        this.customCss = customCss;
        this.latestData = latestData;
        this.color = color;
        this.version = version;
    }

    public static UserSettings defaultSettings(Long userId) {
        return new UserSettings(userId, false, Language.EN, UnitSystem.METRIC, null, null, null, TimeDisplayMode.DEFAULT, null, null, "#f1ba63", null);
    }
    public Long getUserId() {
        return userId;
    }
    
    public boolean isPreferColoredMap() {
        return preferColoredMap;
    }
    
    public Language getSelectedLanguage() {
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

    public TimeDisplayMode getTimeDisplayMode() {
        return timeDisplayMode;
    }

    public ZoneId getTimeZoneOverride() {
        return timeZoneOverride;
    }

    public String getCustomCss() {
        return customCss;
    }

    public String getColor() {
        return color;
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
                Objects.equals(timeZoneOverride, that.timeZoneOverride) &&
                Objects.equals(timeDisplayMode, that.timeDisplayMode) &&
                Objects.equals(customCss, that.customCss) &&
                Objects.equals(latestData, that.latestData) &&
                Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, preferColoredMap, selectedLanguage, unitSystem, homeLatitude, homeLongitude, timeZoneOverride, timeDisplayMode, customCss, latestData, version);
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
                ", timeZoneOverride=" + timeZoneOverride +
                ", timeDisplayMode=" + timeDisplayMode +
                ", customCss=" + (customCss != null ? "[" + customCss.length() + " chars]" : "null") +
                ", latestData=" + latestData +
                ", version=" + version +
                '}';
    }

    public UserSettings withVersion(long version) {
        return new UserSettings(userId, preferColoredMap, selectedLanguage, unitSystem, homeLatitude, homeLongitude, timeZoneOverride, timeDisplayMode, customCss, latestData, color, version);
    }
}
