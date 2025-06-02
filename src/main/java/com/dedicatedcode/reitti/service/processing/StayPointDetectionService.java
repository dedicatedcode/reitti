package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.GeoUtils;
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
            @Value("${reitti.staypoint.distance-threshold:50}") double distanceThreshold,
            @Value("${reitti.staypoint.time-threshold:1200}") long timeThreshold,
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
            // Get points from 1 hour before the earliest new point
            Instant windowStart = earliestNewPoint.get();
            // Get points from 1 hour after the latest new point
            Instant windowEnd = latestNewPoint.get();

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
        if (points.size() < minPointsInCluster) {
            return Collections.emptyList();
        }

        logger.info("Starting cluster-based stay point detection with {} points", points.size());
        
        // Step 1: Create clusters based on spatial proximity
        List<List<RawLocationPoint>> clusters = createSpatialClusters(points);
        logger.info("Created {} initial spatial clusters", clusters.size());
        
        // Step 2: Filter clusters based on time threshold

        List<List<RawLocationPoint>> validClusters = filterClustersByTimeThreshold(clusters);
        logger.info("Found {} valid clusters after time threshold filtering", validClusters.size());
        
        // Step 3: Convert valid clusters to stay points
        return validClusters.stream()
                .map(this::createStayPoint)
                .collect(Collectors.toList());
    }
    
    private List<List<RawLocationPoint>> createSpatialClusters(List<RawLocationPoint> points) {
        List<List<RawLocationPoint>> clusters = new ArrayList<>();
        Set<RawLocationPoint> processedPoints = new HashSet<>();
        
        for (RawLocationPoint point : points) {
            if (processedPoints.contains(point)) {
                continue;
            }
            
            // Start a new cluster with this point
            List<RawLocationPoint> cluster = new ArrayList<>();
            cluster.add(point);
            processedPoints.add(point);
            
            // Find all points within DISTANCE_THRESHOLD of this point
            for (RawLocationPoint otherPoint : points) {
                if (processedPoints.contains(otherPoint)) {
                    continue;
                }
                
                double distance = GeoUtils.distanceInMeters(point, otherPoint);
                
                if (distance <= distanceThreshold) {
                    cluster.add(otherPoint);
                    processedPoints.add(otherPoint);
                }
            }
            
            // Only add clusters with enough points
            if (cluster.size() >= minPointsInCluster) {
                // Sort the cluster by timestamp
                cluster.sort(Comparator.comparing(RawLocationPoint::getTimestamp));
                clusters.add(cluster);
            }
        }
        
        return clusters;
    }
    
    private List<List<RawLocationPoint>> filterClustersByTimeThreshold(List<List<RawLocationPoint>> clusters) {
        List<List<RawLocationPoint>> validClusters = new ArrayList<>();
        
        for (List<RawLocationPoint> cluster : clusters) {
            // Calculate the total time span of the cluster
            Instant firstTimestamp = cluster.get(0).getTimestamp();
            Instant lastTimestamp = cluster.get(cluster.size() - 1).getTimestamp();
            
            long timeSpanSeconds = Duration.between(firstTimestamp, lastTimestamp).getSeconds();
            
            if (timeSpanSeconds >= timeThreshold) {
                validClusters.add(cluster);
            }
        }
        
        return validClusters;
    }

    private StayPoint createStayPoint(List<RawLocationPoint> clusterPoints) {
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

        // Get the time range
        Instant arrivalTime = clusterPoints.get(0).getTimestamp();
        Instant departureTime = clusterPoints.get(clusterPoints.size() - 1).getTimestamp();

        return new StayPoint(latCentroid, lngCentroid, arrivalTime, departureTime, clusterPoints);
    }
}
