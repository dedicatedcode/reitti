package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;

import java.time.Instant;
import java.time.ZoneId;

public record UserSettingsDTO(
        boolean preferColoredMap,
        String selectedLanguage,
        Instant newestData,
        UnitSystem unitSystem,
        Double homeLatitude,
        Double homeLongitude,
        TilesCustomizationDTO tiles,
        UIMode uiMode,
        TimeDisplayMode displayMode,
        ZoneId timezoneOverride
) {

    public enum UIMode {
        FULL,
        SHARED_FULL,
        SHARED_LIVE_MODE_ONLY
    }

    public record TilesCustomizationDTO(String service, String attribution){}

}
