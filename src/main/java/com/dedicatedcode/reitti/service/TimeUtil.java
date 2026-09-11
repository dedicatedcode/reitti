package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.UserSettings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    /**
     * Start of the given day according to the user's configured day boundary.
     * With 00:00 this equals the classic start of day, otherwise the day begins at the given time.
     */
    public static Instant startOfDay(LocalDate date, ZoneId zoneId, LocalTime dayStartTime) {
        if (dayStartTime == null || LocalTime.MIDNIGHT.equals(dayStartTime)) {
            return date.atStartOfDay(zoneId).toInstant();
        }
        return date.atTime(dayStartTime).atZone(zoneId).toInstant();
    }

    /**
     * End of the given day (inclusive last millisecond) according to the user's configured day boundary.
     */
    public static Instant endOfDay(LocalDate date, ZoneId zoneId, LocalTime dayStartTime) {
        return startOfDay(date.plusDays(1), zoneId, dayStartTime).minusMillis(1);
    }

    /**
     * The day the given instant belongs to according to the user's configured day boundary.
     */
    public static LocalDate dayKey(Instant instant, ZoneId zoneId, LocalTime dayStartTime) {
        LocalDate candidate = instant.atZone(zoneId).toLocalDate();
        Instant dayStart = startOfDay(candidate, zoneId, dayStartTime);
        return instant.isBefore(dayStart) ? candidate.minusDays(1) : candidate;
    }

    /**
     * The current day according to the user's configured day boundary. Before the configured
     * start time the previous calendar day is still considered "today".
     */
    public static LocalDate effectiveToday(ZoneId zoneId, LocalTime dayStartTime) {
        return effectiveToday(ZonedDateTime.now(zoneId), dayStartTime);
    }

    public static LocalDate effectiveToday(ZonedDateTime now, LocalTime dayStartTime) {
        if (dayStartTime != null && !LocalTime.MIDNIGHT.equals(dayStartTime) && now.toLocalTime().isBefore(dayStartTime)) {
            return now.toLocalDate().minusDays(1);
        }
        return now.toLocalDate();
    }

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
