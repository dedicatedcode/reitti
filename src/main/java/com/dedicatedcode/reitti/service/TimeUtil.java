package com.dedicatedcode.reitti.service;

import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TimeUtil {
    public static LocalDateTime adjustInstant(Instant instant) {
        return instant.atZone(ZoneId.of("UTC")).withZoneSameInstant(LocaleContextHolder.getTimeZone().toZoneId()).toLocalDateTime();
    }
}
