package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService.TransportModeOverride;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransportModeService {
    private static final Logger log = LoggerFactory.getLogger(TransportModeService.class);
    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    private static final long CHUNK_DURATION_SECONDS = 60;
    private static final long MIN_SEGMENT_DURATION_SECONDS = 60;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService,
                                TransportModeOverrideJdbcService transportModeOverrideJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.transportModeOverrideJdbcService = transportModeOverrideJdbcService;
    }

    public TransportMode inferTransportMode(User user, List<RawLocationPoint> tripPoints, Instant startTime, Instant endTime) {
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        List<TransportModeSegment> segments = segmentTrip(user, tripPoints, startTime, endTime, configs);
        return segments.stream()
                .collect(Collectors.groupingBy(TransportModeSegment::mode, Collectors.summingLong(TransportModeSegment::durationSeconds)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(TransportMode.UNKNOWN);
    }

    public void overrideTransportMode(User user, TransportMode transportMode, Trip trip) {
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, trip.getStartTime(), trip.getEndTime());
    }

    public void overrideTransportModeSegment(User user, TransportMode transportMode, Trip trip, long offsetSeconds, long durationSeconds) {
        Instant segmentStart = trip.getStartTime().plusSeconds(offsetSeconds);
        Instant segmentEnd = segmentStart.plusSeconds(durationSeconds);
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, segmentStart, segmentEnd);
    }

    public List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd) {
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        double totalDistanceMeters = GeoUtils.calculateTripDistance(points);
        return segmentTrip(user, points, tripStart, tripEnd, configs, totalDistanceMeters);
    }

    /**
     * Splits a trip into per-mode segments using fixed-time-window chunking.
     * Points are divided into CHUNK_DURATION_SECONDS windows, each chunk is
     * classified by its average speed, then short fluke segments are collapsed
     * and adjacent same-mode segments are merged. Always returns at least one
     * segment (UNKNOWN fallback if no chunks could be classified).
     */
    public List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd, List<TransportModeConfig> configs) {
        double totalDistanceMeters = points.size() >= 2 ? GeoUtils.calculateTripDistance(points) : 0.0;
        return segmentTrip(user, points, tripStart, tripEnd, configs, totalDistanceMeters);
    }

    private List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd, List<TransportModeConfig> configs, double totalDistanceMeters) {
        if (points.size() < 2) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            return List.of(new TransportModeSegment(TransportMode.UNKNOWN, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        // Load all overrides for the trip's time range upfront
        List<TransportModeOverride> overrides = this.transportModeOverrideJdbcService.getTransportModeOverrides(user, tripStart, tripEnd);

        List<ChunkClass> chunks = chunkAndClassify(points, configs);

        List<TransportModeSegment> result = new ArrayList<>();
        long chunkStartOffset = 0;

        for (ChunkClass chunk : chunks) {
            long chunkDuration = Math.max(1, chunk.durationSeconds());
            TransportMode mode = chunk.mode();

            // Check if any override midpoint falls within this chunk
            Instant chunkStart = tripStart.plusSeconds(chunkStartOffset);
            Instant chunkEnd = tripStart.plusSeconds(chunkStartOffset + chunkDuration);
            for (TransportModeOverride override : overrides) {
                if (!override.time().isBefore(chunkStart) && override.time().isBefore(chunkEnd)) {
                    mode = override.mode();
                    break;
                }
            }

            result.add(new TransportModeSegment(mode, chunkStartOffset, chunkDuration, chunk.distanceMeters()));
            chunkStartOffset += chunkDuration;
        }

        result = collapseShortSegments(result);
        result = mergeSameModeSegments(result);

        if (result.isEmpty()) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            result = List.of(new TransportModeSegment(TransportMode.UNKNOWN, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        return result;
    }

    private List<ChunkClass> chunkAndClassify(List<RawLocationPoint> points, List<TransportModeConfig> configs) {
        List<ChunkClass> chunks = new ArrayList<>();
        Instant tripStart = points.getFirst().getTimestamp();
        Instant tripEnd = points.getLast().getTimestamp();
        long totalDuration = Duration.between(tripStart, tripEnd).getSeconds();

        int pointIndex = 0;
        for (long offset = 0; offset < totalDuration; offset += CHUNK_DURATION_SECONDS) {
            long chunkEnd = Math.min(offset + CHUNK_DURATION_SECONDS, totalDuration);
            Instant chunkStartTime = tripStart.plusSeconds(offset);
            Instant chunkEndTime = tripStart.plusSeconds(chunkEnd);

            List<RawLocationPoint> chunkPoints = new ArrayList<>();
            while (pointIndex < points.size() && !points.get(pointIndex).getTimestamp().isAfter(chunkEndTime)) {
                chunkPoints.add(points.get(pointIndex));
                pointIndex++;
            }

            if (chunkPoints.size() < 2) {
                continue;
            }

            TransportMode mode = classifySegment(chunkPoints, configs);
            double distanceMeters = GeoUtils.calculateTripDistance(chunkPoints);
            chunks.add(new ChunkClass(mode, chunkEnd - offset, distanceMeters));
        }

        return chunks;
    }

    private TransportMode classifySegment(List<RawLocationPoint> segmentPoints, List<TransportModeConfig> configs) {
        List<Double> speeds = calculateSpeeds(segmentPoints);
        double avgSpeed = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        for (TransportModeConfig transportModeConfig : configs) {
            if (transportModeConfig.maxKmh() == null || transportModeConfig.maxKmh() > avgSpeed) {
                return transportModeConfig.mode();
            }
        }
        return TransportMode.UNKNOWN;
    }

    private List<Double> calculateSpeeds(List<RawLocationPoint> points) {
        List<Double> speeds = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double distanceKm = GeoUtils.distanceInMeters(points.get(i - 1), points.get(i)) / 1000.0;
            Duration timeDiff = Duration.between(points.get(i - 1).getTimestamp(), points.get(i).getTimestamp());
            double timeHours = timeDiff.toMillis() / (1000.0 * 3600.0);
            double speedKmH = timeHours > 0 ? distanceKm / timeHours : 0;
            speeds.add(speedKmH);
        }
        return speeds;
    }

    public List<TransportModeSegment> mergeSameModeSegments(List<TransportModeSegment> segments) {
        if (segments.size() < 2) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>();
        TransportModeSegment current = segments.getFirst();
        for (int i = 1; i < segments.size(); i++) {
            TransportModeSegment next = segments.get(i);
            if (current.mode() == next.mode()) {
                current = new TransportModeSegment(
                        current.mode(),
                        current.offsetSeconds(),
                        current.durationSeconds() + next.durationSeconds(),
                        current.distanceMeters() + next.distanceMeters()
                );
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    private List<TransportModeSegment> collapseShortSegments(List<TransportModeSegment> segments) {
        if (segments.size() < 3) {
            return segments;
        }

        List<TransportModeSegment> result = new ArrayList<>(segments);
        boolean changed;
        do {
            changed = false;
            List<TransportModeSegment> next = new ArrayList<>();
            for (int i = 0; i < result.size(); i++) {
                TransportModeSegment current = result.get(i);
                if (i == 0 || i == result.size() - 1) {
                    next.add(current);
                    continue;
                }
                TransportModeSegment prev = result.get(i - 1);
                TransportModeSegment nextSeg = result.get(i + 1);
                if (prev.mode() == nextSeg.mode() && current.mode() != prev.mode()
                        && current.durationSeconds() < MIN_SEGMENT_DURATION_SECONDS) {
                    changed = true;
                } else {
                    next.add(current);
                }
            }
            result = next;
        } while (changed);

        return result;
    }

    /**
     * Temporary holder for a chunk's classification result.
     */
    private record ChunkClass(TransportMode mode, long durationSeconds, double distanceMeters) {
    }
}
