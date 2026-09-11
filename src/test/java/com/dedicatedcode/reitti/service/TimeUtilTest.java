package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {

    private static final ZoneId TZ = ZoneId.of("Europe/Berlin");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);

    @Test
    void startOfDay_withMidnight_returnsClassicStartOfDay() {
        Instant expected = DATE.atStartOfDay(TZ).toInstant();
        assertEquals(expected, TimeUtil.startOfDay(DATE, TZ, LocalTime.MIDNIGHT));
        assertEquals(expected, TimeUtil.startOfDay(DATE, TZ, null));
    }

    @Test
    void startOfDay_withCustomTime_shiftsBoundary() {
        assertEquals(DATE.atTime(4, 0).atZone(TZ).toInstant(), TimeUtil.startOfDay(DATE, TZ, LocalTime.of(4, 0)));
        assertEquals(DATE.atTime(3, 30).atZone(TZ).toInstant(), TimeUtil.startOfDay(DATE, TZ, LocalTime.of(3, 30)));
    }

    @Test
    void endOfDay_withCustomTime_isOneMillisBeforeNextDayStart() {
        Instant nextDayStart = DATE.plusDays(1).atTime(4, 0).atZone(TZ).toInstant();
        assertEquals(nextDayStart.minusMillis(1), TimeUtil.endOfDay(DATE, TZ, LocalTime.of(4, 0)));
        assertEquals(DATE.plusDays(1).atStartOfDay(TZ).toInstant().minusMillis(1), TimeUtil.endOfDay(DATE, TZ, LocalTime.MIDNIGHT));
    }

    @Test
    void dayKey_beforeBoundary_belongsToPreviousDay() {
        Instant earlyMorning = DATE.atTime(1, 0).atZone(TZ).toInstant();
        assertEquals(DATE.minusDays(1), TimeUtil.dayKey(earlyMorning, TZ, LocalTime.of(4, 0)));
    }

    @Test
    void dayKey_atBoundary_belongsToCurrentDay() {
        Instant atBoundary = DATE.atTime(4, 0).atZone(TZ).toInstant();
        assertEquals(DATE, TimeUtil.dayKey(atBoundary, TZ, LocalTime.of(4, 0)));
    }

    @Test
    void dayKey_afterBoundary_belongsToCurrentDay() {
        Instant noon = DATE.atTime(12, 0).atZone(TZ).toInstant();
        assertEquals(DATE, TimeUtil.dayKey(noon, TZ, LocalTime.of(4, 0)));
    }

    @Test
    void dayKey_withMidnight_matchesCalendarDate() {
        Instant beforeMidnight = DATE.minusDays(1).atTime(23, 59).atZone(TZ).toInstant();
        assertEquals(DATE.minusDays(1), TimeUtil.dayKey(beforeMidnight, TZ, LocalTime.MIDNIGHT));
        Instant afterMidnight = DATE.atTime(0, 1).atZone(TZ).toInstant();
        assertEquals(DATE, TimeUtil.dayKey(afterMidnight, TZ, LocalTime.MIDNIGHT));
    }

    @Test
    void dayKey_handlesDstFallBackDay() {
        // Berlin fall-back: 2025-10-26 03:00 -> 02:00. The local time 03:30 occurs twice; both
        // occurrences are before the 04:00 boundary, so both belong to the previous day.
        LocalDate fallBackDay = LocalDate.of(2025, 10, 26);
        ZonedDateTime ambiguous = fallBackDay.atTime(3, 30).atZone(TZ);
        Instant earlierOccurrence = ambiguous.withEarlierOffsetAtOverlap().toInstant();
        Instant laterOccurrence = ambiguous.withLaterOffsetAtOverlap().toInstant();

        assertEquals(fallBackDay.minusDays(1), TimeUtil.dayKey(earlierOccurrence, TZ, LocalTime.of(4, 0)));
        assertEquals(fallBackDay.minusDays(1), TimeUtil.dayKey(laterOccurrence, TZ, LocalTime.of(4, 0)));
    }

    @Test
    void effectiveToday_beforeBoundary_returnsYesterday() {
        ZonedDateTime now = LocalDateTime.of(2026, 9, 11, 2, 0).atZone(TZ);
        assertEquals(LocalDate.of(2026, 9, 10), TimeUtil.effectiveToday(now, LocalTime.of(4, 0)));
    }

    @Test
    void effectiveToday_atOrAfterBoundary_returnsToday() {
        ZonedDateTime atBoundary = LocalDateTime.of(2026, 9, 11, 4, 0).atZone(TZ);
        assertEquals(LocalDate.of(2026, 9, 11), TimeUtil.effectiveToday(atBoundary, LocalTime.of(4, 0)));

        ZonedDateTime lateEvening = LocalDateTime.of(2026, 9, 11, 23, 30).atZone(TZ);
        assertEquals(LocalDate.of(2026, 9, 11), TimeUtil.effectiveToday(lateEvening, LocalTime.of(4, 0)));
    }

    @Test
    void effectiveToday_withMidnight_alwaysReturnsCalendarDate() {
        ZonedDateTime now = LocalDateTime.of(2026, 9, 11, 2, 0).atZone(TZ);
        assertEquals(LocalDate.of(2026, 9, 11), TimeUtil.effectiveToday(now, LocalTime.MIDNIGHT));
        assertEquals(LocalDate.of(2026, 9, 11), TimeUtil.effectiveToday(now, null));
    }
}