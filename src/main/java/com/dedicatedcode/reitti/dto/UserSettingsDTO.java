package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;

import java.time.Instant;
import java.time.ZoneId;

public record UserSettingsDTO(
        Language selectedLanguage,
        String selectedLocale,
        Instant newestData,
        UnitSystem unitSystem,
        Double homeLatitude,
        Double homeLongitude,
        TilesCustomizationDTO tiles,
        UIMode uiMode,
        PhotoMode photoMode,
        TimeDisplayMode displayMode,
        TimeMode timeMode,
        ZoneId timezoneOverride,
        String customCssUrl,
        String timelineColor
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

    @Deprecated(forRemoval = true)
    public record TilesCustomizationDTO(String service, String attribution){}

}
