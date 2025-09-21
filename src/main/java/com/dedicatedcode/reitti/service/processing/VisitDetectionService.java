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
            double[] metersAsDegrees = GeoUtils.metersToDegreesAtPosition(detectionParameters.getSearchDistanceInMeters(), baseLatitude);
            List<ClusteredPoint> clusteredPointsInTimeRangeForUser;
            if (incoming.getPreviewId() == null) {
                clusteredPointsInTimeRangeForUser = this.rawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(user, windowStart, windowEnd, detectionParameters.getMinimumAdjacentPoints(), metersAsDegrees[0]);
            } else {
                clusteredPointsInTimeRangeForUser = this.previewRawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(user, incoming.getPreviewId(), windowStart, windowEnd, detectionParameters.getMinimumAdjacentPoints(), metersAsDegrees[0]);
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

    private Visit createVisit(Double longitude, Double latitude, StayPoint stayPoint) {
        return new Visit(longitude, latitude, stayPoint.getArrivalTime(), stayPoint.getDepartureTime(), stayPoint.getDurationSeconds(), false);
    }
}
