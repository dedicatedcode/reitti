package com.dedicatedcode.reitti.dto.timeline;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.Mood;

import java.time.LocalDate;
import java.util.*;

public record GroupedTimelineEntry(UUID syntheticId, String name, String subHeadline, String href, List<OverviewEntry> overview, Long visits, Long trips, List<MoodValue> visitMoods, List<MoodValue> tripMoods, List<TransportEntry> transportEntries, List<VisitEntry> visitEntries) implements TimelineEntry {

    @Override
    public boolean isAggregated() {
        return true;
    }

    public record OverviewEntry(LocalDate slot, Long visits, Long trips) {
    }

    public record TransportEntry(TransportMode transportMode, List<TransportModePart> parts) {
        public long durationSeconds() {
            return parts.stream().mapToLong(TransportModePart::durationSeconds).sum();
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

    public record TransportModePart(TransportMode transportMode, Mood mood, long durationSeconds, double percent) {
    }
    public record VisitPart(Long placeId, String placeName, Mood mood, long durationSeconds, double percent) {
    }
}
