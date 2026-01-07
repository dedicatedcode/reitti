package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeoPointAnomalyFilter {
    private static final Logger logger = LoggerFactory.getLogger(GeoPointAnomalyFilter.class);
    private final GeoPointAnomalyFilterConfig config;

    public GeoPointAnomalyFilter(GeoPointAnomalyFilterConfig config) {
        this.config = config;
    }

    public List<RawLocationPoint> detectAnomalies(List<RawLocationPoint> pointsToCheck) {
        if (pointsToCheck == null || pointsToCheck.size() < 2) {
            return new ArrayList<>();
        }

        List<RawLocationPoint> sortedPoints = pointsToCheck.stream()
                .sorted(Comparator.comparing(RawLocationPoint::getTimestamp))
                .collect(Collectors.toList());

        Set<Long> anomalyIds = new HashSet<>();

        // Simple accuracy filter
        anomalyIds.addAll(filterByAccuracy(sortedPoints));

        // Speed-based anomaly detection for all points
        anomalyIds.addAll(filterBySpeed(sortedPoints));

        return pointsToCheck.stream()
                .filter(p -> anomalyIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    private Set<Long> filterByAccuracy(List<RawLocationPoint> points) {
        return points.stream()
                .filter(p -> p.getAccuracyMeters() > config.getMaxAccuracyMeters())
                .map(RawLocationPoint::getId)
                .collect(Collectors.toSet());
    }

    private Set<Long> filterBySpeed(List<RawLocationPoint> points) {
        Set<Long> anomalies = new HashSet<>();

        if (points.size() < 2) return anomalies;

        // Calculate all successive speeds
        List<Double> speeds = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            double speed = calculateSpeed(points.get(i), points.get(i + 1));
            if (speed >= 0) {
                speeds.add(speed);
            }
        }

        if (speeds.isEmpty()) return anomalies;

        // Calculate statistical baseline
        List<Double> sortedSpeeds = new ArrayList<>(speeds);
        Collections.sort(sortedSpeeds);
        double medianSpeed = getMedian(sortedSpeeds);
        double threshold = Math.max(config.getMaxSpeedKmh() / 3.6, medianSpeed * 3);

        // Check first point (only has speed after)
        if (speeds.size() >= 2 && speeds.get(0) > threshold) {
            // If first speed is excessive but second is normal, first point is anomaly
            if (speeds.get(1) <= threshold) {
                anomalies.add(points.getFirst().getId());
            }
        }

        // Check middle points (have speeds before and after)
        for (int i = 1; i < points.size() - 1; i++) {
            double speedBefore = speeds.get(i - 1);
            double speedAfter = speeds.get(i);

            if (speedBefore > threshold && speedAfter > threshold) {
                anomalies.add(points.get(i).getId());
            }
        }

        // Check last point (only has speed before)
        if (speeds.size() >= 2) {
            int lastSpeedIdx = speeds.size() - 1;
            if (speeds.get(lastSpeedIdx) > threshold) {
                // If last speed is excessive but second-to-last is normal, last point is anomaly
                if (speeds.get(lastSpeedIdx - 1) <= threshold) {
                    anomalies.add(points.getLast().getId());
                }
            }
        }

        logger.debug("Filtered {} points due to excessive speed (median: {} m/s, threshold: {} m/s)",
                     anomalies.size(), medianSpeed, threshold);
        return anomalies;
    }

    private double calculateSpeed(RawLocationPoint p1, RawLocationPoint p2) {
        if (p1.getTimestamp() == null || p2.getTimestamp() == null) {
            return -1; // Invalid
        }

        long timeDiffSeconds = Math.abs(ChronoUnit.SECONDS.between(p1.getTimestamp(), p2.getTimestamp()));
        if (timeDiffSeconds == 0) {
            return -1; // Invalid
        }

        double distance = GeoUtils.distanceInMeters(p1, p2);
        return distance / timeDiffSeconds; // m/s
    }

    private double getMedian(List<Double> sortedValues) {
        int size = sortedValues.size();
        if (size % 2 == 0) {
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        } else {
            return sortedValues.get(size / 2);
        }
    }
}
