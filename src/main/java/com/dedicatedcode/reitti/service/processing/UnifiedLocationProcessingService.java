package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.ClusteredPoint;
import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.*;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.GeoLocationTimezoneService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified service that processes the entire GPS pipeline atomically per user.
 * Ensures deterministic, repeatable results by processing events sequentially
 * per user while maintaining parallelism across different users.
 */
@Service
public class UnifiedLocationProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedLocationProcessingService.class);

    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final PreviewTripJdbcService previewTripJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final PreviewSignificantPlaceJdbcService previewSignificantPlaceJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final PreviewVisitDetectionParametersJdbcService previewVisitDetectionParametersJdbcService;
    private final TransportModeService transportModeService;
    private final UserNotificationService userNotificationService;
    private final GeoLocationTimezoneService timezoneService;
    private final GeometryFactory geometryFactory;
    private final RabbitTemplate rabbitTemplate;

    public UnifiedLocationProcessingService(
            UserJdbcService userJdbcService,
            RawLocationPointJdbcService rawLocationPointJdbcService,
            PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
            ProcessedVisitJdbcService processedVisitJdbcService,
            PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService,
            TripJdbcService tripJdbcService,
            PreviewTripJdbcService previewTripJdbcService,
            SignificantPlaceJdbcService significantPlaceJdbcService,
            PreviewSignificantPlaceJdbcService previewSignificantPlaceJdbcService,
            SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
            VisitDetectionParametersService visitDetectionParametersService,
            PreviewVisitDetectionParametersJdbcService previewVisitDetectionParametersJdbcService,
            TransportModeService transportModeService,
            UserNotificationService userNotificationService,
            GeoLocationTimezoneService timezoneService,
            GeometryFactory geometryFactory, RabbitTemplate rabbitTemplate) {

        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.previewProcessedVisitJdbcService = previewProcessedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.previewTripJdbcService = previewTripJdbcService;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.previewSignificantPlaceJdbcService = previewSignificantPlaceJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.visitDetectionParametersService = visitDetectionParametersService;
        this.previewVisitDetectionParametersJdbcService = previewVisitDetectionParametersJdbcService;
        this.transportModeService = transportModeService;
        this.userNotificationService = userNotificationService;
        this.timezoneService = timezoneService;
        this.geometryFactory = geometryFactory;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Entry point for location processing events.
     * Enqueues the event for the user and ensures processing starts.
     */
    public void processLocationEvent(LocationProcessEvent event) {
        long startTime = System.currentTimeMillis();
        String username = event.getUsername();
        String previewId = event.getPreviewId();

        logger.info("Processing location data for user [{}], mode: {}",
                username, previewId == null ? "LIVE" : "PREVIEW");

        User user = userJdbcService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        // STEP 1: Visit Detection
        // ----------------------
        VisitDetectionResult detectionResult = detectVisits(user, event);
        logger.debug("Detection: {} visits created", detectionResult.visits.size());

        // STEP 2: Visit Merging
        // ---------------------
        VisitMergingResult mergingResult = mergeVisits(
                user,
                previewId,
                event.getTraceId(),
                detectionResult.searchStart,
                detectionResult.searchEnd,
                detectionResult.visits);
        logger.debug("Merging: {} visits merged into {} processed visits",
                mergingResult.inputVisits.size(),
                mergingResult.processedVisits.size());

        // STEP 3: Trip Detection
        // ----------------------
        TripDetectionResult tripResult = detectTrips(
                user,
                previewId,
                mergingResult.searchStart,
                mergingResult.searchEnd,
                mergingResult.processedVisits
        );
        logger.debug("Trip detection: {} trips created", tripResult.trips.size());

        // STEP 4: Notifications
        // ---------------------
        if (previewId == null) {
            userNotificationService.newVisits(user, mergingResult.processedVisits);
            userNotificationService.newTrips(user, tripResult.trips);
        } else {
            userNotificationService.newTrips(user, tripResult.trips, previewId);
        }

        long duration = System.currentTimeMillis() - startTime;

        if (logger.isTraceEnabled()) {
            // Tabular output for trace level logging
            StringBuilder traceOutput = new StringBuilder();
            traceOutput.append("\n=== PROCESSING RESULTS FOR USER [").append(username).append("] ===\n");
            traceOutput.append("Event Period: ").append(event.getEarliest()).append(" → ").append(event.getLatest()).append("\n");
            traceOutput.append("Search Period: ").append(detectionResult.searchStart).append(" → ").append(detectionResult.searchEnd).append("\n");
            traceOutput.append("Duration: ").append(duration).append("ms\n\n");

            // Input Visits Table
            traceOutput.append("INPUT VISITS (").append(detectionResult.visits.size()).append(") - took [").append(detectionResult.durationInMillis).append("]ms:\n");
            traceOutput.append("┌─────────────────────┬─────────────────────┬───────────┬─────────────┬─────────────┬──────────────────────────────────────────────────────────────────────┐\n");
            traceOutput.append("│ Start Time          │ End Time            │ Duration  │ Latitude    │ Longitude   │ Google Maps Link                                                     │\n");
            traceOutput.append("├─────────────────────┼─────────────────────┼───────────┼─────────────┼─────────────┼──────────────────────────────────────────────────────────────────────┤\n");
            for (Visit visit : detectionResult.visits) {
                String googleMapsLink = "https://www.google.com/maps/search/?api=1&query=" + visit.getLatitude() + "," + visit.getLongitude();
                traceOutput.append(String.format("│ %-19s │ %-19s │ %8ds │ %11.6f │ %11.6f │ %-68s │\n",
                    visit.getStartTime().toString().substring(0, 19),
                    visit.getEndTime().toString().substring(0, 19),
                    visit.getDurationSeconds(),
                    visit.getLatitude(),
                    visit.getLongitude(),
                    googleMapsLink));
            }
            traceOutput.append("└─────────────────────┴─────────────────────┴───────────┴─────────────┴─────────────┴──────────────────────────────────────────────────────────────────────┘\n\n");

            // Processed Visits Table
            traceOutput.append("PROCESSED VISITS (").append(mergingResult.processedVisits.size()).append(") - took [").append(mergingResult.durationInMillis).append("]ms:\n");
            traceOutput.append("┌─────────────────────┬─────────────────────┬───────────┬─────────────┬─────────────┬──────────────────────┐\n");
            traceOutput.append("│ Start Time          │ End Time            │ Duration  │ Latitude    │ Longitude   │ Place Name           │\n");
            traceOutput.append("├─────────────────────┼─────────────────────┼───────────┼─────────────┼─────────────┼──────────────────────┤\n");
            for (ProcessedVisit visit : mergingResult.processedVisits) {
                String placeName = visit.getPlace().getName() != null ? visit.getPlace().getName() : "Unnamed Place";
                if (placeName.length() > 20) placeName = placeName.substring(0, 17) + "...";
                traceOutput.append(String.format("│ %-19s │ %-19s │ %8ds │ %11.6f │ %11.6f │ %-20s │\n",
                    visit.getStartTime().toString().substring(0, 19),
                    visit.getEndTime().toString().substring(0, 19),
                    visit.getDurationSeconds(),
                    visit.getPlace().getLatitudeCentroid(),
                    visit.getPlace().getLongitudeCentroid(),
                    placeName));
            }
            traceOutput.append("└─────────────────────┴─────────────────────┴───────────┴─────────────┴─────────────┴──────────────────────┘\n\n");

            // Trips Table
            traceOutput.append("TRIPS (").append(tripResult.trips.size()).append(") - took [").append(tripResult.durationInMillis).append("]ms:\n");
            traceOutput.append("┌─────────────────────┬─────────────────────┬───────────┬───────────┬───────────┬─────────────────┐\n");
            traceOutput.append("│ Start Time          │ End Time            │ Duration  │ Distance  │ Traveled  │ Transport Mode  │\n");
            traceOutput.append("├─────────────────────┼─────────────────────┼───────────┼───────────┼───────────┼─────────────────┤\n");
            for (Trip trip : tripResult.trips) {
                traceOutput.append(String.format("│ %-19s │ %-19s │ %8ds │ %8.0fm │ %8.0fm │ %-15s │\n",
                    trip.getStartTime().toString().substring(0, 19),
                    trip.getEndTime().toString().substring(0, 19),
                    trip.getDurationSeconds(),
                    trip.getEstimatedDistanceMeters(),
                    trip.getTravelledDistanceMeters(),
                    trip.getTransportModeInferred().toString()));
            }
            traceOutput.append("└─────────────────────┴─────────────────────┴───────────┴───────────┴───────────┴─────────────────┘\n");

            logger.trace(traceOutput.toString());
        }

        logger.info("Completed processing for user [{}] in {}ms: {} visits → {} processed visits → {} trips",
                username, duration, detectionResult.visits.size(),
                mergingResult.processedVisits.size(), tripResult.trips.size());
    }

    /**
     * STEP 1: Visit Detection
     * Detects stay points from raw location data and creates Visit entities.
     */
    private VisitDetectionResult detectVisits(User user, LocationProcessEvent event) {
        long start = System.currentTimeMillis();

        String previewId = event.getPreviewId();
        Instant windowStart = event.getEarliest().minus(1, ChronoUnit.DAYS);
        Instant windowEnd = event.getLatest().plus(1, ChronoUnit.DAYS);

        // Get detection parameters
        DetectionParameter currentConfiguration;
        DetectionParameter.VisitDetection detectionParams;
        if (previewId == null) {
            currentConfiguration = visitDetectionParametersService.getCurrentConfiguration(user, windowStart);
        } else {
            currentConfiguration = previewVisitDetectionParametersJdbcService.findCurrent(user, previewId);
        }
        detectionParams = currentConfiguration.getVisitDetection();

        List<ProcessedVisit> existingProcessedVisits;
        if (previewId == null) {
            existingProcessedVisits = processedVisitJdbcService
                    .findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, windowEnd, windowStart);
        } else {
            existingProcessedVisits = previewProcessedVisitJdbcService
                    .findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, previewId, windowEnd, windowStart);
        }

        // Expand window based on deleted processed visits
        if (!existingProcessedVisits.isEmpty()) {
            if (existingProcessedVisits.getFirst().getStartTime().isBefore(windowStart)) {
                windowStart = existingProcessedVisits.getFirst().getStartTime();
            }
            if (existingProcessedVisits.getLast().getEndTime().isAfter(windowEnd)) {
                windowEnd = existingProcessedVisits.getLast().getEndTime();
            }
        }

        // Get clustered points
        double baseLatitude = existingProcessedVisits.isEmpty() ? 50 : existingProcessedVisits.getFirst().getPlace().getLatitudeCentroid();
        double metersAsDegrees = GeoUtils.metersToDegreesAtPosition((double) currentConfiguration.getVisitMerging().getMinDistanceBetweenVisits() / 2, baseLatitude);

        List<ClusteredPoint> clusteredPoints;
        int minimumAdjacentPoints = Math.max(4, Math.toIntExact(detectionParams.getMinimumStayTimeInSeconds() / 20));
        if (previewId == null) {
            clusteredPoints = rawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(
                    user, windowStart, windowEnd, minimumAdjacentPoints, metersAsDegrees);
        } else {
            clusteredPoints = previewRawLocationPointJdbcService.findClusteredPointsInTimeRangeForUser(
                    user, previewId, windowStart, windowEnd, minimumAdjacentPoints, metersAsDegrees);
        }
        logger.debug("Searching for clustered points in range [{}, {}], minimum adjacent points: {} ", windowStart, windowEnd, minimumAdjacentPoints);

        // Cluster by location and time
        Map<Integer, List<RawLocationPoint>> clusteredByLocation = new TreeMap<>();
        for (ClusteredPoint cp : clusteredPoints.stream().filter(clusteredPoint -> !clusteredPoint.getPoint().isIgnored()).toList()) {
            if (cp.getClusterId() != null) {
                clusteredByLocation
                        .computeIfAbsent(cp.getClusterId(), _ -> new ArrayList<>())
                        .add(cp.getPoint());
            }
        }

        // Detect stay points
        List<StayPoint> stayPoints = detectStayPointsFromTrajectory(clusteredByLocation, detectionParams);

        // Create visits
        List<Visit> visits = stayPoints.stream()
                .map(sp -> new Visit(
                        sp.getLongitude(),
                        sp.getLatitude(),
                        sp.getArrivalTime(),
                        sp.getDepartureTime(),
                        sp.getDurationSeconds(),
                        false
                ))
                .toList();

        return new VisitDetectionResult(visits, windowStart, windowEnd, System.currentTimeMillis() - start);
    }

    /**
     * STEP 2: Visit Merging
     * Merges nearby visits into ProcessedVisit entities with SignificantPlaces.
     */
    private VisitMergingResult mergeVisits(User user, String previewId, String traceId, Instant initialStart, Instant initialEnd, List<Visit> allVisits) {
        long start = System.currentTimeMillis();

        // Get merging parameters
        DetectionParameter.VisitMerging mergeConfig;
        if (previewId == null) {
            mergeConfig = visitDetectionParametersService
                    .getCurrentConfiguration(user, initialStart)
                    .getVisitMerging();
        } else {
            mergeConfig = previewVisitDetectionParametersJdbcService
                    .findCurrent(user, previewId)
                    .getVisitMerging();
        }

        // Expand search window for merging
        Instant searchStart = initialStart;
        Instant searchEnd = initialEnd;

        // Delete existing processed visits in range
        List<ProcessedVisit> existingProcessedVisits;
        if (previewId == null) {
            existingProcessedVisits = processedVisitJdbcService
                    .findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, searchEnd, searchStart);
            processedVisitJdbcService.deleteAll(existingProcessedVisits);
        } else {
            existingProcessedVisits = previewProcessedVisitJdbcService
                    .findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, previewId, searchEnd, searchStart);
            previewProcessedVisitJdbcService.deleteAll(existingProcessedVisits);
        }

        // Expand window based on deleted processed visits
        if (!existingProcessedVisits.isEmpty()) {
            if (existingProcessedVisits.getFirst().getStartTime().isBefore(searchStart)) {
                searchStart = existingProcessedVisits.getFirst().getStartTime();
            }
            if (existingProcessedVisits.getLast().getEndTime().isAfter(searchEnd)) {
                searchEnd = existingProcessedVisits.getLast().getEndTime();
            }
        }

        if (allVisits.isEmpty()) {
            return new VisitMergingResult(List.of(), List.of(), searchStart, searchEnd, System.currentTimeMillis() - start);
        }

        allVisits = allVisits.stream().sorted(Comparator.comparing(Visit::getStartTime)).toList();
        // Merge visits chronologically
        List<ProcessedVisit> processedVisits = mergeVisitsChronologically(
                user, previewId, traceId, allVisits, mergeConfig);

        // Save processed visits
        if (previewId == null) {
            processedVisits = processedVisitJdbcService.bulkInsert(user, processedVisits);
        } else {
            processedVisits = previewProcessedVisitJdbcService.bulkInsert(
                    user, previewId, processedVisits);
        }

        return new VisitMergingResult(allVisits, processedVisits, searchStart, searchEnd, System.currentTimeMillis() - start);
    }

    /**
     * STEP 3: Trip Detection
     * Creates Trip entities between consecutive ProcessedVisits.
     */
    private TripDetectionResult detectTrips(User user, String previewId, Instant searchStart, Instant searchEnd, List<ProcessedVisit> processedVisits) {

        long start = System.currentTimeMillis();
        processedVisits.sort(Comparator.comparing(ProcessedVisit::getStartTime));

        // Delete existing trips in range
        if (previewId == null) {
            List<Trip> existingTrips = tripJdbcService.findByUserAndTimeOverlap(
                    user, searchStart, searchEnd);
            tripJdbcService.deleteAll(existingTrips);
        } else {
            List<Trip> existingTrips = previewTripJdbcService.findByUserAndTimeOverlap(
                    user, previewId, searchStart, searchEnd);
            previewTripJdbcService.deleteAll(existingTrips);
        }

        // Create trips between consecutive visits
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < processedVisits.size() - 1; i++) {
            ProcessedVisit startVisit = processedVisits.get(i);
            ProcessedVisit endVisit = processedVisits.get(i + 1);

            Trip trip = createTripBetweenVisits(user, previewId, startVisit, endVisit);
            if (trip != null) {
                trips.add(trip);
            }
        }

        // Save trips
        if (previewId == null) {
            trips = tripJdbcService.bulkInsert(user, trips);
        } else {
            trips = previewTripJdbcService.bulkInsert(user, previewId, trips);
        }

        return new TripDetectionResult(trips, System.currentTimeMillis() - start);
    }

    // ==================== Helper Methods ====================
    // Copy from existing services with minimal changes

    private List<StayPoint> detectStayPointsFromTrajectory(
            Map<Integer, List<RawLocationPoint>> points,
            DetectionParameter.VisitDetection visitDetectionParameters) {
        logger.debug("Starting cluster-based stay point detection with {} different spatial clusters.", points.size());

        List<List<RawLocationPoint>> clusters = new ArrayList<>();

        //split them up when time is x seconds between
        for (List<RawLocationPoint> clusteredByLocation : points.values()) {
            logger.debug("Start splitting up geospatial cluster with [{}] elements based on minimum time [{}]s between points", clusteredByLocation.size(), visitDetectionParameters.getMaxMergeTimeBetweenSameStayPoints());
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

        logger.debug("Found {} valid clusters after duration filtering with minimum stay time [{}]s", filteredByMinimumDuration.size(), visitDetectionParameters.getMinimumStayTimeInSeconds());

        // Step 3: Convert valid clusters to stay points
        return filteredByMinimumDuration.stream()
                .map(this::createStayPoint)
                .collect(Collectors.toList());
    }

    private List<ProcessedVisit> mergeVisitsChronologically(
            User user, String previewId, String traceId, List<Visit> visits,
            DetectionParameter.VisitMerging mergeConfiguration) {
        if (visits.isEmpty()) {
            return new ArrayList<>();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Merging [{}] visits between [{}] and [{}]", visits.size(), visits.getFirst().getStartTime(), visits.getLast().getEndTime());
        }
        List<ProcessedVisit> result = new ArrayList<>();

        // Start with the first visit
        Visit currentVisit = visits.getFirst();
        Instant currentStartTime = currentVisit.getStartTime();
        Instant currentEndTime = currentVisit.getEndTime();
        SignificantPlace currentPlace = findOrCreateSignificantPlace(user, previewId, currentVisit.getLatitude(), currentVisit.getLongitude(), mergeConfiguration, traceId);

        for (int i = 1; i < visits.size(); i++) {
            Visit nextVisit = visits.get(i);

            if (nextVisit.getStartTime().isBefore(currentEndTime)) {
                logger.debug("Skipping visit [{}] because it starts before the end time of the previous one [{}]", nextVisit, currentEndTime);
                continue;
            }
            SignificantPlace nextPlace = findOrCreateSignificantPlace(user, previewId, nextVisit.getLatitude(), nextVisit.getLongitude(), mergeConfiguration, traceId);

            boolean samePlace = nextPlace.getId().equals(currentPlace.getId());
            boolean withinTimeThreshold = Duration.between(currentEndTime, nextVisit.getStartTime()).getSeconds() <= mergeConfiguration.getMaxMergeTimeBetweenSameVisits();

            boolean shouldMergeWithNextVisit = samePlace && withinTimeThreshold;

            if (samePlace && !withinTimeThreshold) {
                List<RawLocationPoint> pointsBetweenVisits;
                if (previewId == null) {
                    pointsBetweenVisits = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, currentEndTime, nextVisit.getStartTime());
                } else {
                    pointsBetweenVisits = this.previewRawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, previewId, currentEndTime, nextVisit.getStartTime());
                }
                if (pointsBetweenVisits.size() > 2) {
                    double travelledDistanceInMeters = GeoUtils.calculateTripDistance(pointsBetweenVisits);
                    shouldMergeWithNextVisit = travelledDistanceInMeters <= mergeConfiguration.getMinDistanceBetweenVisits();
                } else {
                    logger.debug("There are no points tracked between {} and {}. Will merge consecutive visits because they are on the same place", currentEndTime, nextVisit.getStartTime());
                    shouldMergeWithNextVisit = true;
                }
            }

            if (shouldMergeWithNextVisit) {
                currentEndTime = nextVisit.getEndTime().isAfter(currentEndTime) ?
                        nextVisit.getEndTime() : currentEndTime;
            } else {
                // Finalize the current merged visit
                ProcessedVisit processedVisit = createProcessedVisit(currentPlace, currentStartTime, currentEndTime);
                if (processedVisit != null) {
                    result.add(processedVisit);

                    // This is the end time of the visit we just created.
                    Instant previousProcessedVisitEndTime = processedVisit.getEndTime();

                    // Start a new merged set, ensuring it does not start before the previous one ended.
                    currentPlace = nextPlace;
                    currentStartTime = nextVisit.getStartTime();
                    currentEndTime = nextVisit.getEndTime();

                    // FIX: Adjust start time to prevent overlap.
                    if (currentStartTime.isBefore(previousProcessedVisitEndTime)) {
                        currentStartTime = previousProcessedVisitEndTime;
                    }

                    // FIX: Ensure the end time is not before the (potentially adjusted) start time.
                    // This handles cases where a visit is completely enveloped by the previous one.
                    if (currentEndTime.isBefore(currentStartTime)) {
                        currentEndTime = currentStartTime;
                    }
                }
            }
        }

        ProcessedVisit lastProcessedVisit = createProcessedVisit(currentPlace, currentStartTime, currentEndTime);
        if (lastProcessedVisit != null) {
            result.add(lastProcessedVisit);
        }
        return result;
    }

    private ProcessedVisit createProcessedVisit(SignificantPlace place, Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime)) {
            logger.warn("Skipping zero or negative duration processed visit for place [{}] between [{}] and [{}]", place.getId(), startTime, endTime);
            return null;  // Indicate to skip
        }
        if (endTime.equals(startTime)) {
            logger.warn("Skipping zero duration processed visit for place [{}] from [{} -> {}]", place.getId(), startTime, endTime);
            return null;
        }
        logger.debug("Creating processed visit for place [{}] between [{}] and [{}]", place.getId(), startTime, endTime);
        return new ProcessedVisit(place, startTime, endTime, endTime.getEpochSecond() - startTime.getEpochSecond());
    }

    private StayPoint createStayPoint(List<RawLocationPoint> clusterPoints) {
        GeoPoint result = weightedCenter(clusterPoints);

        // Get the time range
        Instant arrivalTime = clusterPoints.getFirst().getTimestamp();
        Instant departureTime = clusterPoints.getLast().getTimestamp();

        logger.debug("Creating stay point at [{}] with arrival time [{}] and departure time [{}]", result, arrivalTime, departureTime);
        return new StayPoint(result.latitude(), result.longitude(), arrivalTime, departureTime, clusterPoints);
    }

    private GeoPoint weightedCenter(List<RawLocationPoint> clusterPoints) {

        long start = System.currentTimeMillis();

        GeoPoint result;
        // For small clusters, use the original algorithm
        if (clusterPoints.size() <= 100) {
            result = weightedCenterSimple(clusterPoints);
        } else {
            // For large clusters, use spatial partitioning for better performance
            result = weightedCenterOptimized(clusterPoints);
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
        double minLon = clusterPoints.stream().mapToDouble(RawLocationPoint::getLongitude).min().orElse(0);

        // Grid cell size approximately 10 meters (rough approximation)
        double cellSizeLat = 0.0001; // ~11 meters
        double cellSizeLon = 0.0001; // varies by latitude but roughly 11 meters

        // Create grid map for fast neighbor lookup
        Map<String, List<RawLocationPoint>> grid = new HashMap<>();
        for (RawLocationPoint point : clusterPoints) {
            int gridLat = (int) ((point.getLatitude() - minLat) / cellSizeLat);
            int gridLon = (int) ((point.getLongitude() - minLon) / cellSizeLon);
            String gridKey = gridLat + "," + gridLon;
            grid.computeIfAbsent(gridKey, _ -> new ArrayList<>()).add(point);
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

            // Check neighboring grid cells
            for (int latOffset = -searchRadiusInCells; latOffset <= searchRadiusInCells; latOffset++) {
                for (int lonOffset = -searchRadiusInCells; lonOffset <= searchRadiusInCells; lonOffset++) {
                    String neighborKey = (candidateGridLat + latOffset) + "," + (candidateGridLon + lonOffset);
                    List<RawLocationPoint> neighbors = grid.get(neighborKey);

                    if (neighbors != null) {
                        for (RawLocationPoint neighbor : neighbors) {
                            if (candidate != neighbor) {
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

    private Trip createTripBetweenVisits(User user, String previewId,
                                         ProcessedVisit startVisit, ProcessedVisit endVisit) {
        // Trip starts when the first visit ends
        Instant tripStartTime = startVisit.getEndTime();

        // Trip ends when the second visit starts
        Instant tripEndTime = endVisit.getStartTime();

        if (previewId != null) {
            if (this.previewProcessedVisitJdbcService.findById(startVisit.getId()).isEmpty() || this.previewProcessedVisitJdbcService.findById(endVisit.getId()).isEmpty()) {
                logger.debug("One of the following preview visits [{},{}] where already deleted. Will skip trip creation.", startVisit.getId(), endVisit.getId());
                return null;
            }
        } else {
            if (this.processedVisitJdbcService.findById(startVisit.getId()).isEmpty() || this.processedVisitJdbcService.findById(endVisit.getId()).isEmpty()) {
                logger.debug("One of the following visits [{},{}] where already deleted. Will skip trip creation.", startVisit.getId(), endVisit.getId());
                return null;
            }
        }
        // If end time is before or equal to start time, this is not a valid trip
        if (tripEndTime.isBefore(tripStartTime) || tripEndTime.equals(tripStartTime)) {
            logger.warn("Invalid trip time range detected for user {}: {} to {}",
                    user.getUsername(), tripStartTime, tripEndTime);
            return null;
        }


        if (previewId == null) {
            // Check if a trip already exists with the same start and end times
            if (tripJdbcService.existsByUserAndStartTimeAndEndTime(user, tripStartTime, tripEndTime)) {
                logger.debug("Trip already exists for user {} from {} to {}",
                        user.getUsername(), tripStartTime, tripEndTime);
                return null;
            }
        }

        // Get location points between the two visits
        List<RawLocationPoint> tripPoints;
        if (previewId == null) {
            tripPoints = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, tripStartTime, tripEndTime);
        } else {
            tripPoints = previewRawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, previewId, tripStartTime, tripEndTime);
        }
        double estimatedDistanceInMeters = calculateDistanceBetweenPlaces(startVisit.getPlace(), endVisit.getPlace());
        double travelledDistanceMeters = GeoUtils.calculateTripDistance(tripPoints);
        // Create a new trip
        TransportMode transportMode = this.transportModeService.inferTransportMode(user, tripPoints, tripStartTime, tripEndTime);
        Trip trip = new Trip(
                tripStartTime,
                tripEndTime,
                tripEndTime.getEpochSecond() - tripStartTime.getEpochSecond(),
                estimatedDistanceInMeters,
                travelledDistanceMeters,
                transportMode,
                startVisit,
                endVisit
        );
        logger.debug("Created trip from {} to {}: travelled distance={}m, mode={}",
                Optional.ofNullable(startVisit.getPlace().getName()).orElse("Unknown Name"),
                Optional.ofNullable(endVisit.getPlace().getName()).orElse("Unknown Name"),
                Math.round(travelledDistanceMeters),
                transportMode);

        // Save and return the trip
        return trip;
    }

    private List<SignificantPlace> findNearbyPlaces(User user, String previewId, double latitude, double longitude, DetectionParameter.VisitMerging mergeConfiguration) {
        // Create a point geometry
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        // Find places within the merge distance
        if (previewId == null) {
            return significantPlaceJdbcService.findEnclosingPlaces(user.getId(), point, GeoUtils.metersToDegreesAtPosition((double) mergeConfiguration.getMinDistanceBetweenVisits() / 2, latitude));
        } else {
            return previewSignificantPlaceJdbcService.findNearbyPlaces(user.getId(), point, GeoUtils.metersToDegreesAtPosition((double) mergeConfiguration.getMinDistanceBetweenVisits() /2, latitude), previewId);
        }
    }

    private SignificantPlace findOrCreateSignificantPlace(User user, String previewId,
                                                          double latitude, double longitude,
                                                          DetectionParameter.VisitMerging mergeConfig,
                                                          String traceId) {
        List<SignificantPlace> nearbyPlaces = findNearbyPlaces(user, previewId, latitude, longitude, mergeConfig);
        return nearbyPlaces.isEmpty() ? createSignificantPlace(user, latitude, longitude, previewId, traceId) : findClosestPlace(latitude, longitude, nearbyPlaces);
    }


    private SignificantPlace createSignificantPlace(User user, double latitude, double longitude, String previewId, String traceId) {
        SignificantPlace significantPlace = SignificantPlace.create(latitude, longitude);
        Optional<ZoneId> timezone = this.timezoneService.getTimezone(significantPlace);
        if (timezone.isPresent()) {
            significantPlace = significantPlace.withTimezone(timezone.get());
        }
        // Check for override
        GeoPoint point = new GeoPoint(significantPlace.getLatitudeCentroid(), significantPlace.getLongitudeCentroid());
        Optional<PlaceInformationOverride> override = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);
        if (override.isPresent()) {
            logger.info("Found override for user [{}] and location [{}], using override information: {}", user.getUsername(), point, override.get());
            significantPlace = significantPlace
                    .withName(override.get().name())
                    .withType(override.get().category())
                    .withTimezone(override.get().timezone())
                    .withPolygon(override.get().polygon());
        }
        significantPlace = previewId == null ? this.significantPlaceJdbcService.create(user, significantPlace) : this.previewSignificantPlaceJdbcService.create(user, previewId, significantPlace);
        publishSignificantPlaceCreatedEvent(user, significantPlace, previewId, traceId);
        return significantPlace;
    }

    private SignificantPlace findClosestPlace(double latitude, double longitude, List<SignificantPlace> places) {

        Comparator<SignificantPlace> distanceComparator = Comparator.comparingDouble(place ->
                GeoUtils.distanceInMeters(
                        latitude, longitude,
                        place.getLatitudeCentroid(), place.getLongitudeCentroid()));
        return places.stream()
                .min(distanceComparator.thenComparing(SignificantPlace::getId))
                .orElseThrow(() -> new IllegalStateException("No places found"));
    }

    private double calculateDistanceBetweenPlaces(SignificantPlace place1, SignificantPlace place2) {
        return GeoUtils.distanceInMeters(
                place1.getLatitudeCentroid(), place1.getLongitudeCentroid(),
                place2.getLatitudeCentroid(), place2.getLongitudeCentroid());
    }

    private void publishSignificantPlaceCreatedEvent(User user, SignificantPlace place, String previewId, String traceId) {
        SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                user.getUsername(),
                previewId,
                place.getId(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                traceId
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
        logger.info("Published SignificantPlaceCreatedEvent for place ID: {}", place.getId());
    }

    // ==================== Result Classes ====================

    private record VisitDetectionResult(List<Visit> visits, Instant searchStart, Instant searchEnd, long durationInMillis) {
    }

    private record VisitMergingResult(List<Visit> inputVisits, List<ProcessedVisit> processedVisits,
                                      Instant searchStart, Instant searchEnd, long durationInMillis) {
    }

    private record TripDetectionResult(List<Trip> trips, long durationInMillis) {
    }
}
