package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.geo.SignificantPlace.PlaceType;
import com.dedicatedcode.reitti.model.geo.TransportMode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public final class StatisticsData {

    private StatisticsData() {
    }

    public record Place(long id, String name, String city, String countryCode, PlaceType type,
                        double latitude, double longitude, ZoneId timezone) {
    }

    public record Visit(long placeId, Instant start, Instant end) {
        public long overlapSeconds(Instant from, Instant to) {
            return StatisticsData.overlapSeconds(start, end, from, to);
        }
    }

    public record Segment(TransportMode mode, long offsetSeconds, long durationSeconds, double distanceMeters) {
    }

    public record Trip(long id, Instant start, Instant end, Long startPlaceId, Long endPlaceId,
                       double distanceMeters, List<Segment> segments) {

        public long durationSeconds() {
            return Math.max(0, Duration.between(start, end).getSeconds());
        }

        public long overlapSeconds(Instant from, Instant to) {
            return StatisticsData.overlapSeconds(start, end, from, to);
        }

        public TransportMode dominantMode() {
            TransportMode best = TransportMode.UNKNOWN;
            long bestDuration = -1;
            for (Segment segment : segments) {
                if (segment.durationSeconds() > bestDuration) {
                    best = segment.mode();
                    bestDuration = segment.durationSeconds();
                }
            }
            return best;
        }

        public long distinctModes() {
            return segments.stream().map(Segment::mode).distinct().count();
        }
    }

    public record Memory(Instant start, Instant end) {
    }

    public static long overlapSeconds(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        Instant start = aStart.isAfter(bStart) ? aStart : bStart;
        Instant end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        return start.isBefore(end) ? Duration.between(start, end).getSeconds() : 0;
    }
}
