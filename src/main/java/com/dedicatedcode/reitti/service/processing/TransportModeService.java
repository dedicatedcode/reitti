package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransportModeService {

    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService,
                               TransportModeOverrideJdbcService transportModeOverrideJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.transportModeOverrideJdbcService = transportModeOverrideJdbcService;
    }

    public TransportMode inferTransportMode(User user, List<RawLocationPoint> tripPoints, double distanceMeters, Instant startTime, Instant endTime) {

        Optional<TransportMode> override = this.transportModeOverrideJdbcService.getTransportModeOverride(user, startTime, endTime);
        if (override.isPresent()) {
            return override.get();
        }
        List<TransportModeConfig> config = transportModeJdbcService.getTransportModeConfigs(user);

        // Calculate duration in seconds
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();

        // Avoid division by zero
        if (durationSeconds <= 0) {
            return TransportMode.UNKNOWN;
        }

        // Calculate speed in meters per second
        double speedMps = distanceMeters / durationSeconds;

        // Convert to km/h for easier interpretation
        double speedKmh = speedMps * 3.6;

        for (TransportModeConfig transportModeConfig : config) {
            if (transportModeConfig.maxKmh() == null || transportModeConfig.maxKmh() > speedKmh)
                return transportModeConfig.mode();
        }

        return TransportMode.UNKNOWN;
    }

    public void overrideTransportMode(User user, TransportMode transportMode, Trip trip) {
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, trip.getStartTime(), trip.getEndTime());
    }

    public TransportMode segmentAndClassifyTrip(List<RawLocationPoint> points, List<TransportModeConfig> configs) {
        List<TripSegment> segments = new ArrayList<>();
        List<Double> speeds = calculateSpeeds(points); // Speeds between points

        List<RawLocationPoint> currentSegmentPoints = new ArrayList<>();
        currentSegmentPoints.add(points.getFirst());

        for (int i = 1; i < points.size(); i++) {
            double prevSpeed = (i > 1) ? speeds.get(i - 2) : 0; // Speed to previous point
            double currSpeed = speeds.get(i - 1); // Speed from current to next

            // Simple change-point detection: If speed increases/decreases by 50%+, start new segment
            // Adjust threshold as needed (0.5 = 50%)
            if (prevSpeed > 0 && Math.abs(currSpeed - prevSpeed) / prevSpeed > 0.5) {
                // Classify previous segment and add to list
                TransportMode mode = classifySegment(currentSegmentPoints, configs);
                segments.add(new TripSegment(new ArrayList<>(currentSegmentPoints), mode));
                currentSegmentPoints.clear();
            }
            currentSegmentPoints.add(points.get(i));
        }

        // Add the last segment
        TransportMode mode = classifySegment(currentSegmentPoints, configs);
        segments.add(new TripSegment(currentSegmentPoints, mode));

        // count the number of transportMode from the segments and return the one which occured the mose AI!
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
