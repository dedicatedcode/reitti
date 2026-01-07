package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        double maxSpeedMps = config.getMaxSpeedKmh() / 3.6;

        for (int i = 1; i < points.size() - 1; i++) {
            RawLocationPoint prev = points.get(i - 1);
            RawLocationPoint current = points.get(i);
            RawLocationPoint next = points.get(i + 1);

            double speedToPrev = calculateSpeed(prev, current);
            double speedToNext = calculateSpeed(current, next);

            // If BOTH speeds are excessive, the current point is likely anomalous
            if (speedToPrev > maxSpeedMps && speedToNext > maxSpeedMps) {
                anomalies.add(current.getId());
            }
        }

        // Handle edge cases - first and last points
        if (points.size() >= 2) {
            // Check first point
            double firstSpeed = calculateSpeed(points.get(0), points.get(1));
            if (firstSpeed > maxSpeedMps && points.size() > 2) {
                double secondSpeed = calculateSpeed(points.get(1), points.get(2));
                if (secondSpeed <= maxSpeedMps) { // Normal speed after first point
                    anomalies.add(points.get(0).getId());
                }
            }

            // Check last point
            int lastIdx = points.size() - 1;
            double lastSpeed = calculateSpeed(points.get(lastIdx - 1), points.get(lastIdx));
            if (lastSpeed > maxSpeedMps && points.size() > 2) {
                double secondLastSpeed = calculateSpeed(points.get(lastIdx - 2), points.get(lastIdx - 1));
                if (secondLastSpeed <= maxSpeedMps) { // Normal speed before last point
                    anomalies.add(points.get(lastIdx).getId());
                }
            }
        }

        logger.debug("Filtered {} points due to excessive speed", anomalies.size());
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
}
