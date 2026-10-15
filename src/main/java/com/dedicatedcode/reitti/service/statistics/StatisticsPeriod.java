package com.dedicatedcode.reitti.service.statistics;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StatisticsPeriod(Type type, Integer year, Integer month) implements Serializable {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{1,2})$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})$");

    public enum Type {MONTH, YEAR, ALL}

    public static StatisticsPeriod ofMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }
        return new StatisticsPeriod(Type.MONTH, year, month);
    }

    public static StatisticsPeriod ofYear(int year) {
        return new StatisticsPeriod(Type.YEAR, year, null);
    }

    public static StatisticsPeriod all() {
        return new StatisticsPeriod(Type.ALL, null, null);
    }

    public static StatisticsPeriod ofMonth(YearMonth yearMonth) {
        return ofMonth(yearMonth.getYear(), yearMonth.getMonthValue());
    }

    public static Optional<StatisticsPeriod> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if ("all".equalsIgnoreCase(value)) {
            return Optional.of(all());
        }
        Matcher monthMatcher = MONTH_PATTERN.matcher(value);
        if (monthMatcher.matches()) {
            int month = Integer.parseInt(monthMatcher.group(2));
            if (month < 1 || month > 12) {
                return Optional.empty();
            }
            return Optional.of(ofMonth(Integer.parseInt(monthMatcher.group(1)), month));
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(value);
        if (yearMatcher.matches()) {
            return Optional.of(ofYear(Integer.parseInt(yearMatcher.group(1))));
        }
        return Optional.empty();
    }

    public String key() {
        return switch (type) {
            case MONTH -> year + "-" + month;
            case YEAR -> String.valueOf(year);
            case ALL -> "all";
        };
    }

    public String path() {
        return switch (type) {
            case MONTH -> "/statistics/" + year + "/" + month;
            case YEAR -> "/statistics/" + year;
            case ALL -> "/statistics/all";
        };
    }

    public boolean isMonth() {
        return type == Type.MONTH;
    }

    public boolean isYear() {
        return type == Type.YEAR;
    }

    public boolean isAll() {
        return type == Type.ALL;
    }

    /**
     * First day of the period. For {@link Type#ALL} the given fallback is used.
     */
    public LocalDate startDate(LocalDate allFallback) {
        return switch (type) {
            case MONTH -> LocalDate.of(year, month, 1);
            case YEAR -> LocalDate.of(year, 1, 1);
            case ALL -> allFallback;
        };
    }

    /**
     * Day after the last day of the period. For {@link Type#ALL} the given fallback is used.
     */
    public LocalDate endDateExclusive(LocalDate allFallback) {
        return switch (type) {
            case MONTH -> LocalDate.of(year, month, 1).plusMonths(1);
            case YEAR -> LocalDate.of(year + 1, 1, 1);
            case ALL -> allFallback;
        };
    }

    public Optional<StatisticsPeriod> previous() {
        return switch (type) {
            case MONTH -> Optional.of(ofMonth(YearMonth.of(year, month).minusMonths(1)));
            case YEAR -> Optional.of(ofYear(year - 1));
            case ALL -> Optional.empty();
        };
    }

    public Optional<StatisticsPeriod> next() {
        return switch (type) {
            case MONTH -> Optional.of(ofMonth(YearMonth.of(year, month).plusMonths(1)));
            case YEAR -> Optional.of(ofYear(year + 1));
            case ALL -> Optional.empty();
        };
    }

    public StatisticsPeriod asYear() {
        return type == Type.ALL ? this : ofYear(year);
    }
}
