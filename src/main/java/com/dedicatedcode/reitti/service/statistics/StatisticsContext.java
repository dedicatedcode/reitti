package com.dedicatedcode.reitti.service.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Everything the report builder needs for one period, loaded once per request.
 */
public record StatisticsContext(
        StatisticsPeriod period,
        ZoneId zone,
        LocalTime dayStartTime,
        LocalDate today,
        LocalDate start,
        LocalDate end,
        Map<Long, StatisticsData.Place> places,
        Map<Long, Instant> firstVisits,
        List<StatisticsData.Visit> visits,
        List<StatisticsData.Trip> trips,
        SortedMap<LocalDate, NightCalculator.Night> nights,
        HomeResolver.HomeTimeline homes,
        List<DayCalculator.Day> days,
        StatisticsPeriod comparePeriod,
        List<DayCalculator.Day> compareDays,
        List<StatisticsPeriod> compareCandidates,
        List<DayCalculator.Day> yearDays,
        List<StatisticsData.Memory> memories,
        List<YearMonth> availableMonths,
        Set<Long> secondBaseCandidates,
        Long homeSuggestion,
        int homeSuggestionNights,
        boolean photoCheckAvailable
) {
}
