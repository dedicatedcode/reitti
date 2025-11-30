package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;

import java.time.Instant;
import java.time.ZoneId;

public record UserSettingsDTO(
        boolean preferColoredMap,
        Language selectedLanguage,
        Instant newestData,
        UnitSystem unitSystem,
        Double homeLatitude,
        Double homeLongitude,
        TilesCustomizationDTO tiles,
        UIMode uiMode,
        PhotoMode photoMode,
        TimeDisplayMode displayMode,
        ZoneId timezoneOverride,
        String customCssUrl
) {

    public enum UIMode {
        FULL,
        SHARED_FULL,
        VIEW_MEMORIES,
        SHARED_LIVE_MODE_ONLY
    }
    public enum PhotoMode {
        ENABLED,
        DISABLED
    }

    public record TilesCustomizationDTO(String service, String attribution){}

}
