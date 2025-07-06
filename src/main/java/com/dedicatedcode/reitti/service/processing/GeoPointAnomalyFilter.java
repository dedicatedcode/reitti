package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GeoPointAnomalyFilter {
    private static final Logger logger = LoggerFactory.getLogger(GeoPointAnomalyFilter.class);
    private final GeoPointAnomalyFilterConfig config;

    public GeoPointAnomalyFilter(GeoPointAnomalyFilterConfig config) {
        this.config = config;
    }

    /**
     * Main filtering method that removes anomalous geopoints
     */
    public List<LocationDataRequest.LocationPoint> filterAnomalies(List<LocationDataRequest.LocationPoint> points) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        Set<LocationDataRequest.LocationPoint> detectedAnomalies = new HashSet<>();

        // Apply multiple detection methods
        detectedAnomalies.addAll(detectAccuracyAnomalies(points));
        detectedAnomalies.addAll(detectSpeedAnomalies(points));
        detectedAnomalies.addAll(detectDistanceJumpAnomalies(points));
        detectedAnomalies.addAll(detectDirectionAnomalies(points));

        // Filter out anomalies
        return points.stream()
                .filter(point -> !detectedAnomalies.contains(point))
                .collect(Collectors.toList());
    }

    /**
     * Detect points with poor accuracy
     */
    private Set<LocationDataRequest.LocationPoint> detectAccuracyAnomalies(List<LocationDataRequest.LocationPoint> points) {
        Set<LocationDataRequest.LocationPoint> anomalies = new HashSet<>();

        for (LocationDataRequest.LocationPoint point : points) {
            if (point.getAccuracyMeters() > config.maxAccuracyMeters) {
                anomalies.add(point);
            }
        }
        logger.debug("Filtering out [{}] points because min accuracy [{}] not met.", anomalies.size(), config.maxAccuracyMeters);
        return anomalies;
    }

    /**
     * Detect impossible speeds between consecutive points
     */
    private Set<LocationDataRequest.LocationPoint> detectSpeedAnomalies(List<LocationDataRequest.LocationPoint> points) {
        Set<LocationDataRequest.LocationPoint> anomalies = new HashSet<>();

        for (int i = 1; i < points.size(); i++) {
            LocationDataRequest.LocationPoint prev = points.get(i - 1);
            LocationDataRequest.LocationPoint curr = points.get(i);

            if (prev.getTimestamp() != null && curr.getTimestamp() != null) {
                double distance = GeoUtils.distanceInMeters(prev, curr);
                long timeDiffSeconds = java.time.Duration.between(
                        getTimestamp(prev.getTimestamp()), getTimestamp(curr.getTimestamp())).getSeconds();

                if (timeDiffSeconds > 0) {
                    double speedKmh = (distance / 1000.0) / (timeDiffSeconds / 3600.0);
                    double maxSpeed = isEdgePoint(i, points.size()) ?
                            config.maxSpeedKmh * config.edgeToleranceMultiplier : config.maxSpeedKmh;

                    if (speedKmh > maxSpeed) {
                        // Mark the point with worse accuracy as anomaly
                        if (curr.getAccuracyMeters() > prev.getAccuracyMeters()) {
                            anomalies.add(curr);
                        } else {
                            anomalies.add(prev);
                        }
                    }
                }
            }
        }

        logger.debug("Filtering out [{}] points because speed was above [{}].", anomalies.size(), config.maxSpeedKmh);
        return anomalies;
    }

    private Instant getTimestamp(String timestamp) {
        ZonedDateTime parse = ZonedDateTime.parse(timestamp);
        return parse.toInstant();
    }

    /**
     * Detect large distance jumps between consecutive points
     */
    private Set<LocationDataRequest.LocationPoint> detectDistanceJumpAnomalies(List<LocationDataRequest.LocationPoint> points) {
        Set<LocationDataRequest.LocationPoint> anomalies = new HashSet<>();

        for (int i = 1; i < points.size(); i++) {
            LocationDataRequest.LocationPoint prev = points.get(i - 1);
            LocationDataRequest.LocationPoint curr = points.get(i);

            double distance = GeoUtils.distanceInMeters(prev, curr);
            double maxDistance = isEdgePoint(i, points.size()) ?
                    config.maxDistanceJumpMeters * config.edgeToleranceMultiplier :
                    config.maxDistanceJumpMeters;

            if (distance > maxDistance) {
                // For edge points, be more careful about which point to remove
                if (isEdgePoint(i, points.size())) {
                    anomalies.add(selectWorsePoint(prev, curr, points, i));
                } else {
                    // Mark the point with worse accuracy as anomaly
                    if (curr.getAccuracyMeters() > prev.getAccuracyMeters()) {
                        anomalies.add(curr);
                    } else {
                        anomalies.add(prev);
                    }
                }
            }
        }

        logger.debug("Filtering out [{}] points because distance jumped more than [{}] meters.", anomalies.size(), config.maxDistanceJumpMeters);

        return anomalies;
    }
    /**
     * Detect sudden direction changes that might indicate errors
     */
    private Set<LocationDataRequest.LocationPoint> detectDirectionAnomalies(List<LocationDataRequest.LocationPoint> points) {
        Set<LocationDataRequest.LocationPoint> anomalies = new HashSet<>();

        if (points.size() < 3) {
            return anomalies;
        }

        for (int i = 1; i < points.size() - 1; i++) {
            LocationDataRequest.LocationPoint prev = points.get(i - 1);
            LocationDataRequest.LocationPoint curr = points.get(i);
            LocationDataRequest.LocationPoint next = points.get(i + 1);

            // Calculate bearings
            double bearing1 = calculateBearing(prev, curr);
            double bearing2 = calculateBearing(curr, next);

            // Calculate angle difference
            double angleDiff = Math.abs(bearing2 - bearing1);
            if (angleDiff > 180) {
                angleDiff = 360 - angleDiff;
            }

            // If it's a sharp reversal (close to 180 degrees) and the distances are significant
            double dist1 = GeoUtils.distanceInMeters(prev, curr);
            double dist2 = GeoUtils.distanceInMeters(next, curr);

            if (angleDiff > 150 && dist1 > 50 && dist2 > 50) {
                // Check if current point has worse accuracy
                if (curr.getAccuracyMeters() > Math.max(prev.getAccuracyMeters(), next.getAccuracyMeters())) {
                    anomalies.add(curr);
                }
            }
        }
        logger.debug("Filtering out [{}] points because the suddenly changed the direction.", anomalies.size());

        return anomalies;
    }

    /**
     * Handle edge cases specially - first and last points
     */
    private LocationDataRequest.LocationPoint selectWorsePoint(LocationDataRequest.LocationPoint p1, LocationDataRequest.LocationPoint p2, List<LocationDataRequest.LocationPoint> allPoints, int currentIndex) {
        // For edge points, compare against multiple criteria
        double accuracyScore1 = p1.getAccuracyMeters();
        double accuracyScore2 = p2.getAccuracyMeters();

        // If we have enough points, check consistency with neighbors
        if (currentIndex == 1 && allPoints.size() > 2) {
            // First edge: check consistency with second next point
            LocationDataRequest.LocationPoint next = allPoints.get(currentIndex + 1);
            double dist1Next = GeoUtils.distanceInMeters(p1, next);
            double dist2Next = GeoUtils.distanceInMeters(p2, next);

            // Prefer the point that's more consistent with the next point
            if (Math.abs(dist1Next - dist2Next) > 1000) {
                return dist1Next > dist2Next ? p1 : p2;
            }
        }

        if (currentIndex == allPoints.size() - 1 && allPoints.size() > 2) {
            // Last edge: check consistency with second previous point
            LocationDataRequest.LocationPoint prevPrev = allPoints.get(currentIndex - 2);
            double prevPrevDist1 = GeoUtils.distanceInMeters(prevPrev, p1);
            double prevPrevDist2 = GeoUtils.distanceInMeters(prevPrev, p2);

            // Prefer the point that's more consistent with the previous point
            if (Math.abs(prevPrevDist1 - prevPrevDist2) > 1000) {
                return prevPrevDist1 > prevPrevDist2 ? p1 : p2;
            }
        }

        // Fall back to accuracy
        return accuracyScore1 > accuracyScore2 ? p1 : p2;
    }

    private boolean isEdgePoint(int index, int totalSize) {
        return index == 0 || index == totalSize - 1;
    }

    private double calculateBearing(LocationDataRequest.LocationPoint from, LocationDataRequest.LocationPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLng = Math.toRadians(to.getLongitude() - from.getLongitude());

        double y = Math.sin(deltaLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
}
