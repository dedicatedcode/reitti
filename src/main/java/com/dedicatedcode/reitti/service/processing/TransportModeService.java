package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransportModeService {
    private static final Logger log = LoggerFactory.getLogger(TransportModeService.class);
    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

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
        return segmentTrip(user, points, tripStart, tripEnd, configs);
    }

    /**
     * Splits a trip into per-mode segments, applying any user overrides for each
     * segment's time range. Returns segments with offset/duration/distance relative
     * to the trip start.
     */
    public List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd, List<TransportModeConfig> configs) {
        if (points.size() < 2) {
            return List.of();
        }

        List<TripSegment> rawSegments = segmentAndClassifyTrip(points, configs);
        List<TransportModeSegment> result = new ArrayList<>();

        for (TripSegment segment : rawSegments) {
            if (segment.points().size() < 2) {
                continue;
            }
            Instant segmentStart = segment.points().getFirst().getTimestamp();
            Instant segmentEnd = segment.points().getLast().getTimestamp();
            if (segmentEnd.isBefore(segmentStart) || segmentEnd.equals(segmentStart)) {
                continue;
            }

            TransportMode mode = segment.dominantMode();
            Optional<TransportMode> override = this.transportModeOverrideJdbcService.getTransportModeOverride(user, segmentStart, segmentEnd);
            if (override.isPresent()) {
                mode = override.get();
            }

            long offsetSeconds = Duration.between(tripStart, segmentStart).getSeconds();
            long durationSeconds = Duration.between(segmentStart, segmentEnd).getSeconds();
            double distanceMeters = GeoUtils.calculateTripDistance(segment.points());

            result.add(new TransportModeSegment(mode, Math.max(0, offsetSeconds), durationSeconds, distanceMeters));
        }

        return result;
    }

    /**
     * Segments points into homogeneous transport-mode segments using speed-based
     * heuristics. Each returned segment carries its points and dominant mode.
     */
    public List<TripSegment> segmentAndClassifyTrip(List<RawLocationPoint> points, List<TransportModeConfig> configs) {
        if (points.isEmpty()) {
            return List.of();
        }

        List<TripSegment> segments = new ArrayList<>();
        List<Double> speeds = calculateSpeeds(points);

        List<RawLocationPoint> currentSegmentPoints = new ArrayList<>();
        currentSegmentPoints.add(points.getFirst());

        for (int i = 1; i < points.size(); i++) {
            double prevSpeed = (i > 1) ? speeds.get(i - 2) : 0;
            double currSpeed = speeds.get(i - 1);

            if (prevSpeed > 0 && Math.abs(currSpeed - prevSpeed) / prevSpeed > 0.5) {
                TransportMode mode = classifySegment(currentSegmentPoints, configs);
                segments.add(new TripSegment(new ArrayList<>(currentSegmentPoints), mode));
                currentSegmentPoints.clear();
            }
            currentSegmentPoints.add(points.get(i));
        }

        // Add the last segment
        TransportMode mode = classifySegment(currentSegmentPoints, configs);
        segments.add(new TripSegment(currentSegmentPoints, mode));

        return segments;
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

    /**
     * Classifies a segment based on average speed (simple thresholds).
     * Customize thresholds or add more modes/logic as needed.
     */
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

    public record TripSegment(List<RawLocationPoint> points, TransportMode dominantMode) {
    }
}
