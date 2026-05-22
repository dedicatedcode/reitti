package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.UserSettings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeUtil {
    public static LocalDateTime adjustInstant(Instant instant, ZoneId zoneId) {
        if (instant == null || zoneId == null) {
            return null;
        } else {
            return instant.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId).toLocalDateTime();
        }
    }

    public static String formatTimeRange(Instant startTime, Instant endTime, ZoneId startTimezone, ZoneId endTimezone, LocalDate selectedDate, UserSettings userSettings) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d " + userSettings.getTimeMode().getPattern());

        LocalDate startDate = startTime.atZone(startTimezone).toLocalDate();
        LocalDate endDate = endTime.atZone(endTimezone).toLocalDate();
        LocalDate selectedDateInStartTimezone = selectedDate.atTime(10,0).atZone(startTimezone).toLocalDate();
        LocalDate selectedDateInEndTimezone = selectedDate.atTime(10,0).atZone(endTimezone).toLocalDate();
        String start, end;

        // If start time is not on the selected date, show date + time
        if (!startDate.equals(selectedDateInStartTimezone)) {
            start = startTime.atZone(startTimezone).format(dateTimeFormatter);
        } else {
            start = userSettings.getTimeMode().format(startTime, startTimezone);
        }

        // If end time is not on the selected date, show date + time
        if (!endDate.equals(selectedDateInEndTimezone)) {
            end = endTime.atZone(endTimezone).format(dateTimeFormatter);
        } else {
            end = userSettings.getTimeMode().format(endTime, endTimezone);
        }

        return start + " - " + end;
    }

    public static String formatTimeRange(Instant startTime, Instant endTime, ZoneId timezone, LocalDate selectedDate, UserSettings userSettings) {
        return formatTimeRange(startTime, endTime, timezone, timezone, selectedDate, userSettings);
    }
}
