package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointStream;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService.TransportModeOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransportModeService {
    private static final Logger log = LoggerFactory.getLogger(TransportModeService.class);
    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    private static final long CHUNK_DURATION_SECONDS = 60;

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
        return segmentTrip(user, points.iterator(), points.size(), points.getFirst().getTimestamp(), points.getLast().getTimestamp(),
                tripStart, tripEnd, configs, totalDistanceMeters);
    }

    public List<TransportModeSegment> segmentTrip(User user, RawLocationPointStream points, Instant tripStart, Instant tripEnd) {
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        double totalDistanceMeters = GeoUtils.calculateTripDistance(points.iterator());
        return segmentTrip(user, points.iterator(), points.getCount(), points.getFirstTimestamp(), points.getLastTimestamp(),
                tripStart, tripEnd, configs, totalDistanceMeters);
    }

    private List<TransportModeSegment> segmentTrip(User user, Iterator<RawLocationPoint> pointIterator, long pointCount,
                                                   Instant firstPointTimestamp, Instant lastPointTimestamp,
                                                   Instant tripStart, Instant tripEnd, List<TransportModeConfig> configs, double totalDistanceMeters) {
        if (pointCount < 2) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            TransportMode fallbackMode = slowestConfiguredMode(configs);
            log.debug("segmentTrip: only {} point(s), returning single {} segment, duration={}s", pointCount, fallbackMode, duration);
            return List.of(new TransportModeSegment(fallbackMode, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        // Load all overrides for the trip's time range upfront
        List<TransportModeOverride> overrides = this.transportModeOverrideJdbcService.getTransportModeOverrides(user, tripStart, tripEnd);
        if (!overrides.isEmpty()) {
            log.trace("segmentTrip: loaded {} override(s) for trip [{}..{}]", overrides.size(), tripStart, tripEnd);
        }
        List<ChunkClass> chunks = chunkAndClassify(pointIterator, firstPointTimestamp, lastPointTimestamp, pointCount, configs);
        log.trace("segmentTrip: {} points, {} chunks, trip [{}-{}]", pointCount, chunks.size(), tripStart, tripEnd);

        List<TransportModeSegment> result = new ArrayList<>();
        long chunkStartOffset = 0;

        for (ChunkClass chunk : chunks) {
            long chunkDuration = Math.max(1, chunk.durationSeconds());
            result.add(new TransportModeSegment(chunk.mode(), chunkStartOffset, chunkDuration, chunk.distanceMeters()));
            chunkStartOffset += chunkDuration;
        }

        log.trace("segmentTrip: before post-processing: {} segments: {}", result.size(), segmentSummary(result));
        result = absorbImplausibleRuns(result, configs);
        log.trace("segmentTrip: after flanking absorption: {} segments: {}", result.size(), segmentSummary(result));

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
        long firstPointOffset = Duration.between(tripStart, firstPointTimestamp).getSeconds();
        if (firstPointOffset > 0) {
            result = result.stream()
                    .map(s -> new TransportModeSegment(s.mode(), s.offsetSeconds() + firstPointOffset, s.durationSeconds(), s.distanceMeters()))
                    .toList();
            log.trace("segmentTrip: shifted {} segment(s) by +{}s (first point is {}s after tripStart)",
                    result.size(), firstPointOffset, firstPointOffset);
        }

        if (result.isEmpty()) {
            long duration = Duration.between(tripStart, tripEnd).getSeconds();
            TransportMode fallbackMode = slowestConfiguredMode(configs);
            log.trace("segmentTrip: no segments after post-processing, returning single {} segment", fallbackMode);
            result = List.of(new TransportModeSegment(fallbackMode, 0L, Math.max(1, duration), totalDistanceMeters));
        }

        result = applyOverrides(result, tripStart, overrides);
        return result;
    }

    private List<TransportModeSegment> applyOverrides(List<TransportModeSegment> segments, Instant tripStart,
                                                      List<TransportModeOverride> overrides) {
        if (overrides.isEmpty()) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>();
        for (TransportModeSegment segment : segments) {
            Instant segmentStart = tripStart.plusSeconds(segment.offsetSeconds());
            Instant segmentEnd = segmentStart.plusSeconds(segment.durationSeconds());

            TransportMode overriddenMode = null;
            for (TransportModeOverride override : overrides) {
                if (!override.time().isBefore(segmentStart) && override.time().isBefore(segmentEnd)) {
                    overriddenMode = override.mode();
                }
            }
            if (overriddenMode != null) {
                log.trace("applyOverrides: segment at +{}s+{}s overridden {} → {}",
                        segment.offsetSeconds(), segment.durationSeconds(), segment.mode(), overriddenMode);
                result.add(new TransportModeSegment(overriddenMode, segment.offsetSeconds(),
                        segment.durationSeconds(), segment.distanceMeters()));
            } else {
                result.add(segment);
            }
        }
        return mergeSameModeSegments(result);
    }

    private TransportMode slowestConfiguredMode(List<TransportModeConfig> configs) {
        return configs.stream()
                .filter(config -> config.maxKmh() != null)
                .min(Comparator.comparing(TransportModeConfig::maxKmh))
                .map(TransportModeConfig::mode)
                .orElse(TransportMode.WALKING);
    }

    private String segmentSummary(List<TransportModeSegment> segments) {
        return segments.stream()
                .map(s -> s.mode() + "@" + s.offsetSeconds() + "+" + s.durationSeconds() + "s")
                .collect(Collectors.joining(", "));
    }

    private List<ChunkClass> chunkAndClassify(Iterator<RawLocationPoint> pointIterator, Instant firstPointTimestamp,
                                              Instant lastPointTimestamp, long pointCount, List<TransportModeConfig> configs) {
        List<ChunkClass> chunks = new ArrayList<>();
        long totalDuration = Duration.between(firstPointTimestamp, lastPointTimestamp).getSeconds();

        int chunkCount = 0;
        Long pendingStartOffset = null;
        long pendingEndOffset = 0;
        List<RawLocationPoint> pendingPoints = new ArrayList<>();
        RawLocationPoint carry = null;
        RawLocationPoint spanLead = null;
        RawLocationPoint nextPoint = pointIterator.hasNext() ? pointIterator.next() : null;
        for (long offset = 0; offset < totalDuration && nextPoint != null; offset += CHUNK_DURATION_SECONDS) {
            long chunkEnd = Math.min(offset + CHUNK_DURATION_SECONDS, totalDuration);
            Instant chunkEndTime = firstPointTimestamp.plusSeconds(chunkEnd);

            List<RawLocationPoint> chunkPoints = new ArrayList<>();
            while (nextPoint != null && !nextPoint.getTimestamp().isAfter(chunkEndTime)) {
                chunkPoints.add(nextPoint);
                nextPoint = pointIterator.hasNext() ? pointIterator.next() : null;
            }

            if (chunkPoints.size() < 2) {
                log.trace("chunkAndClassify: chunk {} at +{}s sparse ({} point(s)), deferring classification", chunkCount, offset, chunkPoints.size());
                if (pendingStartOffset == null) {
                    pendingStartOffset = offset;
                    spanLead = carry;
                }
                pendingEndOffset = chunkEnd;
                pendingPoints.addAll(chunkPoints);
                if (!chunkPoints.isEmpty()) {
                    carry = chunkPoints.getLast();
                }
                chunkCount++;
                continue;
            }

            carry = flushSparseSpan(pendingStartOffset, pendingEndOffset, pendingPoints, spanLead, carry, configs, chunks);
            pendingStartOffset = null;
            spanLead = null;
            pendingPoints.clear();

            List<RawLocationPoint> measured = withLead(carry, chunkPoints);
            carry = chunkPoints.getLast();

            TransportMode mode = classifySegment(measured, configs);
            double distanceMeters = GeoUtils.calculateTripDistance(measured);
            double avgSpeed = calculateAverageSpeedKmh(measured);
            log.trace("chunkAndClassify: chunk {} at +{}s: {} pts, avgSpeed={} km/h, distance={}m → {}", chunkCount, offset, measured.size(), String.format("%.1f", avgSpeed), String.format("%.0f", distanceMeters), mode);
            chunks.add(new ChunkClass(mode, chunkEnd - offset, distanceMeters));
            chunkCount++;
        }

        flushSparseSpan(pendingStartOffset, pendingEndOffset, pendingPoints, spanLead, carry, configs, chunks);

        log.trace("chunkAndClassify: {} chunks from {} points over {}s", chunks.size(), pointCount, totalDuration);
        return chunks;
    }

    private List<RawLocationPoint> withLead(RawLocationPoint lead, List<RawLocationPoint> points) {
        if (lead == null) {
            return points;
        }
        List<RawLocationPoint> withLead = new ArrayList<>(points.size() + 1);
        withLead.add(lead);
        withLead.addAll(points);
        return withLead;
    }

    private RawLocationPoint flushSparseSpan(Long startOffset, long endOffset, List<RawLocationPoint> spanPoints,
                                            RawLocationPoint lead, RawLocationPoint carry,
                                            List<TransportModeConfig> configs, List<ChunkClass> chunks) {
        if (startOffset == null) {
            return carry;
        }
        if (spanPoints.isEmpty()) {
            return carry;
        }
        RawLocationPoint outgoing = spanPoints.getLast();
        if (spanPoints.size() < 2) {
            log.trace("flushSparseSpan: sparse span at +{}s..+{}s dropped ({} point(s)), covered by neighboring segments", startOffset, endOffset, spanPoints.size());
            return outgoing;
        }
        List<RawLocationPoint> measured = withLead(lead, spanPoints);
        TransportMode mode = classifySegment(measured, configs);
        double distanceMeters = GeoUtils.calculateTripDistance(measured);
        double avgSpeed = calculateAverageSpeedKmh(measured);
        log.trace("flushSparseSpan: sparse span at +{}s..+{}s: {} pts, avgSpeed={} km/h, distance={}m → {}", startOffset, endOffset, measured.size(), String.format("%.1f", avgSpeed), String.format("%.0f", distanceMeters), mode);
        chunks.add(new ChunkClass(mode, endOffset - startOffset, distanceMeters));
        return outgoing;
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

    private int band(TransportMode mode, List<TransportModeConfig> configs) {
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).mode() == mode) {
                return i;
            }
        }
        return configs.size();
    }

    private List<TransportModeSegment> absorbImplausibleRuns(List<TransportModeSegment> segments,
                                                            List<TransportModeConfig> configs) {
        List<TransportModeSegment> result = mergeSameModeSegments(segments);
        for (int i = indexOfFirstImplausibleRun(result, configs); i > 0; i = indexOfFirstImplausibleRun(result, configs)) {
            TransportModeSegment left = result.get(i - 1);
            TransportModeSegment absorbed = result.get(i);
            TransportModeSegment right = result.get(i + 1);
            log.trace("absorbImplausibleRuns: absorbing {}@+{}s+{}s into the preceding {} run, flanked by {} and {}",
                    absorbed.mode(), absorbed.offsetSeconds(), absorbed.durationSeconds(),
                    left.mode(), left.mode(), right.mode());

            List<TransportModeSegment> next = new ArrayList<>(result.size() - 1);
            next.addAll(result.subList(0, i - 1));
            next.add(new TransportModeSegment(
                    left.mode(),
                    left.offsetSeconds(),
                    left.durationSeconds() + absorbed.durationSeconds(),
                    left.distanceMeters() + absorbed.distanceMeters()));
            next.addAll(result.subList(i + 1, result.size()));
            result = mergeSameModeSegments(next);
        }
        return result;
    }

    private int indexOfFirstImplausibleRun(List<TransportModeSegment> segments, List<TransportModeConfig> configs) {
        for (int i = 1; i < segments.size() - 1; i++) {
            int band = band(segments.get(i).mode(), configs);
            boolean slowerThanBoth = band < band(segments.get(i - 1).mode(), configs)
                    && band < band(segments.get(i + 1).mode(), configs);
            boolean fasterThanBothButShort = band > band(segments.get(i - 1).mode(), configs)
                    && band > band(segments.get(i + 1).mode(), configs)
                    && segments.get(i).durationSeconds() <= CHUNK_DURATION_SECONDS;
            if (slowerThanBoth || fasterThanBothButShort) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Temporary holder for a chunk's classification result.
     */
    private record ChunkClass(TransportMode mode, long durationSeconds, double distanceMeters) {
    }
}
