package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeoPointAnomalyFilter {
    private static final Logger logger = LoggerFactory.getLogger(GeoPointAnomalyFilter.class);
    private final GeoPointAnomalyFilterConfig config;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    public GeoPointAnomalyFilter(GeoPointAnomalyFilterConfig config, RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.config = config;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    /**
     * Main filtering method that removes anomalous geopoints
     */
    public List<LocationPoint> filterAnomalies(User user, List<LocationPoint> points) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        List<WindowedPoint> allPoints = new ArrayList<>();
        // Add new points (mark as new)
        for (LocationPoint lp : points) {
            allPoints.add(new WindowedPoint(
                    ZonedDateTime.parse(lp.getTimestamp()).toInstant(),
                    lp.getAccuracyMeters(),
                    lp.getLatitude(),
                    lp.getLongitude(),
                    false // isNew
            ));
        }

        allPoints.sort(Comparator.comparing(WindowedPoint::timestamp));

        Instant start = allPoints.getFirst().timestamp().minus(config.getWindowSize(), ChronoUnit.HOURS);
        Instant end = allPoints.getLast().timestamp().plus(config.getWindowSize(), ChronoUnit.HOURS);

        //to speed up calculation, we only use relevant points here.
        List<RawLocationPoint> rawHistory = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, start, end, true, false);

        // Add history (mark as history)
        for (RawLocationPoint rlp : rawHistory) {
            allPoints.add(new WindowedPoint(
                    rlp.getTimestamp(),
                    rlp.getAccuracyMeters(),
                    rlp.getLatitude(),
                    rlp.getLongitude(),
                    true // isHistory
            ));
        }

        // Sort all points by timestamp
        allPoints.sort(Comparator.comparing(WindowedPoint::timestamp));

        Set<WindowedPoint> detectedAnomalies = new HashSet<>();

        // Apply multiple detection methods
        detectedAnomalies.addAll(detectAccuracyAnomalies(allPoints));
        detectedAnomalies.addAll(detectSpeedAnomalies(allPoints));
        detectedAnomalies.addAll(detectDistanceJumpAnomalies(allPoints));
        detectedAnomalies.addAll(detectDirectionAnomalies(allPoints));

        // Filter out anomalies
        // We only return points that were originally in 'newPoints' AND were not flagged as anomalies
        return points.stream()
                .filter(lp -> {
                    Instant ts = ZonedDateTime.parse(lp.getTimestamp()).toInstant();
                    // Check if this specific point (by timestamp and accuracy) was flagged
                    boolean isAnomaly = allPoints.stream()
                            .filter(wp -> !wp.isHistory) // Only look at new points
                            .filter(wp -> wp.timestamp.equals(ts))
                            .anyMatch(detectedAnomalies::contains);
                    return !isAnomaly;
                })
                .collect(Collectors.toList());
    }

    /**
     * Detect points with poor accuracy
     */
    private Set<WindowedPoint> detectAccuracyAnomalies(List<WindowedPoint> points) {
        Set<WindowedPoint> anomalies = new HashSet<>();
        for (WindowedPoint point : points) {
            if (point.accuracy > config.getMaxAccuracyMeters()) {
                anomalies.add(point);
            }
        }
        logger.debug("Filtering out [{}] points because min accuracy [{}] not met.", anomalies.size(), config.getMaxAccuracyMeters());
        return anomalies;
    }

    /**
     * Detect impossible speeds between consecutive points
     */
    private Set<WindowedPoint> detectSpeedAnomalies(List<WindowedPoint> points) {
        Set<WindowedPoint> anomalies = new HashSet<>();
        int windowSize = config.getWindowSize();

        for (int i = 0; i < points.size(); i++) {
            WindowedPoint current = points.get(i);

            int lookBackCount = 0;
            for (int j = i - 1; j >= 0 && lookBackCount < windowSize; j--, lookBackCount++) {
                WindowedPoint prev = points.get(j);

                if (prev.timestamp == null || current.timestamp == null) continue;

                double distance = GeoUtils.distanceInMeters(prev.latitude, prev.longitude, current.latitude, current.longitude);
                long timeDiffSeconds = ChronoUnit.SECONDS.between(prev.timestamp, current.timestamp);

                if (timeDiffSeconds > 0) {
                    double speedKmh = (distance / 1000.0) / (timeDiffSeconds / 3600.0);

                    boolean isEdge = i == points.size() - 1;
                    double maxSpeed = isEdge ? config.getMaxSpeedKmh() * config.getEdgeToleranceMultiplier() : config.getMaxSpeedKmh();

                    if (speedKmh > maxSpeed) {
                        if (current.accuracy > prev.accuracy) { //we prefer higher accuracy
                            anomalies.add(current);
                        } else if (!current.isHistory() && prev.isHistory()) { //we prefer already stored points
                            anomalies.add(current);
                        } else {
                            anomalies.add(prev);
                        }
                    }
                }
            }
        }

        logger.debug("Filtering out [{}] points because speed was above [{}].", anomalies.size(), config.getMaxSpeedKmh());
        return anomalies;
    }

    private Instant getTimestamp(String timestamp) {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(timestamp, Instant::from);
    }

    /**
     * Detect large distance jumps between consecutive points
     */
    private Set<WindowedPoint> detectDistanceJumpAnomalies(List<WindowedPoint> points) {
        Set<WindowedPoint> anomalies = new HashSet<>();
        int windowSize = config.getWindowSize();

        for (int i = 0; i < points.size(); i++) {
            WindowedPoint current = points.get(i);

            int lookBackCount = 0;
            for (int j = i - 1; j >= 0 && lookBackCount < windowSize; j--, lookBackCount++) {
                WindowedPoint prev = points.get(j);

                double distance = GeoUtils.distanceInMeters(prev.latitude, prev.longitude, current.latitude, current.longitude);

                boolean isEdge = (i == 0 || i == points.size() - 1);
                double maxDistance = isEdge ? config.getMaxDistanceJumpMeters() * config.getEdgeToleranceMultiplier() : config.getMaxDistanceJumpMeters();

                if (distance > maxDistance) {
                    if (current.accuracy > prev.accuracy) {
                        anomalies.add(current);
                    } else {
                        anomalies.add(prev);
                    }
                }
            }
        }
        logger.debug("Filtering out [{}] points because distance jumped more than [{}] meters.", anomalies.size(), config.getMaxDistanceJumpMeters());

        return anomalies;
    }
    /**
     * Detect sudden direction changes that might indicate errors
     */
    private Set<WindowedPoint> detectDirectionAnomalies(List<WindowedPoint> points) {
        Set<WindowedPoint> anomalies = new HashSet<>();
        int windowSize = config.getWindowSize();

        for (int i = 0; i < points.size(); i++) {
            WindowedPoint current = points.get(i);

            int lookBackCount = 0;
            for (int j = i - 1; j >= 0 && lookBackCount < windowSize; j--, lookBackCount++) {
                WindowedPoint prev = points.get(j);

                int lookForwardCount = 0;
                for (int k = i + 1; k < points.size() && lookForwardCount < windowSize; k++, lookForwardCount++) {
                    WindowedPoint next = points.get(k);

                    double bearing1 = calculateBearing(prev, current);
                    double bearing2 = calculateBearing(current, next);

                    double angleDiff = Math.abs(bearing2 - bearing1);
                    if (angleDiff > 180) {
                        angleDiff = 360 - angleDiff;
                    }

                    double dist1 = GeoUtils.distanceInMeters(prev.latitude, prev.longitude, current.latitude, current.longitude);
                    double dist2 = GeoUtils.distanceInMeters(current.latitude, current.longitude, next.latitude, next.longitude);

                    if (angleDiff > 150 && dist1 > 50 && dist2 > 50) {
                        if (current.accuracy > Math.max(prev.accuracy, next.accuracy)) {
                            anomalies.add(current);
                        }
                    }
                }
            }
        }
        logger.debug("Filtering out [{}] points because the suddenly changed the direction.", anomalies.size());
        return anomalies;
    }

    /**
     * Handle edge cases specially - first and last points
     */
    private LocationPoint selectWorsePoint(LocationPoint p1, LocationPoint p2, List<LocationPoint> allPoints, int currentIndex) {
        // For edge points, compare against multiple criteria
        double accuracyScore1 = p1.getAccuracyMeters();
        double accuracyScore2 = p2.getAccuracyMeters();

        // If we have enough points, check consistency with neighbors
        if (currentIndex == 1 && allPoints.size() > 2) {
            // First edge: check consistency with second next point
            LocationPoint next = allPoints.get(currentIndex + 1);
            double dist1Next = GeoUtils.distanceInMeters(p1, next);
            double dist2Next = GeoUtils.distanceInMeters(p2, next);

            // Prefer the point that's more consistent with the next point
            if (Math.abs(dist1Next - dist2Next) > 1000) {
                return dist1Next > dist2Next ? p1 : p2;
            }
        }

        if (currentIndex == allPoints.size() - 1 && allPoints.size() > 2) {
            // Last edge: check consistency with second previous point
            LocationPoint prevPrev = allPoints.get(currentIndex - 2);
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

    private double calculateBearing(WindowedPoint from, WindowedPoint to) {
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);
        double deltaLng = Math.toRadians(to.longitude - from.longitude());

        double y = Math.sin(deltaLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }


    private record WindowedPoint(Instant timestamp, double accuracy, double latitude, double longitude,
                                 boolean isHistory) {
    }
}
