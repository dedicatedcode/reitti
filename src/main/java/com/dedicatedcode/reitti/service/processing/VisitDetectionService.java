package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
import com.dedicatedcode.reitti.model.ClusteredPoint;
import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class VisitDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(VisitDetectionService.class);

    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final VisitJdbcService visitJdbcService;
    private final PreviewVisitJdbcService previewVisitJdbcService;

    private final RabbitTemplate rabbitTemplate;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private final int minimumAdjacentPoints = 5;

    @Autowired
    public VisitDetectionService(
            RawLocationPointJdbcService rawLocationPointJdbcService,
            PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
            VisitDetectionParametersService visitDetectionParametersService,
            UserJdbcService userJdbcService,
            VisitJdbcService visitJdbcService,
            PreviewVisitJdbcService previewVisitJdbcService,
            RabbitTemplate rabbitTemplate) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.visitDetectionParametersService = visitDetectionParametersService;
        this.userJdbcService = userJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.visitJdbcService = visitJdbcService;
        this.previewVisitJdbcService = previewVisitJdbcService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void detectStayPoints(LocationProcessEvent incoming) {
        String username = incoming.getUsername();
        ReentrantLock userLock = userLocks.computeIfAbsent(username, _ -> new ReentrantLock());
        
        userLock.lock();
        try {
            logger.debug("Detecting stay points for user {} from {} to {}. Mode: {}", username, incoming.getEarliest(), incoming.getLatest(), incoming.getPreviewId() == null ? "live" : "preview");
            User user = userJdbcService.findByUsername(username).orElseThrow();
            // We extend the search window slightly to catch visits spanning midnight
            Instant windowStart = incoming.getEarliest().minus(5, ChronoUnit.MINUTES);
            // Get points from 1 day after the latest new point
            Instant windowEnd = incoming.getLatest().plus(5, ChronoUnit.MINUTES);

            DetectionParameter.VisitDetection detectionParameters;
            if (incoming.getPreviewId() == null) {
                detectionParameters = this.visitDetectionParametersService.getCurrentConfiguration(user, windowStart).getVisitDetection();
            } else {
                detectionParameters = this.visitDetectionParametersService.getCurrentConfiguration(user, incoming.getPreviewId()).getVisitDetection();

            }
            List<Visit> affectedVisits;
            if (incoming.getPreviewId() == null) {
                affectedVisits = this.visitJdbcService.findByUserAndTimeAfterAndStartTimeBefore(user, windowStart, windowEnd);
            } else {
                affectedVisits = this.previewVisitJdbcService.findByUserAndTimeAfterAndStartTimeBefore(user, incoming.getPreviewId(), windowStart, windowEnd);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Found [{}] visits which touch the timerange from [{}] to [{}]", affectedVisits.size(), windowStart, windowEnd);
                affectedVisits.forEach(visit -> logger.debug("Visit [{}] from [{}] to [{}] at [{},{}]", visit.getId(), visit.getStartTime(), visit.getEndTime(), visit.getLongitude(), visit.getLatitude()));

            }
            try {
                if (incoming.getPreviewId() == null) {
                    this.visitJdbcService.delete(affectedVisits);
                } else {
                    this.previewVisitJdbcService.delete(affectedVisits);
                }
                logger.debug("Deleted [{}] visits with ids [{}]", affectedVisits.size(), affectedVisits.stream().map(Visit::getId).map(Object::toString).collect(Collectors.joining()));
            } catch (OptimisticLockException e) {
                logger.error("Optimistic lock exception", e);
                throw new RuntimeException(e);
            }

            if (!affectedVisits.isEmpty()) {
                if (affectedVisits.getFirst().getStartTime().isBefore(windowStart)) {
                    windowStart = affectedVisits.getFirst().getStartTime();
                }

                if (affectedVisits.getLast().getEndTime().isAfter(windowEnd)) {
                    windowEnd = affectedVisits.getLast().getEndTime();
                }
            }
            logger.debug("Searching for points in the timerange from [{}] to [{}]", windowStart, windowEnd);

            double baseLatitude = affectedVisits.isEmpty() ? 50 : affectedVisits.getFirst().getLatitude();
            double metersAsDegrees = GeoUtils.metersToDegreesAtPosition(50.0, baseLatitude);
            List<ClusteredPoint> clusteredPointsInTimeRangeForUser;
            if (incoming.getPreviewId() == null) {
                clusteredPointsInTimeRangeForUser = this.rawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(user, windowStart, windowEnd, minimumAdjacentPoints, metersAsDegrees);
            } else {
                clusteredPointsInTimeRangeForUser = this.previewRawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(user, incoming.getPreviewId(), windowStart, windowEnd, minimumAdjacentPoints, metersAsDegrees);
            }

            Map<Integer, List<RawLocationPoint>> clusteredByLocation = new HashMap<>();
            for (ClusteredPoint clusteredPoint : clusteredPointsInTimeRangeForUser) {
                if (clusteredPoint.getClusterId() != null) {
                    clusteredByLocation.computeIfAbsent(clusteredPoint.getClusterId(), _ -> new ArrayList<>()).add(clusteredPoint.getPoint());
                }
            }

            logger.debug("Found {} point clusters in the processing window from [{}] to [{}]", clusteredByLocation.size(), windowStart, windowEnd);

            // Apply the stay point detection algorithm
            List<StayPoint> stayPoints = detectStayPointsFromTrajectory(clusteredByLocation, detectionParameters);

            logger.info("Detected {} stay points for user {}", stayPoints.size(), user.getUsername());

            List<Visit> createdVisits = new ArrayList<>();

            for (StayPoint stayPoint : stayPoints) {
                    Visit visit = createVisit(stayPoint.getLongitude(), stayPoint.getLatitude(), stayPoint);
                    logger.debug("Creating new visit: {}", visit);
                    createdVisits.add(visit);
            }

            List<Long> createdIds;
            if (incoming.getPreviewId() == null) {
                createdIds = visitJdbcService.bulkInsert(user, createdVisits).stream().map(Visit::getId).toList();
            } else {
                createdIds = previewVisitJdbcService.bulkInsert(user, incoming.getPreviewId(), createdVisits).stream().map(Visit::getId).toList();
            }
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new VisitUpdatedEvent(user.getUsername(), createdIds, incoming.getPreviewId()));
        } finally {
            userLock.unlock();
        }
    }


    private List<StayPoint> detectStayPointsFromTrajectory(Map<Integer, List<RawLocationPoint>> points, DetectionParameter.VisitDetection visitDetectionParameters) {
        logger.debug("Starting cluster-based stay point detection with {} different spatial clusters.", points.size());

        List<List<RawLocationPoint>> clusters = new ArrayList<>();

        //split them up when time is x seconds between
        for (List<RawLocationPoint> clusteredByLocation : points.values()) {
            logger.debug("Start splitting up geospatial cluster with [{}] elements based on minimum time [{}]s between points", clusteredByLocation.size(), visitDetectionParameters.getMinimumStayTimeInSeconds());
            //first sort them by timestamp
            clusteredByLocation.sort(Comparator.comparing(RawLocationPoint::getTimestamp));

            List<RawLocationPoint> currentTimedCluster = new ArrayList<>();
            clusters.add(currentTimedCluster);
            currentTimedCluster.add(clusteredByLocation.getFirst());

            Instant currentTime = clusteredByLocation.getFirst().getTimestamp();

            for (int i = 1; i < clusteredByLocation.size(); i++) {
                RawLocationPoint next = clusteredByLocation.get(i);
                if (Duration.between(currentTime, next.getTimestamp()).getSeconds() < visitDetectionParameters.getMaxMergeTimeBetweenSameStayPoints()) {
                    currentTimedCluster.add(next);
                } else {
                    currentTimedCluster = new ArrayList<>();
                    currentTimedCluster.add(next);
                    clusters.add(currentTimedCluster);
                }
                currentTime = next.getTimestamp();
            }
        }

        logger.debug("Detected {} stay points after splitting them up.", clusters.size());
        //filter them by duration
        List<List<RawLocationPoint>> filteredByMinimumDuration = clusters.stream()
                .filter(c -> Duration.between(c.getFirst().getTimestamp(), c.getLast().getTimestamp()).toSeconds() > visitDetectionParameters.getMinimumStayTimeInSeconds())
                .toList();

        logger.debug("Found {} valid clusters after duration filtering", filteredByMinimumDuration.size());

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

    private GeoPoint weightedCenter(List<RawLocationPoint> clusterPoints) {
        // Find the most likely actual location by identifying the point with highest local density
        // and snapping to the nearest actual measurement point

        long start = System.currentTimeMillis();

        GeoPoint result;
        // For small clusters, use the original algorithm
        if (clusterPoints.size() <= 100) {
            result =  weightedCenterSimple(clusterPoints);
        } else {
            // For large clusters, use spatial partitioning for better performance
            result =  weightedCenterOptimized(clusterPoints);
        }
        logger.debug("Weighted center calculation took {}ms for [{}] number of points", System.currentTimeMillis() - start, clusterPoints.size());
        return result;

    }
    
    private GeoPoint weightedCenterSimple(List<RawLocationPoint> clusterPoints) {
        RawLocationPoint bestPoint = null;
        double maxDensityScore = 0;
        
        // For each point, calculate a density score based on nearby points and accuracy
        for (RawLocationPoint candidate : clusterPoints) {
            double densityScore = 0;
            
            for (RawLocationPoint neighbor : clusterPoints) {
                if (candidate == neighbor) continue;
                
                double distance = GeoUtils.distanceInMeters(candidate, neighbor);
                double accuracy = candidate.getAccuracyMeters() != null && candidate.getAccuracyMeters() > 0 
                    ? candidate.getAccuracyMeters() 
                    : 50.0; // default accuracy if null
                
                // Points within accuracy radius contribute to density
                // Closer points and better accuracy contribute more
                if (distance <= accuracy * 2) {
                    double proximityWeight = Math.max(0, 1.0 - (distance / (accuracy * 2)));
                    double accuracyWeight = 1.0 / accuracy;
                    densityScore += proximityWeight * accuracyWeight;
                }
            }
            
            // Add self-contribution based on accuracy
            densityScore += 1.0 / (candidate.getAccuracyMeters() != null && candidate.getAccuracyMeters() > 0 
                ? candidate.getAccuracyMeters() 
                : 50.0);
            
            if (densityScore > maxDensityScore) {
                maxDensityScore = densityScore;
                bestPoint = candidate;
            }
        }
        
        // Fallback to first point if no best point found
        if (bestPoint == null) {
            bestPoint = clusterPoints.getFirst();
        }

        return new GeoPoint(bestPoint.getLatitude(), bestPoint.getLongitude());
    }
    
    private GeoPoint weightedCenterOptimized(List<RawLocationPoint> clusterPoints) {
        // Sample a subset of points for density calculation to improve performance
        // Use every nth point or random sampling for very large clusters
        int sampleSize = Math.min(200, clusterPoints.size());
        List<RawLocationPoint> samplePoints = new ArrayList<>();
        
        if (clusterPoints.size() <= sampleSize) {
            samplePoints = clusterPoints;
        } else {
            // Take evenly distributed samples
            int step = clusterPoints.size() / sampleSize;
            for (int i = 0; i < clusterPoints.size(); i += step) {
                samplePoints.add(clusterPoints.get(i));
            }
        }
        
        // Use spatial grid approach to avoid distance calculations
        // Create a grid based on the bounding box of all points
        double minLat = clusterPoints.stream().mapToDouble(RawLocationPoint::getLatitude).min().orElse(0);
        double maxLat = clusterPoints.stream().mapToDouble(RawLocationPoint::getLatitude).max().orElse(0);
        double minLon = clusterPoints.stream().mapToDouble(RawLocationPoint::getLongitude).min().orElse(0);
        double maxLon = clusterPoints.stream().mapToDouble(RawLocationPoint::getLongitude).max().orElse(0);
        
        // Grid cell size approximately 10 meters (rough approximation)
        double cellSizeLat = 0.0001; // ~11 meters
        double cellSizeLon = 0.0001; // varies by latitude but roughly 11 meters
        
        // Create grid map for fast neighbor lookup
        Map<String, List<RawLocationPoint>> grid = new HashMap<>();
        for (RawLocationPoint point : clusterPoints) {
            int gridLat = (int) ((point.getLatitude() - minLat) / cellSizeLat);
            int gridLon = (int) ((point.getLongitude() - minLon) / cellSizeLon);
            String gridKey = gridLat + "," + gridLon;
            grid.computeIfAbsent(gridKey, k -> new ArrayList<>()).add(point);
        }
        
        RawLocationPoint bestPoint = null;
        double maxDensityScore = 0;
        
        // Calculate density scores for sample points using grid lookup
        for (RawLocationPoint candidate : samplePoints) {
            double accuracy = candidate.getAccuracyMeters() != null && candidate.getAccuracyMeters() > 0 
                ? candidate.getAccuracyMeters() 
                : 50.0;
            
            // Calculate grid coordinates for candidate
            int candidateGridLat = (int) ((candidate.getLatitude() - minLat) / cellSizeLat);
            int candidateGridLon = (int) ((candidate.getLongitude() - minLon) / cellSizeLon);
            
            // Search radius in grid cells (conservative estimate)
            int searchRadiusInCells = Math.max(1, (int) (accuracy / 100000)); // rough conversion
            
            double densityScore = 0;
            int nearbyCount = 0;
            
            // Check neighboring grid cells
            for (int latOffset = -searchRadiusInCells; latOffset <= searchRadiusInCells; latOffset++) {
                for (int lonOffset = -searchRadiusInCells; lonOffset <= searchRadiusInCells; lonOffset++) {
                    String neighborKey = (candidateGridLat + latOffset) + "," + (candidateGridLon + lonOffset);
                    List<RawLocationPoint> neighbors = grid.get(neighborKey);
                    
                    if (neighbors != null) {
                        for (RawLocationPoint neighbor : neighbors) {
                            if (candidate != neighbor) {
                                nearbyCount++;
                                // Simple proximity weight based on grid distance
                                double gridDistance = Math.sqrt(latOffset * latOffset + lonOffset * lonOffset);
                                double proximityWeight = Math.max(0, 1.0 - (gridDistance / searchRadiusInCells));
                                densityScore += proximityWeight;
                            }
                        }
                    }
                }
            }
            
            // Combine density with accuracy weight
            double accuracyWeight = 1.0 / accuracy;
            densityScore = (densityScore * accuracyWeight) + accuracyWeight;
            
            if (densityScore > maxDensityScore) {
                maxDensityScore = densityScore;
                bestPoint = candidate;
            }
        }
        
        // Fallback to first point if no best point found
        if (bestPoint == null) {
            bestPoint = clusterPoints.getFirst();
        }

        return new GeoPoint(bestPoint.getLatitude(), bestPoint.getLongitude());
    }

    private Visit createVisit(Double longitude, Double latitude, StayPoint stayPoint) {
        return new Visit(longitude, latitude, stayPoint.getArrivalTime(), stayPoint.getDepartureTime(), stayPoint.getDurationSeconds(), false);
    }
}
