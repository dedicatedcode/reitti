package com.dedicatedcode.reitti.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public enum TimeMode {
    TWELVE_HOUR,
    TWENTY_FOUR_HOUR;

    public String format(Instant instant, ZoneId zoneId) {
        DateTimeFormatter formatter = switch (this) {
            case TWELVE_HOUR -> DateTimeFormatter.ofPattern("h:mm a");
            case TWENTY_FOUR_HOUR -> DateTimeFormatter.ofPattern("HH:mm");
        };
        return instant.atZone(zoneId).format(formatter);
    }
}
