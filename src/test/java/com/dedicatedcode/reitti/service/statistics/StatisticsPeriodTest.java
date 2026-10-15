package com.dedicatedcode.reitti.service.statistics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsPeriodTest {

    @Test
    void parsesKeys() {
        assertEquals(StatisticsPeriod.ofMonth(2026, 9), StatisticsPeriod.parse("2026-9").orElseThrow());
        assertEquals(StatisticsPeriod.ofMonth(2026, 9), StatisticsPeriod.parse("2026-09").orElseThrow());
        assertEquals(StatisticsPeriod.ofYear(2026), StatisticsPeriod.parse("2026").orElseThrow());
        assertEquals(StatisticsPeriod.all(), StatisticsPeriod.parse("all").orElseThrow());
        assertTrue(StatisticsPeriod.parse("2026-13").isEmpty());
        assertTrue(StatisticsPeriod.parse("").isEmpty());
        assertTrue(StatisticsPeriod.parse("foo").isEmpty());
    }

    @Test
    void navigatesAcrossYearBoundaries() {
        StatisticsPeriod january = StatisticsPeriod.ofMonth(2026, 1);
        assertEquals(StatisticsPeriod.ofMonth(2025, 12), january.previous().orElseThrow());
        assertEquals(StatisticsPeriod.ofMonth(2026, 2), january.next().orElseThrow());
        assertTrue(StatisticsPeriod.all().previous().isEmpty());
    }

    @Test
    void providesDateRangesAndPaths() {
        StatisticsPeriod february = StatisticsPeriod.ofMonth(2028, 2);
        assertEquals(LocalDate.of(2028, 2, 1), february.startDate(null));
        assertEquals(LocalDate.of(2028, 3, 1), february.endDateExclusive(null));
        assertEquals("/statistics/2028/2", february.path());
        assertEquals("/statistics/2028", StatisticsPeriod.ofYear(2028).path());
        assertEquals("/statistics/all", StatisticsPeriod.all().path());
        LocalDate fallback = LocalDate.of(2020, 5, 5);
        assertEquals(fallback, StatisticsPeriod.all().startDate(fallback));
    }
}
