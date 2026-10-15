package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.service.I18nService;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class StatisticsFormatter {

    private static final double METERS_PER_MILE = 1609.344;

    private final I18nService i18n;
    private final Locale locale;
    private final UnitSystem unitSystem;
    private final TimeMode timeMode;

    public StatisticsFormatter(I18nService i18n, Locale locale, UnitSystem unitSystem, TimeMode timeMode) {
        this.i18n = i18n;
        this.locale = locale;
        this.unitSystem = unitSystem != null ? unitSystem : UnitSystem.METRIC;
        this.timeMode = timeMode != null ? timeMode : TimeMode.TWENTY_FOUR_HOUR;
    }

    public String t(String key, Object... args) {
        return i18n.translate(key, args);
    }

    public double toDistanceUnit(double meters) {
        return unitSystem == UnitSystem.IMPERIAL ? meters / METERS_PER_MILE : meters / 1000.0;
    }

    public String distanceUnit() {
        return unitSystem == UnitSystem.IMPERIAL ? "mi" : "km";
    }

    public String distanceNumber(double meters) {
        double value = toDistanceUnit(meters);
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(value < 10 && value > 0 ? 1 : 0);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    public String distance(double meters) {
        return distanceNumber(meters) + " " + distanceUnit();
    }

    public String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }

    public String percentNumber(double share) {
        return number(Math.round(share * 100));
    }

    public String percent(double share) {
        return percentNumber(share) + "%";
    }

    public String percentWithDecimal(double share) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        return format.format(share * 100) + "%";
    }

    public String signedPercent(double share) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        double value = share * 100;
        return (value >= 0 ? "+" : "−") + format.format(Math.abs(value)) + "%";
    }

    public String signed(long value) {
        return (value >= 0 ? "+" : "−") + Math.abs(value);
    }

    public String duration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        if (duration.toMinutes() < 60) {
            return t("statistics.format.minutes", duration.toMinutes());
        }
        double hours = seconds / 3600.0;
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(hours < 10 ? 1 : 0);
        return t("statistics.format.hours", format.format(hours));
    }

    public String time(LocalTime time) {
        if (time == null) {
            return "–";
        }
        return time.format(DateTimeFormatter.ofPattern(timeMode.getPattern(), locale));
    }

    public String day(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("d MMM", locale));
    }

    public String date(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale));
    }

    public String monthName(int month) {
        return t("month." + month);
    }

    public String modeName(com.dedicatedcode.reitti.model.geo.TransportMode mode) {
        return t("transportation.mode." + mode.name() + ".name");
    }

    public String countryName(String countryCode) {
        try {
            return t("country." + countryCode.toLowerCase(Locale.ROOT) + ".label");
        } catch (org.springframework.context.NoSuchMessageException e) {
            return countryCode.toUpperCase(Locale.ROOT);
        }
    }
}
