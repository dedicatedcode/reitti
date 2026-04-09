package com.dedicatedcode.reitti.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public enum TimeMode {
    TWELVE_HOUR("h:mm a"),
    TWENTY_FOUR_HOUR("HH:mm");

    private final String pattern;

    TimeMode(String pattern) {
        this.pattern = pattern;
    }
    public String getPattern() {
        return pattern;
    }

    public String format(Instant instant, ZoneId zoneId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(this.pattern);
        return instant.atZone(zoneId).format(formatter);
    }
}
