package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
            traceOutput.append("INPUT VISITS (").append(mergingResult.inputVisits.size()).append(") - took [").append(detectionResult.durationMillis).append("]ms:\n");
            traceOutput.append("┌─────────────────────┬─────────────────────┬───────────┬─────────────┬─────────────┬──────────────────────────────────────────────────────────────────────┐\n");
            traceOutput.append("│ Start Time          │ End Time            │ Duration  │ Latitude    │ Longitude   │ Google Maps Link                                                     │\n");
            traceOutput.append("├─────────────────────┼─────────────────────┼───────────┼─────────────┼─────────────┼──────────────────────────────────────────────────────────────────────┤\n");
            for (Visit visit : mergingResult.inputVisits) {
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
            traceOutput.append("PROCESSED VISITS (").append(mergingResult.processedVisits.size()).append(") - took [").append(mergingResult.durationMillis).append("]ms:\n");
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
            traceOutput.append("TRIPS (").append(tripResult.trips.size()).append(") - took [").append(tripResult.durationMillis).append("]ms:\n");
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

        // Expand the window based on found processed visits
        if (!existingProcessedVisits.isEmpty()) {
            if (existingProcessedVisits.getFirst().getStartTime().isBefore(windowStart)) {
                windowStart = existingProcessedVisits.getFirst().getStartTime();
            }
            if (existingProcessedVisits.getLast().getEndTime().isAfter(windowEnd)) {
                windowEnd = existingProcessedVisits.getLast().getEndTime();
            }
        }
        List<Visit> visits;
        if (previewId == null) {
            visits = rawLocationPointJdbcService.findVisitsInTimerangeForUser(
                    user, windowStart, windowEnd, detectionParams.getMinimumStayTimeInSeconds(), currentConfiguration.getVisitMerging().getMinDistanceBetweenVisits());
        } else {
            visits = previewRawLocationPointJdbcService.findVisitsInTimerangeForUser(
                    user, previewId, windowStart, windowEnd, detectionParams.getMinimumStayTimeInSeconds(), currentConfiguration.getVisitMerging().getMinDistanceBetweenVisits());
        }

        //for every visit, get the closest rawlocation point in the middle of the visit and put the visit there. AI!
        List<Visit> list = visits.stream().sorted(Comparator.comparing(Visit::getStartTime))
                .filter(v -> v.getDurationSeconds() >= detectionParams.getMinimumStayTimeInSeconds())
                .toList();
        return new VisitDetectionResult(list, windowStart, windowEnd, System.currentTimeMillis() - start);
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
                } else  {
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

    private record VisitDetectionResult(List<Visit> visits, Instant searchStart, Instant searchEnd, long durationMillis) {
    }

    private record VisitMergingResult(List<Visit> inputVisits, List<ProcessedVisit> processedVisits,
                                      Instant searchStart, Instant searchEnd, long durationMillis) {
    }

    private record TripDetectionResult(List<Trip> trips, long durationMillis) {
    }
}
