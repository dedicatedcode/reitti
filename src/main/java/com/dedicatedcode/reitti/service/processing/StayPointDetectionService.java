package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class StayPointDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(StayPointDetectionService.class);

    // Parameters for stay point detection
    private static final double DISTANCE_THRESHOLD = 50; // meters
    private static final long TIME_THRESHOLD = 20 * 60; // 20 minutes in seconds

    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public StayPointDetectionService(RawLocationPointRepository rawLocationPointRepository) {
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @Transactional(readOnly = true)
    public List<StayPoint> detectStayPoints(User user, List<RawLocationPoint> newPoints) {
        logger.info("Detecting stay points for user {} with {} new points", user.getUsername(), newPoints.size());

        // Get a window of points around the new points to ensure continuity
        Optional<Instant> earliestNewPoint = newPoints.stream()
                .map(RawLocationPoint::getTimestamp)
                .min(Instant::compareTo);

        Optional<Instant> latestNewPoint = newPoints.stream()
                .map(RawLocationPoint::getTimestamp)
                .max(Instant::compareTo);

        if (earliestNewPoint.isPresent() && latestNewPoint.isPresent()) {
            // Get points from 1 hour before the earliest new point
            Instant windowStart = earliestNewPoint.get().minus(Duration.ofHours(1));
            // Get points from 1 hour after the latest new point
            Instant windowEnd = latestNewPoint.get().plus(Duration.ofHours(1));

            List<RawLocationPoint> pointsInWindow = rawLocationPointRepository
                    .findByUserAndTimestampBetweenOrderByTimestampAsc(user, windowStart, windowEnd);

            logger.info("Found {} points in the processing window", pointsInWindow.size());

            // Apply the stay point detection algorithm
            List<StayPoint> stayPoints = detectStayPointsFromTrajectory(pointsInWindow);

            logger.info("Detected {} stay points", stayPoints.size());
            return stayPoints;

        }
        return Collections.emptyList();
    }

    private List<StayPoint> detectStayPointsFromTrajectory(List<RawLocationPoint> points) {
        List<StayPoint> stayPoints = new ArrayList<>();

        if (points.size() < 2) {
            return stayPoints;
        }

        int i = 0;
        while (i < points.size()) {
            int j = i + 1;

            while (j < points.size()) {
                // Calculate distance between points[i] and points[j]
                double distance = calculateDistance(
                        points.get(i).getLatitude(), points.get(i).getLongitude(),
                        points.get(j).getLatitude(), points.get(j).getLongitude());

                if (distance < DISTANCE_THRESHOLD) {
                    // Check if the time spent at this location exceeds the threshold
                    long timeSpent = Duration.between(
                            points.get(i).getTimestamp(),
                            points.get(j - 1).getTimestamp()).getSeconds();

                    if (timeSpent >= TIME_THRESHOLD) {
                        // This is a stay point
                        StayPoint stayPoint = createStayPoint(points.subList(i, j));
                        stayPoints.add(stayPoint);
                    }
                }

                j++;
            }

            // Check if we reached the end of the trajectory
            if (j == points.size()) {
                // Check if the time spent at this location exceeds the threshold
                long timeSpent = Duration.between(
                        points.get(i).getTimestamp(),
                        points.get(j - 1).getTimestamp()).getSeconds();

                if (timeSpent >= TIME_THRESHOLD) {
                    // This is a stay point
                    StayPoint stayPoint = createStayPoint(points.subList(i, j));
                    stayPoints.add(stayPoint);
                }

                break;
            }
        }

        return stayPoints;
    }

    private StayPoint createStayPoint(List<RawLocationPoint> clusterPoints) {
        // Calculate the centroid of the cluster
        double latSum = 0;
        double lngSum = 0;

        for (RawLocationPoint point : clusterPoints) {
            latSum += point.getLatitude();
            lngSum += point.getLongitude();
        }

        double latCentroid = latSum / clusterPoints.size();
        double lngCentroid = lngSum / clusterPoints.size();

        // Get the time range
        Instant arrivalTime = clusterPoints.get(0).getTimestamp();
        Instant departureTime = clusterPoints.get(clusterPoints.size() - 1).getTimestamp();

        return new StayPoint(latCentroid, lngCentroid, arrivalTime, departureTime, clusterPoints);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula to calculate distance between two points on Earth
        final int R = 6371000; // Earth radius in meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
