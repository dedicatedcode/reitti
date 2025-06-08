package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.GeoUtils;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StayPointDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(StayPointDetectionService.class);

    // Parameters for stay point detection
    private final double distanceThreshold; // meters
    private final long timeThreshold; // seconds
    private final int minPointsInCluster; // Minimum points to form a valid cluster

    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public StayPointDetectionService(
            RawLocationPointRepository rawLocationPointRepository,
            @Value("${reitti.staypoint.distance-threshold-meters:50}") double distanceThreshold,
            @Value("${reitti.staypoint.time-threshold-seconds:1200}") long timeThreshold,
            @Value("${reitti.staypoint.min-points:5}") int minPointsInCluster) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.distanceThreshold = distanceThreshold;
        this.timeThreshold = timeThreshold;
        this.minPointsInCluster = minPointsInCluster;

        logger.info("StayPointDetectionService initialized with: distanceThreshold={}m, timeThreshold={}s, minPointsInCluster={}",
                distanceThreshold, timeThreshold, minPointsInCluster);
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
            // Get points from 1 day before the earliest new point
            Instant windowStart = earliestNewPoint.get().minus(Duration.ofDays(1));
            // Get points from 1 day after the latest new point
            Instant windowEnd = latestNewPoint.get().plus(Duration.ofDays(1));

            List<RawLocationPointRepository.ClusteredPoint> clusteredPointsInTimeRangeForUser = this.rawLocationPointRepository.findClusteredPointsInTimeRangeForUser(user, windowStart, windowEnd, minPointsInCluster, GeoUtils.metersToDegreesAtPosition(distanceThreshold, newPoints.stream().findFirst().get().getLatitude())[1]);
            Map<Integer, List<RawLocationPoint>> clusteredByLocation = new HashMap<>();
            for (RawLocationPointRepository.ClusteredPoint clusteredPoint : clusteredPointsInTimeRangeForUser) {
                if (clusteredPoint.getClusterId() != null) {
                    clusteredByLocation.computeIfAbsent(clusteredPoint.getClusterId(), k -> new ArrayList<>()).add(clusteredPoint.getPoint());
                }
            }

            logger.info("Found {} point clusters in the processing window", clusteredByLocation.size());

            // Apply the stay point detection algorithm
            List<StayPoint> stayPoints = detectStayPointsFromTrajectory(clusteredByLocation);

            logger.info("Detected {} stay points", stayPoints.size());

            return stayPoints;
        }
        return Collections.emptyList();
    }

    private List<StayPoint> detectStayPointsFromTrajectory(Map<Integer, List<RawLocationPoint>> points) {
        logger.info("Starting cluster-based stay point detection with {} different spatial clusters.", points.size());

        List<List<RawLocationPoint>> clusters = new ArrayList<>();

        //split them up when time is x seconds between
        for (List<RawLocationPoint> clusteredByLocation : points.values()) {
            logger.debug("Start splitting up geospatial cluster with [{}] elements based on minimum time [{}]s between points", clusteredByLocation.size(), timeThreshold);
            //first sort them by timestamp
            clusteredByLocation.sort(Comparator.comparing(RawLocationPoint::getTimestamp));

            List<RawLocationPoint> currentTimedCluster = new ArrayList<>();
            clusters.add(currentTimedCluster);
            currentTimedCluster.add(clusteredByLocation.getFirst());

            Instant currentTime = clusteredByLocation.getFirst().getTimestamp();

            for (int i = 1; i < clusteredByLocation.size(); i++) {
                RawLocationPoint next = clusteredByLocation.get(i);
                if (Duration.between(currentTime, next.getTimestamp()).getSeconds() < timeThreshold) {
                    currentTimedCluster.add(next);
                } else {
                    currentTimedCluster = new ArrayList<>();
                    currentTimedCluster.add(next);
                    clusters.add(currentTimedCluster);
                }
                currentTime = next.getTimestamp();
            }
        }

        logger.info("Detected {} stay points after splitting them up.", clusters.size());
        //filter them by duration
        List<List<RawLocationPoint>> filteredByMinimumDuration = clusters.stream()
                .filter(c -> Duration.between(c.getFirst().getTimestamp(), c.getLast().getTimestamp()).toSeconds() > timeThreshold)
                .toList();

        logger.info("Found {} valid clusters after duration filtering", filteredByMinimumDuration.size());

        // Step 3: Convert valid clusters to stay points
        return filteredByMinimumDuration.stream()
                .map(this::createStayPoint)
                .collect(Collectors.toList());
    }

    private StayPoint createStayPoint(List<RawLocationPoint> clusterPoints) {
        GeoPoint result = weightedCenter(clusterPoints);

        // Get the time range
        Instant arrivalTime = clusterPoints.getFirst().getTimestamp();
        Instant departureTime = clusterPoints.getLast().getTimestamp();

        return new StayPoint(result.latitude(), result.longitude(), arrivalTime, departureTime, clusterPoints);
    }

    private static GeoPoint weightedCenter(List<RawLocationPoint> clusterPoints) {
        // Calculate the centroid of the cluster using weighted average based on accuracy
        // Points with better accuracy (lower meters value) get higher weight
        double weightSum = 0;
        double weightedLatSum = 0;
        double weightedLngSum = 0;

        for (RawLocationPoint point : clusterPoints) {
            // Use inverse of accuracy as weight (higher accuracy = higher weight)
            double weight = point.getAccuracyMeters() != null && point.getAccuracyMeters() > 0
                ? 1.0 / point.getAccuracyMeters()
                : 1.0; // default weight if accuracy is null or zero

            weightSum += weight;
            weightedLatSum += point.getLatitude() * weight;
            weightedLngSum += point.getLongitude() * weight;
        }

        double latCentroid = weightedLatSum / weightSum;
        double lngCentroid = weightedLngSum / weightSum;
        return new GeoPoint(latCentroid, lngCentroid);
    }

}
