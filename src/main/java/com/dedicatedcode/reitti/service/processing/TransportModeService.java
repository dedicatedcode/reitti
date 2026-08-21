package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService.TransportModeOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransportModeService {
    private static final Logger log = LoggerFactory.getLogger(TransportModeService.class);
    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    private static final long CHUNK_DURATION_SECONDS = 60;
    private static final long MIN_SEGMENT_DURATION_SECONDS = CHUNK_DURATION_SECONDS * 3;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService,
                                TransportModeOverrideJdbcService transportModeOverrideJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.transportModeOverrideJdbcService = transportModeOverrideJdbcService;
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

    private List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd, List<TransportModeConfig> configs, double totalDistanceMeters) {
        if (points.size() < 2) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            log.debug("segmentTrip: only {} point(s), returning single UNKNOWN segment, duration={}s", points.size(), duration);
            return List.of(new TransportModeSegment(TransportMode.UNKNOWN, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        // Load all overrides for the trip's time range upfront
        List<TransportModeOverride> overrides = this.transportModeOverrideJdbcService.getTransportModeOverrides(user, tripStart, tripEnd);
        if (!overrides.isEmpty()) {
            log.trace("segmentTrip: loaded {} override(s) for trip [{}..{}]", overrides.size(), tripStart, tripEnd);
        }

        List<ChunkClass> chunks = chunkAndClassify(points, configs);
        log.trace("segmentTrip: {} points, {} chunks, trip [{}-{}]", points.size(), chunks.size(), tripStart, tripEnd);

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
                    log.trace("segmentTrip: chunk at +{}s raw={}, override matches at {} → {}", chunkStartOffset, mode, override.time(), override.mode());
                    mode = override.mode();
                    break;
                }
            }

            result.add(new TransportModeSegment(mode, chunkStartOffset, chunkDuration, chunk.distanceMeters()));
            chunkStartOffset += chunkDuration;
        }

        log.trace("segmentTrip: before post-processing: {} segments: {}", result.size(), segmentSummary(result));
        result = collapseShortSegments(result);
        log.trace("segmentTrip: after collapse: {} segments: {}", result.size(), segmentSummary(result));
        result = mergeSameModeSegments(result);
        log.trace("segmentTrip: after merge: {} segments: {}", result.size(), segmentSummary(result));

        // Ensure segments tile the trip time range contiguously
        for (int i = 0; i < result.size() - 1; i++) {
            TransportModeSegment cur = result.get(i);
            TransportModeSegment next = result.get(i + 1);
            long curEnd = cur.offsetSeconds() + cur.durationSeconds();
            if (curEnd < next.offsetSeconds()) {
                result.set(i, new TransportModeSegment(
                        cur.mode(),
                        cur.offsetSeconds(),
                        next.offsetSeconds() - cur.offsetSeconds(),
                        cur.distanceMeters()
                ));
                log.trace("segmentTrip: extended segment {} at +{}s to cover gap of {}s", i, cur.offsetSeconds(), next.offsetSeconds() - curEnd);
            }
        }

        // Adjust segment offsets from first-point-relative to tripStart-relative
        long firstPointOffset = Duration.between(tripStart, points.getFirst().getTimestamp()).getSeconds();
        if (firstPointOffset > 0) {
            result = result.stream()
                    .map(s -> new TransportModeSegment(s.mode(), s.offsetSeconds() + firstPointOffset, s.durationSeconds(), s.distanceMeters()))
                    .toList();
            log.trace("segmentTrip: shifted {} segment(s) by +{}s (first point is {}s after tripStart)",
                    result.size(), firstPointOffset, firstPointOffset);
        }

        if (result.isEmpty()) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            log.trace("segmentTrip: no segments after post-processing, returning single UNKNOWN");
            result = List.of(new TransportModeSegment(TransportMode.UNKNOWN, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        return result;
    }

    private String segmentSummary(List<TransportModeSegment> segments) {
        return segments.stream()
                .map(s -> s.mode() + "@" + s.offsetSeconds() + "+" + s.durationSeconds() + "s")
                .collect(Collectors.joining(", "));
    }

    private List<ChunkClass> chunkAndClassify(List<RawLocationPoint> points, List<TransportModeConfig> configs) {
        List<ChunkClass> chunks = new ArrayList<>();
        Instant tripStart = points.getFirst().getTimestamp();
        Instant tripEnd = points.getLast().getTimestamp();
        long totalDuration = Duration.between(tripStart, tripEnd).getSeconds();

        int pointIndex = 0;
        int chunkCount = 0;
        for (long offset = 0; offset < totalDuration; offset += CHUNK_DURATION_SECONDS) {
            long chunkEnd = Math.min(offset + CHUNK_DURATION_SECONDS, totalDuration);
            Instant chunkEndTime = tripStart.plusSeconds(chunkEnd);

            List<RawLocationPoint> chunkPoints = new ArrayList<>();
            while (pointIndex < points.size() && !points.get(pointIndex).getTimestamp().isAfter(chunkEndTime)) {
                chunkPoints.add(points.get(pointIndex));
                pointIndex++;
            }

            if (chunkPoints.size() < 2) {
                log.trace("chunkAndClassify: chunk {} at +{}s skipped ({} point(s))", chunkCount, offset, chunkPoints.size());
                chunkCount++;
                continue;
            }

            TransportMode mode = classifySegment(chunkPoints, configs);
            double distanceMeters = GeoUtils.calculateTripDistance(chunkPoints);
            double avgSpeed = calculateAverageSpeedKmh(chunkPoints);
            log.trace("chunkAndClassify: chunk {} at +{}s: {} pts, avgSpeed={} km/h, distance={}m → {}", chunkCount, offset, chunkPoints.size(), String.format("%.1f", avgSpeed), String.format("%.0f", distanceMeters), mode);
            chunks.add(new ChunkClass(mode, chunkEnd - offset, distanceMeters));
            chunkCount++;
        }

        log.trace("chunkAndClassify: {} chunks from {} points over {}s", chunks.size(), points.size(), totalDuration);
        return chunks;
    }

    private double calculateAverageSpeedKmh(List<RawLocationPoint> points) {
        List<Double> speeds = calculateSpeeds(points);
        return speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private TransportMode classifySegment(List<RawLocationPoint> segmentPoints, List<TransportModeConfig> configs) {
        double avgSpeed = calculateAverageSpeedKmh(segmentPoints);
        for (TransportModeConfig transportModeConfig : configs) {
            if (transportModeConfig.maxKmh() == null || transportModeConfig.maxKmh() > avgSpeed) {
                log.trace("classifySegment: avgSpeed={} km/h → matched {} (maxKmh={})", String.format("%.1f", avgSpeed), transportModeConfig.mode(), transportModeConfig.maxKmh());
                return transportModeConfig.mode();
            }
        }
        log.trace("classifySegment: avgSpeed={} km/h → no config matched, returning UNKNOWN", String.format("%.1f", avgSpeed));
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
        int mergeCount = 0;
        List<TransportModeSegment> result = new ArrayList<>();
        TransportModeSegment current = segments.getFirst();
        for (int i = 1; i < segments.size(); i++) {
            TransportModeSegment next = segments.get(i);
            if (current.mode() == next.mode()) {
                log.trace("mergeSameModeSegments: merging {}@+{}s+{}s with {}@+{}s+{}s",
                        current.mode(), current.offsetSeconds(), current.durationSeconds(),
                        next.mode(), next.offsetSeconds(), next.durationSeconds());
                current = new TransportModeSegment(
                        current.mode(),
                        current.offsetSeconds(),
                        current.durationSeconds() + next.durationSeconds(),
                        current.distanceMeters() + next.distanceMeters()
                );
                mergeCount++;
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        if (mergeCount > 0) {
            log.trace("mergeSameModeSegments: merged {} adjacent segment(s) down to {} segments", mergeCount, result.size());
        }
        return result;
    }

    private List<TransportModeSegment> collapseShortSegments(List<TransportModeSegment> segments) {
        if (segments.size() < 3) {
            return segments;
        }

        int totalCollapsed = 0;
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
                    log.trace("collapseShortSegments: collapsing {}@+{}s+{}s ({}s < {}s) between {} and {}",
                            current.mode(), current.offsetSeconds(), current.durationSeconds(),
                            current.durationSeconds(), MIN_SEGMENT_DURATION_SECONDS, prev.mode(), nextSeg.mode());
                    totalCollapsed++;
                    changed = true;
                } else {
                    next.add(current);
                }
            }
            result = next;
        } while (changed);

        if (totalCollapsed > 0) {
            log.trace("collapseShortSegments: collapsed {} short segment(s) (MIN_SEGMENT_DURATION_SECONDS={}s)", totalCollapsed, MIN_SEGMENT_DURATION_SECONDS);
        }
        return result;
    }

    /**
     * Temporary holder for a chunk's classification result.
     */
    private record ChunkClass(TransportMode mode, long durationSeconds, double distanceMeters) {
    }
}
