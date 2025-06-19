package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.VisitJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
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
    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final VisitJdbcService visitJdbcService;

    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public VisitDetectionService(
            RawLocationPointJdbcService rawLocationPointJdbcService,
            @Value("${reitti.staypoint.distance-threshold-meters:50}") double distanceThreshold,
            @Value("${reitti.visit.merge-threshold-seconds:300}") long timeThreshold,
            @Value("${reitti.staypoint.min-points:5}") int minPointsInCluster,
            UserJdbcService userJdbcService,
            VisitJdbcService visitJdbcService,
            RabbitTemplate rabbitTemplate, JdbcTemplate jdbcTemplate) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.distanceThreshold = distanceThreshold;
        this.timeThreshold = timeThreshold;
        this.minPointsInCluster = minPointsInCluster;
        this.userJdbcService = userJdbcService;
        this.visitJdbcService = visitJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcTemplate = jdbcTemplate;

        logger.info("StayPointDetectionService initialized with: distanceThreshold={}m, timeThreshold={}s, minPointsInCluster={}",
                distanceThreshold, timeThreshold, minPointsInCluster);
    }

    @RabbitListener(queues = RabbitMQConfig.STAY_DETECTION_QUEUE, concurrency = "1-16")
    public void detectStayPoints(LocationProcessEvent incoming) {
        logger.debug("Detecting stay points for user {} from {} to {} ", incoming.getUsername(), incoming.getEarliest(), incoming.getLatest());
        User user = userJdbcService.getUserByUsername(incoming.getUsername());
        // Get points from 1 day before the earliest new point
        Instant windowStart = incoming.getEarliest().minus(Duration.ofDays(1));
        // Get points from 1 day after the latest new point
        Instant windowEnd = incoming.getLatest().plus(Duration.ofDays(1));

        List<RawLocationPointJdbcService.ClusteredPoint> clusteredPointsInTimeRangeForUser = this.rawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(user, windowStart, windowEnd, minPointsInCluster, GeoUtils.metersToDegreesAtPosition(distanceThreshold, 50)[0]);
        Map<Integer, List<RawLocationPoint>> clusteredByLocation = new HashMap<>();
        for (RawLocationPointJdbcService.ClusteredPoint clusteredPoint : clusteredPointsInTimeRangeForUser) {
            if (clusteredPoint.getClusterId() != null) {
                clusteredByLocation.computeIfAbsent(clusteredPoint.getClusterId(), k -> new ArrayList<>()).add(clusteredPoint.getPoint());
            }
        }

        logger.debug("Found {} point clusters in the processing window", clusteredByLocation.size());

        // Apply the stay point detection algorithm
        List<StayPoint> stayPoints = detectStayPointsFromTrajectory(clusteredByLocation);

        logger.info("Detected {} stay points for user {}", stayPoints.size(), user.getUsername());

        List<Visit> updateVisits = new ArrayList<>();
        List<Visit> createdVisits = new ArrayList<>();

        for (StayPoint stayPoint : stayPoints) {
            List<Visit> existingVisitByStart = this.visitJdbcService.findByUserAndStartTime(user, stayPoint.getArrivalTime());
            List<Visit> existingVisitByEnd = this.visitJdbcService.findByUserAndEndTime(user, stayPoint.getDepartureTime());
            List<Visit> overlappingVisits = this.visitJdbcService.findByUserAndStartTimeBeforeAndEndTimeAfter(user, stayPoint.getDepartureTime(), stayPoint.getArrivalTime());


            Set<Visit> visitsToUpdate = new HashSet<>();
            visitsToUpdate.addAll(existingVisitByStart);
            visitsToUpdate.addAll(existingVisitByEnd);
            visitsToUpdate.addAll(overlappingVisits);


            for (Visit visit : visitsToUpdate) {
                boolean changed = false;
                if (stayPoint.getDepartureTime().isAfter(visit.getEndTime())) {
                    visit = visit.withEndTime(stayPoint.getDepartureTime()).withProcessed(false);
                    changed = true;
                }

                if (stayPoint.getArrivalTime().isBefore(visit.getEndTime())) {
                    visit = visit.withStartTime(stayPoint.getArrivalTime()).withProcessed(false);
                    changed = true;
                }

                if (changed) {
                    updateVisits.add(visit);
                }
            }

            if (visitsToUpdate.isEmpty()) {
                Visit visit = createVisit(stayPoint.getLongitude(), stayPoint.getLatitude(), stayPoint);
                logger.debug("Creating new visit: {}", visit);
                createdVisits.add(visit);
            }
        }


        // Deduplicate visits by ID before bulk update
        List<Visit> deduplicatedVisits = deduplicateVisitsById(updateVisits);

        // Check for time-based duplicates and remove them from database
        List<Visit> duplicatesToDelete = findTimeDuplicates(deduplicatedVisits);
        if (!duplicatesToDelete.isEmpty()) {
            bulkDelete(duplicatesToDelete);
            // Remove deleted visits from the list to update
            deduplicatedVisits.removeAll(duplicatesToDelete);
        }

        List<Long> updatedIds = bulkUpdate(deduplicatedVisits).stream().map(Visit::getId).toList();

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new VisitUpdatedEvent(user.getUsername(), updatedIds));
        List<Long> createdIds = bulkInsert(user, createdVisits).stream().map(Visit::getId).toList();
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new VisitUpdatedEvent(user.getUsername(), createdIds));
    }

    private List<Visit> bulkInsert(User user, List<Visit> visitsToInsert) {
        if (visitsToInsert.isEmpty()) {
            return Collections.emptyList();
        }
        logger.debug("Bulk inserting {} visits", visitsToInsert.size());

        List<Visit> createdVisits = new ArrayList<>();
        String sql = """
                INSERT INTO visits (user_id, latitude, longitude, start_time, end_time, duration_seconds, processed, version)
                VALUES (?, ?, ?, ?, ?, ?, false, 1) ON CONFLICT DO NOTHING;
                """;

        List<Object[]> batchArgs = visitsToInsert.stream()
                .map(visit -> new Object[]{
                        user.getId(),
                        visit.getLatitude(),
                        visit.getLongitude(),
                        Timestamp.from(visit.getStartTime()),
                        Timestamp.from(visit.getEndTime()),
                        visit.getDurationSeconds()
                })
                .collect(Collectors.toList());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        for (int i = 0; i < updateCounts.length; i++) {
            int updateCount = updateCounts[i];
            if (updateCount > 0) {
                createdVisits.addAll(this.visitJdbcService.findByUserAndStartTimeAndEndTime(user, visitsToInsert.get(i).getStartTime(), visitsToInsert.get(i).getEndTime()));
            }
        }
        logger.debug("Successfully inserted {} visits", createdVisits.size());
        return createdVisits;
    }

    private List<Visit> bulkUpdate(List<Visit> visitsToUpdate) {
        if (visitsToUpdate.isEmpty()) {
            return Collections.emptyList();
        }

        List<Visit> updatedVisits = new ArrayList<>();
        logger.debug("Bulk updating {} visits", visitsToUpdate.size());

        String sql = """
                UPDATE visits
                SET start_time = ?, end_time = ?, duration_seconds = ?, processed = ?
                WHERE id = ?
                """;

        List<Object[]> batchArgs = visitsToUpdate.stream()
                .map(visit -> new Object[]{
                        Timestamp.from(visit.getStartTime()),
                        Timestamp.from(visit.getEndTime()),
                        visit.getDurationSeconds(),
                        visit.isProcessed(),
                        visit.getId()
                })
                .collect(Collectors.toList());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);

        for (int i = 0; i < updateCounts.length; i++) {
            int updateCount = updateCounts[i];
            if (updateCount > 0) {
                updatedVisits.add(visitsToUpdate.get(i));
            }
        }

        logger.debug("Successfully updated {} visits", updatedVisits.size());
        return updatedVisits;
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

    private List<Visit> deduplicateVisitsById(List<Visit> visits) {
        Map<Long, Visit> visitMap = new HashMap<>();

        for (Visit visit : visits) {
            Long visitId = visit.getId();
            if (visitMap.containsKey(visitId)) {
                Visit existing = visitMap.get(visitId);

                // Take the earliest start time
                Instant earliestStart = visit.getStartTime().isBefore(existing.getStartTime())
                        ? visit.getStartTime() : existing.getStartTime();

                // Take the latest end time
                Instant latestEnd = visit.getEndTime().isAfter(existing.getEndTime())
                        ? visit.getEndTime() : existing.getEndTime();

                // Update the existing visit with combined times
                visitMap.put(visitId, existing.withStartTime(earliestStart)
                        .withEndTime(latestEnd)
                        .withDurationSeconds(latestEnd.getEpochSecond() - earliestStart.getEpochSecond())
                        .withProcessed(false));
            } else {
                visitMap.put(visitId, visit);
            }
        }

        return new ArrayList<>(visitMap.values());
    }

    private List<Visit> findTimeDuplicates(List<Visit> visits) {
        Map<String, List<Visit>> timeGroups = new HashMap<>();

        // Group visits by start and end time
        for (Visit visit : visits) {
            String timeKey = visit.getStartTime() + "_" + visit.getEndTime();
            timeGroups.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(visit);
        }

        List<Visit> duplicatesToDelete = new ArrayList<>();

        // For each time group with multiple visits, keep one and mark others for deletion
        for (List<Visit> timeGroup : timeGroups.values()) {
            if (timeGroup.size() > 1) {
                // Sort by ID to have consistent behavior (keep the one with lowest ID)
                timeGroup.sort(Comparator.comparing(Visit::getId));
                // Add all except the first one to deletion list
                duplicatesToDelete.addAll(timeGroup.subList(1, timeGroup.size()));
                logger.debug("Found {} time-based duplicates for time range {}-{}",
                        timeGroup.size() - 1,
                        timeGroup.getFirst().getStartTime(),
                        timeGroup.getFirst().getEndTime());
            }
        }

        return duplicatesToDelete;
    }

    private void bulkDelete(List<Visit> visitsToDelete) {
        if (visitsToDelete.isEmpty()) {
            return;
        }

        logger.debug("Bulk deleting {} visits", visitsToDelete.size());

        String sql = "DELETE FROM visits WHERE id = ?";

        List<Object[]> batchArgs = visitsToDelete.stream()
                .map(visit -> new Object[]{visit.getId()})
                .collect(Collectors.toList());

        int[] deleteCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        logger.debug("Successfully deleted {} visits", deleteCounts.length);
    }

    private Visit createVisit(Double longitude, Double latitude, StayPoint stayPoint) {
        return new Visit(longitude, latitude, stayPoint.getArrivalTime(), stayPoint.getDepartureTime(), stayPoint.getDurationSeconds(), false);
    }
}
