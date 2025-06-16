package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.VisitCreatedEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import com.dedicatedcode.reitti.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VisitDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(VisitDetectionService.class);

    // Parameters for stay point detection
    private final double distanceThreshold; // meters
    private final long timeThreshold; // seconds
    private final int minPointsInCluster; // Minimum points to form a valid cluster
    private final UserService userService;
    private final RawLocationPointRepository rawLocationPointRepository;
    private final VisitRepository visitRepository;

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public VisitDetectionService(
            RawLocationPointRepository rawLocationPointRepository,
            @Value("${reitti.staypoint.distance-threshold-meters:50}") double distanceThreshold,
            @Value("${reitti.visit.merge-threshold-seconds:300}") long timeThreshold,
            @Value("${reitti.staypoint.min-points:5}") int minPointsInCluster,
            UserService userService,
            VisitRepository visitRepository,
            RabbitTemplate rabbitTemplate) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.distanceThreshold = distanceThreshold;
        this.timeThreshold = timeThreshold;
        this.minPointsInCluster = minPointsInCluster;
        this.userService = userService;
        this.visitRepository = visitRepository;
        this.rabbitTemplate = rabbitTemplate;

        logger.info("StayPointDetectionService initialized with: distanceThreshold={}m, timeThreshold={}s, minPointsInCluster={}",
                distanceThreshold, timeThreshold, minPointsInCluster);
    }

    @RabbitListener(queues = RabbitMQConfig.STAY_DETECTION_QUEUE, concurrency = "1-16")
    public void detectStayPoints(LocationProcessEvent incoming) {
        logger.debug("Detecting stay points for user {} from {} to {} ", incoming.getUsername(), incoming.getEarliest(), incoming.getLatest());
        User user = userService.getUserByUsername(incoming.getUsername());
        // Get points from 1 day before the earliest new point
        Instant windowStart = incoming.getEarliest().minus(Duration.ofDays(1));
        // Get points from 1 day after the latest new point
        Instant windowEnd = incoming.getLatest().plus(Duration.ofDays(1));

        List<RawLocationPointRepository.ClusteredPoint> clusteredPointsInTimeRangeForUser = this.rawLocationPointRepository.findClusteredPointsInTimeRangeForUser(user, windowStart, windowEnd, minPointsInCluster, GeoUtils.metersToDegreesAtPosition(distanceThreshold, 50)[0]);
        Map<Integer, List<RawLocationPoint>> clusteredByLocation = new HashMap<>();
        for (RawLocationPointRepository.ClusteredPoint clusteredPoint : clusteredPointsInTimeRangeForUser) {
            if (clusteredPoint.getClusterId() != null) {
                clusteredByLocation.computeIfAbsent(clusteredPoint.getClusterId(), k -> new ArrayList<>()).add(clusteredPoint.getPoint());
            }
        }

        logger.debug("Found {} point clusters in the processing window", clusteredByLocation.size());

        // Apply the stay point detection algorithm
        List<StayPoint> stayPoints = detectStayPointsFromTrajectory(clusteredByLocation);

        logger.info("Detected {} stay points for user {}", stayPoints.size(), user.getUsername());

        for (StayPoint stayPoint : stayPoints) {
            List<Visit> existingVisitByStart = this.visitRepository.findByUserAndStartTime(user, stayPoint.getArrivalTime());
            List<Visit> existingVisitByEnd = this.visitRepository.findByUserAndEndTime(user, stayPoint.getDepartureTime());
            List<Visit> overlappingVisits = this.visitRepository.findByUserAndStartTimeBeforeAndEndTimeAfter(user, stayPoint.getDepartureTime(), stayPoint.getArrivalTime());


            Set<Visit> visitsToUpdate = new HashSet<>();
            visitsToUpdate.addAll(existingVisitByStart);
            visitsToUpdate.addAll(existingVisitByEnd);
            visitsToUpdate.addAll(overlappingVisits);


            for (Visit visit : visitsToUpdate) {
                boolean changed = false;
                if (stayPoint.getDepartureTime().isAfter(visit.getEndTime())) {
                    visit.setEndTime(stayPoint.getDepartureTime());
                    visit.setProcessed(false);
                    changed = true;
                }

                if (stayPoint.getArrivalTime().isBefore(visit.getEndTime())) {
                    visit.setStartTime(stayPoint.getArrivalTime().isBefore(visit.getStartTime()) ? stayPoint.getArrivalTime() : visit.getStartTime());
                    visit.setProcessed(false);
                    changed = true;
                }

                if (changed) {
                    try {
                        visitRepository.save(visit);
                        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new VisitUpdatedEvent(user.getUsername(), visit.getId()));
                    } catch (Exception e) {
                        logger.debug("Could not save updated visit: {}", visit);
                    }
                }
            }

            if (visitsToUpdate.isEmpty()) {
                Visit visit = createVisit(user, stayPoint.getLongitude(), stayPoint.getLatitude(), stayPoint);
                logger.debug("Creating new visit: {}", visit);

                try {
                    visit = visitRepository.save(visit);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new VisitCreatedEvent(user.getUsername(), visit.getId()));
                } catch (Exception e) {
                    logger.debug("Could not save new visit: {}", visit);
                }
            }
        }
    }

    private List<StayPoint> detectStayPointsFromTrajectory(Map<Integer, List<RawLocationPoint>> points) {
        logger.debug("Starting cluster-based stay point detection with {} different spatial clusters.", points.size());

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

        logger.debug("Detected {} stay points after splitting them up.", clusters.size());
        //filter them by duration
        List<List<RawLocationPoint>> filteredByMinimumDuration = clusters.stream()
                .filter(c -> Duration.between(c.getFirst().getTimestamp(), c.getLast().getTimestamp()).toSeconds() > timeThreshold)
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

    private Visit createVisit(User user, Double longitude, Double latitude, StayPoint stayPoint) {
        Visit visit = new Visit();
        visit.setUser(user);
        visit.setLongitude(longitude);
        visit.setLatitude(latitude);
        visit.setStartTime(stayPoint.getArrivalTime());
        visit.setEndTime(stayPoint.getDepartureTime());
        visit.setDurationSeconds(stayPoint.getDurationSeconds());
        return visit;
    }
}
