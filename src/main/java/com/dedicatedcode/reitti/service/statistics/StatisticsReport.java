package com.dedicatedcode.reitti.service.statistics;

import java.io.Serializable;
import java.util.List;

public record StatisticsReport(
        StatisticsPeriod period,
        StatisticsPeriod comparePeriod,
        java.time.Instant rangeStart,
        java.time.Instant rangeEnd,
        String title,
        boolean hasData,
        Navigation navigation,
        String headline,
        String trackingLine,
        List<Kpi> kpis,
        String kpiNote,
        HomeInfo home,
        WeekSplit weekSplit,
        Movement movement,
        DayTypes dayTypes,
        Donut timeSplit,
        List<String> distanceNotes,
        UsualTrip usualTrip,
        Radar radar,
        List<TextLine> routines,
        List<String> stays,
        Comparison comparison,
        List<TextLine> leftovers,
        boolean photoCheckAvailable,
        List<StayWindow> longStays,
        List<String> footnotes
) implements Serializable {

    public record Navigation(StatisticsPeriod.Type type,
                             Link previous,
                             Link next,
                             Link monthToggle,
                             Link yearToggle,
                             Link allToggle,
                             List<YearOption> years) implements Serializable {
    }

    public record Link(String key, String label, String path, boolean active) implements Serializable {
    }

    public record YearOption(int year, Link yearLink, List<Link> months) implements Serializable {
    }

    public record Kpi(String value, String unit, String label) implements Serializable {
    }

    public record HomeInfo(String name, Double latitude, Double longitude, HomeResolver.Source source, String description,
                           String changingText, Suggestion suggestion) implements Serializable {
    }

    public record Suggestion(long placeId, String text) implements Serializable {
    }

    public record WeekSplit(String weekdayLabel, String weekendLabel, List<Row> rows) implements Serializable {
    }

    public record Row(String label, String weekday, String weekend) implements Serializable {
    }

    public record Movement(List<Bar> bars, List<Legend> legend, String caption, String speeds) implements Serializable {
    }

    public record Bar(String label, List<BarSegment> segments) implements Serializable {
    }

    public record BarSegment(String mode, String title, double percent) implements Serializable {
    }

    public record Legend(String mode, String label) implements Serializable {
    }

    public record DayTypes(Donut donut, List<CalendarCell> calendar, int leadingBlanks, String summary) implements Serializable {
    }

    public record CalendarCell(String type, String title, boolean thin) implements Serializable {
    }

    public record Donut(String centerLabel, List<DonutSlice> slices, String gradient) implements Serializable {
    }

    public record DonutSlice(String key, String label, String percent) implements Serializable {
    }

    public record UsualTrip(String title, String frequency, String duration, double share) implements Serializable {
    }

    public record Radar(List<RadarDot> dots, List<RadarRing> rings, String maxLabel) implements Serializable {
    }

    public record RadarDot(double x, double y, double r, String title) implements Serializable {
    }

    public record RadarRing(double r, double labelX, double labelY, String label) implements Serializable {
    }

    public record Comparison(String headline, List<String> lines, List<String> highlights, List<TopPlace> topPlaces,
                             List<CompareOption> options) implements Serializable {

        public List<CompareOption> suggestedOptions() {
            return options.stream().filter(CompareOption::suggested).toList();
        }

        public List<CompareOption> otherOptions() {
            return options.stream().filter(o -> !o.suggested()).toList();
        }
    }

    public record CompareOption(String key, String label, boolean suggested, boolean selected) implements Serializable {
    }

    public record StayWindow(java.time.Instant start, java.time.Instant end) implements Serializable {
    }

    public record TopPlace(String name, String hours, String visits, String href) implements Serializable {
    }

    public record TextLine(LineLink lead, String text, List<LineLink> links) implements Serializable {
        public TextLine(String text, List<LineLink> links) {
            this(null, text, links);
        }

        public static TextLine of(String text) {
            return new TextLine(null, text, List.of());
        }
    }

    public record LineLink(String label, String href) implements Serializable {
    }
}
