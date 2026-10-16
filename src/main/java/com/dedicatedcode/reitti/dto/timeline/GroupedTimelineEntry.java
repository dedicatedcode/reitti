package com.dedicatedcode.reitti.dto.timeline;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;

import java.time.LocalDate;
import java.util.*;

public record GroupedTimelineEntry(UUID syntheticId, String name, String subHeadline, String href, boolean monthly, int leadingBlanks, List<OverviewEntry> overview, List<ActivityCell> activity, String formattedDistance, String distanceUnit, Long visits, Long trips, List<MoodValue> visitMoods, List<MoodValue> tripMoods, List<TransportEntry> transportEntries, List<VisitEntry> visitEntries, MovementView movement, List<TopPlace> topPlaces) implements TimelineEntry {

    @Override
    public boolean isAggregated() {
        return true;
    }

    public record OverviewEntry(LocalDate slot, Long visits, Long trips, int level, String title) {
    }

    public record ActivityCell(boolean blank, int level, String title) {
    }

    public record MovementView(List<Bar> bars, List<LegendEntry> legend) {
        public record Bar(String labelKey, List<Segment> segments) {
        }

        public record Segment(String mode, double percent, String title) {
        }

        public record LegendEntry(String mode, String label, String moodIcon) {
        }
    }

    public record TopPlace(String name, String duration) {
    }

    public record TransportEntry(TransportMode transportMode, List<TransportModePart> parts) {
        public long durationSeconds() {
            return parts.stream().mapToLong(TransportModePart::durationSeconds).sum();
        }

        public long totalDistanceMeters() {
            return parts.stream().mapToLong(TransportModePart::distanceMeters).sum();
        }

        public Optional<Mood> dominantMood() {
            return parts.stream().sorted(Comparator.comparing(TransportModePart::percent)).filter(p -> p.percent > 0.5).map(TransportModePart::mood).filter(Objects::nonNull).findFirst();
        }
    }

    public record VisitEntry(String name, List<VisitPart> parts) {
        public long durationSeconds() {
            return parts.stream().mapToLong(VisitPart::durationSeconds).sum();
        }

        public Optional<Mood> dominantMood() {
            return parts.stream().sorted(Comparator.comparing(VisitPart::percent)).filter(p -> p.percent > 0.5).map(VisitPart::mood).findFirst();
        }
    }

    public record MoodValue(Mood mood, long amount, long durationSeconds) {
    }

    public record TransportModePart(TransportMode transportMode, Mood mood, long durationSeconds, long distanceMeters, double percent) {
    }
    public record VisitPart(Long placeId, String placeName, Mood mood, long durationSeconds, double percent) {
    }
}
