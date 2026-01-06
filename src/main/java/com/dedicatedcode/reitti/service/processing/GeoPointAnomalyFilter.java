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
        if (pointsToCheck == null || pointsToCheck.isEmpty()) {
            return new ArrayList<>();
        }

        // Combine the points to check with the history context for analysis
        List<WindowedPoint> allPoints = new ArrayList<>();
        for (RawLocationPoint p : pointsToCheck) {
            allPoints.add(new WindowedPoint(p.getTimestamp(), p.getAccuracyMeters(), p.getLatitude(), p.getLongitude(), true, p.getId()));
        }

        allPoints.sort(Comparator.comparing(WindowedPoint::timestamp));

        Set<WindowedPoint> detectedAnomalies = new HashSet<>();
        detectedAnomalies.addAll(detectAccuracyAnomalies(allPoints));
        detectedAnomalies.addAll(detectSpeedAnomalies(allPoints));
        detectedAnomalies.addAll(detectSpeedAnomaliesBackward(allPoints));

        // Filter to only return the points from the original input list that were flagged
        Set<Long> anomalyIds = detectedAnomalies.stream()
                .map(WindowedPoint::dbId)
                .collect(Collectors.toSet());

        return pointsToCheck.stream()
                .filter(p -> anomalyIds.contains(p.getId()))
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

        if (points.size() < 2) {
            return anomalies;
        }

        // We need at least 'windowSize' points before the current one to establish a baseline
        for (int i = windowSize; i < points.size(); i++) {
            WindowedPoint currentPoint = points.get(i);
            WindowedPoint prevPoint = points.get(i - 1);

            if (prevPoint.timestamp == null || currentPoint.timestamp == null) continue;

            long timeDiffLatest = ChronoUnit.SECONDS.between(prevPoint.timestamp, currentPoint.timestamp);
            if (timeDiffLatest <= 0) continue;

            // 1. Calculate the speed of the segment being tested
            double distanceLatest = GeoUtils.distanceInMeters(prevPoint.latitude, prevPoint.longitude, currentPoint.latitude, currentPoint.longitude);
            double speedLatest = distanceLatest / timeDiffLatest; // in m/s

            // 2. Collect speeds from the historical window, but ONLY from segments
            //    where neither point is already in the anomalies set.
            List<Double> historicalSpeeds = new ArrayList<>();
            for (int j = 0; j < windowSize; j++) {
                WindowedPoint p1 = points.get(i - windowSize + j);
                WindowedPoint p2 = points.get(i - windowSize + j + 1);

                if (anomalies.contains(p1) || anomalies.contains(p2)) {
                    continue;
                }

                if (p1.timestamp == null || p2.timestamp == null) continue;

                long timeDiff = ChronoUnit.SECONDS.between(p1.timestamp, p2.timestamp);
                if (timeDiff <= 0) continue;

                double distance = GeoUtils.distanceInMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
                historicalSpeeds.add(distance / timeDiff);
            }

            if (historicalSpeeds.isEmpty()) continue;

            // 3. Find the median of the historical speeds
            Collections.sort(historicalSpeeds);
            double medianSpeed;
            int size = historicalSpeeds.size();
            if (size % 2 == 0) {
                medianSpeed = (historicalSpeeds.get(size / 2 - 1) + historicalSpeeds.get(size / 2)) / 2.0;
            } else {
                medianSpeed = historicalSpeeds.get(size / 2);
            }

            // 4. Compare the latest speed to the median
            double maxSpeedKmh = config.getMaxSpeedKmh();
            double maxSpeedMps = maxSpeedKmh / 3.6;

            if (speedLatest > maxSpeedMps) {
                calculateAndAddCulprit(points, speedLatest, medianSpeed, i, windowSize, prevPoint, maxSpeedMps, currentPoint, anomalies);
            }
        }

        logger.debug("Filtering out [{}] points because speed was above [{}].", anomalies.size(), config.getMaxSpeedKmh());
        return anomalies;
    }

    private Set<WindowedPoint> detectSpeedAnomaliesBackward(List<WindowedPoint> points) {
        Set<WindowedPoint> anomalies = new HashSet<>();
        int windowSize = config.getWindowSize();

        if (points.size() < 2) {
            return anomalies;
        }

        // Iterate backwards from the point that has 'windowSize' points after it
        for (int i = points.size() - 1 - windowSize; i >= 0; i--) {
            WindowedPoint prevPoint = points.get(i);
            WindowedPoint currentPoint = points.get(i + 1);

            if (prevPoint.timestamp == null || currentPoint.timestamp == null) continue;

            long timeDiffLatest = ChronoUnit.SECONDS.between(prevPoint.timestamp, currentPoint.timestamp);
            if (timeDiffLatest <= 0) continue;

            // 1. Calculate the speed of the segment being tested
            double distanceLatest = GeoUtils.distanceInMeters(prevPoint.latitude, prevPoint.longitude, currentPoint.latitude, currentPoint.longitude);
            double speedLatest = distanceLatest / timeDiffLatest; // in m/s

            // 2. Collect speeds from the *future* window (segments *after* the current one)
            List<Double> futureSpeeds = new ArrayList<>();
            for (int j = 0; j < windowSize - 1; j++) {
                WindowedPoint p1 = points.get(i + 1 + j);
                WindowedPoint p2 = points.get(i + 2 + j);

                // Skip if either point is already flagged (from the forward pass or this pass)
                if (anomalies.contains(p1) || anomalies.contains(p2)) {
                    continue;
                }

                if (p1.timestamp == null || p2.timestamp == null) continue;

                long timeDiff = ChronoUnit.SECONDS.between(p1.timestamp, p2.timestamp);
                if (timeDiff <= 0) continue;

                double distance = GeoUtils.distanceInMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
                futureSpeeds.add(distance / timeDiff);
            }

            if (futureSpeeds.isEmpty()) continue;

            // 3. Find the median of the future speeds
            Collections.sort(futureSpeeds);
            double medianSpeed;
            int size = futureSpeeds.size();
            if (size % 2 == 0) {
                medianSpeed = (futureSpeeds.get(size / 2 - 1) + futureSpeeds.get(size / 2)) / 2.0;
            } else {
                medianSpeed = futureSpeeds.get(size / 2);
            }

            // 4. Compare the latest speed to the median
            double maxSpeedKmh = config.getMaxSpeedKmh();
            double maxSpeedMps = maxSpeedKmh / 3.6;

            if (speedLatest > maxSpeedMps) {
                calculateAndAddCulprit(points, speedLatest, medianSpeed, i, windowSize, prevPoint, maxSpeedMps, currentPoint, anomalies);
            }
        }
        return anomalies;
    }

    private static void calculateAndAddCulprit(List<WindowedPoint> points, double speedLatest, double medianSpeed, int i, int windowSize, WindowedPoint prevPoint, double maxSpeedMps, WindowedPoint currentPoint, Set<WindowedPoint> anomalies) {
        double deviation = Math.abs(speedLatest - medianSpeed);

        if (deviation > 10.0) {
            // We have an anomaly. Now, which point is the culprit?
            WindowedPoint culprit = null; // Default to the previous point

            // Check consistency of P_prev with its previous neighbor
            boolean prevIsInconsistent = false;
            if (i > windowSize) { // Ensure we have a point before P_prev
                WindowedPoint prevPrev = points.get(i - 2);
                if (prevPrev.timestamp != null) {
                    long timeDiffPrev = ChronoUnit.SECONDS.between(prevPrev.timestamp, prevPoint.timestamp);
                    if (timeDiffPrev > 0) {
                        double distPrev = GeoUtils.distanceInMeters(prevPrev.latitude, prevPrev.longitude, prevPoint.latitude, prevPoint.longitude);
                        double speedPrev = distPrev / timeDiffPrev;
                        if (speedPrev > maxSpeedMps) {
                            prevIsInconsistent = true;
                        }
                    }
                }
            }

            // Check consistency of P_current with its next neighbor
            boolean currentIsInconsistent = false;
            if (i < points.size() - 1) { // Ensure we have a point after P_current
                WindowedPoint nextPoint = points.get(i + 1);
                if (nextPoint.timestamp != null) {
                    long timeDiffNext = ChronoUnit.SECONDS.between(currentPoint.timestamp, nextPoint.timestamp);
                    if (timeDiffNext > 0) {
                        double distNext = GeoUtils.distanceInMeters(currentPoint.latitude, currentPoint.longitude, nextPoint.latitude, nextPoint.longitude);
                        double speedNext = distNext / timeDiffNext;
                        if (speedNext > maxSpeedMps) {
                            currentIsInconsistent = true;
                        }
                    }
                }
            }

            // Decide the culprit based on consistency
            if (prevIsInconsistent && !currentIsInconsistent) {
                culprit = prevPoint;
            } else if (!prevIsInconsistent && currentIsInconsistent) {
                culprit = currentPoint;
            } else {
                WindowedPoint higherAccuracyPoint = prevPoint.accuracy > currentPoint.accuracy ? prevPoint : currentPoint;
                if (prevIsInconsistent) {
                    culprit = higherAccuracyPoint;
                } else {
                    culprit = higherAccuracyPoint;
                }
            }
            anomalies.add(culprit);
        }
    }

    /**
     * Handle edge cases specially - first and last points
     */
    private WindowedPoint selectWorsePoint(WindowedPoint p1, WindowedPoint p2, List<WindowedPoint> allPoints, int currentIndex) {
        // We need at least one point before the current one for comparison
        if (currentIndex < 1) {
            // Not enough history, fall back to accuracy
            return p1.accuracy() > p2.accuracy() ? p1 : p2;
        }

        // Get the point immediately before the candidates
        WindowedPoint prevPoint = allPoints.get(currentIndex - 1);

        // Calculate the distance "jump" for each candidate from the previous point
        double dist1 = GeoUtils.distanceInMeters(prevPoint.latitude, prevPoint.longitude, p1.latitude, p1.longitude);
        double dist2 = GeoUtils.distanceInMeters(prevPoint.latitude, prevPoint.longitude, p2.latitude, p2.longitude);

        // Calculate the time difference for each candidate
        long timeDiff1 = ChronoUnit.SECONDS.between(prevPoint.timestamp, p1.timestamp);
        long timeDiff2 = ChronoUnit.SECONDS.between(prevPoint.timestamp, p2.timestamp);

        // If timestamps are invalid or zero, fall back to accuracy
        if (timeDiff1 <= 0 || timeDiff2 <= 0) {
            return p1.accuracy() > p2.accuracy() ? p1 : p2;
        }

        // Calculate the speed for each candidate
        double speed1 = dist1 / timeDiff1;
        double speed2 = dist2 / timeDiff2;

        // The point with the higher speed (and therefore a larger, more anomalous jump) is the worse one
        if (Math.abs(speed1 - speed2) > 10) { // A 10 m/s (~36 km/h) difference is significant
            return speed1 > speed2 ? p1 : p2;
        }

        // If speeds are very similar, fall back to accuracy
        return p1.accuracy() > p2.accuracy() ? p1 : p2;
    }

    private record WindowedPoint(Instant timestamp, double accuracy, double latitude, double longitude,
                                 boolean isHistory, Long dbId) {
    }
}
