package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransportModeService {

    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService,
                               TransportModeOverrideJdbcService transportModeOverrideJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.transportModeOverrideJdbcService = transportModeOverrideJdbcService;
    }

    public TransportMode inferTransportMode(User user, List<RawLocationPoint> tripPoints, Instant startTime, Instant endTime) {

        Optional<TransportMode> override = this.transportModeOverrideJdbcService.getTransportModeOverride(user, startTime, endTime);
        if (override.isPresent()) {
            return override.get();
        }
        List<TransportModeConfig> config = transportModeJdbcService.getTransportModeConfigs(user);
        return segmentAndClassifyTrip(tripPoints, config);
    }

    public void overrideTransportMode(User user, TransportMode transportMode, Trip trip) {
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, trip.getStartTime(), trip.getEndTime());
    }

    public void overrideTransportMode(User user, TransportMode transportMode, Instant startTime, Instant endTime) {
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, startTime, endTime);
    }

    public Optional<TransportMode> getTransportModeOverride(User user, Instant startTime, Instant endTime) {
        return transportModeOverrideJdbcService.getTransportModeOverride(user, startTime, endTime);
    }

    public void reclassifyAllTripsForUser(User user) {
        // This method would need access to trip repository and raw location points
        // For now, this is a placeholder that would need to be implemented
        // based on your existing trip storage and processing infrastructure
        
        // Typical implementation would:
        // 1. Get all trips for the user
        // 2. For each trip, get the raw location points
        // 3. Re-run the classification with current transport mode configs
        // 4. Update the trip's transport mode in the database
        
        // Example structure:
        // List<Trip> userTrips = tripRepository.findByUser(user);
        // List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        // 
        // for (Trip trip : userTrips) {
        //     List<RawLocationPoint> points = rawLocationPointRepository.findByTripId(trip.getId());
        //     TransportMode newMode = segmentAndClassifyTrip(points, configs);
        //     tripRepository.updateTransportMode(trip.getId(), newMode);
        // }
        
        throw new UnsupportedOperationException("Reclassification not yet implemented - requires trip repository access");
    }

    public TransportMode segmentAndClassifyTrip(List<RawLocationPoint> points, List<TransportModeConfig> configs) {
        List<TripSegment> segments = new ArrayList<>();
        List<Double> speeds = calculateSpeeds(points); // Speeds between points

        List<RawLocationPoint> currentSegmentPoints = new ArrayList<>();
        currentSegmentPoints.add(points.getFirst());

        for (int i = 1; i < points.size(); i++) {
            double prevSpeed = (i > 1) ? speeds.get(i - 2) : 0; // Speed to previous point
            double currSpeed = speeds.get(i - 1); // Speed from current to next

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

        // Calculate duration in minutes for each transport mode and return the one with the most minutes
        Map<TransportMode, Double> modeDurationMinutes = new HashMap<>();
        
        for (TripSegment segment : segments) {
            if (segment.points().size() < 2) continue;
            
            Instant startTime = segment.points().getFirst().getTimestamp();
            Instant endTime = segment.points().getLast().getTimestamp();
            double durationMinutes = Duration.between(startTime, endTime).toMillis() / (1000.0 * 60.0);
            
            modeDurationMinutes.merge(segment.dominantMode(), durationMinutes, Double::sum);
        }
        
        return modeDurationMinutes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(TransportMode.UNKNOWN);
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
