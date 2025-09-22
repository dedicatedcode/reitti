package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.ProcessedVisitCreatedEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class VisitMergingService {

    private static final Logger logger = LoggerFactory.getLogger(VisitMergingService.class);

    private final VisitJdbcService visitJdbcService;
    private final PreviewVisitJdbcService previewVisitJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService;
    private final UserJdbcService userJdbcService;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final GeometryFactory geometryFactory;
    private final RabbitTemplate rabbitTemplate;
    private final UserNotificationService userNotificationService;
    private final GeoLocationTimezoneService timezoneService;
    private final VisitDetectionParametersService visitDetectionParametersService;

    @Autowired
    public VisitMergingService(VisitJdbcService visitJdbcService,
                               PreviewVisitJdbcService previewVisitJdbcService,
                               ProcessedVisitJdbcService processedVisitJdbcService,
                               PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService,
                               UserJdbcService userJdbcService,
                               RabbitTemplate rabbitTemplate,
                               SignificantPlaceJdbcService significantPlaceJdbcService,
                               RawLocationPointJdbcService rawLocationPointJdbcService,
                               PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                               GeometryFactory geometryFactory,
                               UserNotificationService userNotificationService,
                               GeoLocationTimezoneService timezoneService,
                               VisitDetectionParametersService visitDetectionParametersService) {
        this.visitJdbcService = visitJdbcService;
        this.previewVisitJdbcService = previewVisitJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.previewProcessedVisitJdbcService = previewProcessedVisitJdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.geometryFactory = geometryFactory;
        this.userNotificationService = userNotificationService;
        this.timezoneService = timezoneService;
        this.visitDetectionParametersService = visitDetectionParametersService;
    }

    public void visitUpdated(VisitUpdatedEvent event) {
        String username = event.getUsername();
        handleEvent(username, event.getVisitIds(), event.getPreviewId());
    }

    private void handleEvent(String username, List<Long> visitIds, String previewId) {
        Optional<User> user = userJdbcService.findByUsername(username);
        if (user.isEmpty()) {
            logger.warn("User not found for userName: {}", username);
            return;
        }
        List<Visit> visits = previewId == null ? this.visitJdbcService.findAllByIds(visitIds) : this.previewVisitJdbcService.findAllByIds(visitIds);
        if (visits.isEmpty()) {
            logger.debug("Visit not found for visitId: [{}]", visitIds);
            return;
        }

        Instant firstVisitTime = visits.stream().map(Visit::getStartTime).min(Comparator.naturalOrder()).orElseThrow();
        DetectionParameter.VisitMerging mergeConfiguration;
        if (previewId == null) {
            mergeConfiguration = this.visitDetectionParametersService.getCurrentConfiguration(user.get(), firstVisitTime).getVisitMerging();
        } else{
            mergeConfiguration = this.visitDetectionParametersService.getCurrentConfiguration(user.get(), previewId).getVisitMerging();
        }
        Instant searchStart = visits.stream().min(Comparator.comparing(Visit::getStartTime)).map(Visit::getStartTime).map(instant -> instant.minus(mergeConfiguration.getSearchDurationInHours(), ChronoUnit.HOURS)).orElseThrow();
        Instant searchEnd = visits.stream().max(Comparator.comparing(Visit::getEndTime)).map(Visit::getEndTime).map(instant -> instant.plus(mergeConfiguration.getSearchDurationInHours(), ChronoUnit.HOURS)).orElseThrow();

        processAndMergeVisits(user.get(), previewId, searchStart, searchEnd, mergeConfiguration);
    }

    private void processAndMergeVisits(User user, String previewId, Instant searchStart, Instant searchEnd, DetectionParameter.VisitMerging mergeConfiguration) {
        logger.info("Processing and merging visits for user: [{}] between [{}] and [{}]", user.getUsername(), searchStart, searchEnd);
        List<ProcessedVisit> allProcessedVisitsInRange;
        if (previewId == null) {

            allProcessedVisitsInRange = this.processedVisitJdbcService.findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, searchEnd, searchStart);
            logger.debug("found [{}] processed visits in range [{}] to [{}]", allProcessedVisitsInRange.size(), searchStart, searchEnd);
            this.processedVisitJdbcService.deleteAll(allProcessedVisitsInRange);
        } else {
            allProcessedVisitsInRange = this.previewProcessedVisitJdbcService.findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(user, previewId, searchEnd, searchStart);
            logger.debug("found [{}] processed preview visits in range [{}] to [{}]", allProcessedVisitsInRange.size(), searchStart, searchEnd);
            this.previewProcessedVisitJdbcService.deleteAll(allProcessedVisitsInRange);

        }
        if (!allProcessedVisitsInRange.isEmpty()) {
            if (allProcessedVisitsInRange.getFirst().getStartTime().isBefore(searchStart)) {
                searchStart = allProcessedVisitsInRange.getFirst().getStartTime();
            }
            if (allProcessedVisitsInRange.getLast().getEndTime().isAfter(searchEnd)) {
                searchEnd = allProcessedVisitsInRange.getLast().getEndTime();
            }
        }

        logger.debug("After finding [{}] existing processed visits, expanding search range for Visits between [{}] and [{}]", allProcessedVisitsInRange.size(), searchStart, searchEnd);

        List<Visit> allVisits;
        if (previewId == null) {
            allVisits = this.visitJdbcService.findByUserAndTimeAfterAndStartTimeBefore(user, searchStart, searchEnd);
        } else {
            allVisits = this.previewVisitJdbcService.findByUserAndTimeAfterAndStartTimeBefore(user, previewId, searchStart, searchEnd);
        }
        if (allVisits.isEmpty()) {
            logger.info("No visits found for user: {}", user.getUsername());
            return;
        }

        // Process all visits chronologically to avoid overlaps
        List<ProcessedVisit> processedVisits = mergeVisitsChronologically(user, previewId, allVisits, mergeConfiguration);

        if (previewId == null) {
            processedVisitJdbcService.bulkInsert(user, processedVisits)
                    .stream()
                    .sorted(Comparator.comparing(ProcessedVisit::getStartTime))
                    .forEach(processedVisit -> this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, new ProcessedVisitCreatedEvent(user.getUsername(), processedVisit.getId(), null)));
        } else {
            previewProcessedVisitJdbcService.bulkInsert(user, previewId, processedVisits)
                    .stream()
                    .sorted(Comparator.comparing(ProcessedVisit::getStartTime))
                    .forEach(processedVisit -> this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, new ProcessedVisitCreatedEvent(user.getUsername(), processedVisit.getId(), previewId)));
        }
        logger.debug("Processed [{}] visits into [{}] merged visits for user: [{}]",
                allVisits.size(), processedVisits.size(), user.getUsername());
        if (previewId == null) {
            this.userNotificationService.newVisits(user, processedVisits);
        }
    }


    private List<ProcessedVisit> mergeVisitsChronologically(User user, String previewId, List<Visit> visits, DetectionParameter.VisitMerging mergeConfiguration) {
        if (logger.isDebugEnabled()) {
            logger.debug("Merging [{}] visits between [{}] and [{}]", visits.size(), visits.getFirst().getStartTime(), visits.getLast().getEndTime());
        }
        List<ProcessedVisit> result = new ArrayList<>();

        if (visits.isEmpty()) {
            return result;
        }

        // Start with the first visit
        Visit currentVisit = visits.getFirst();
        Instant currentStartTime = currentVisit.getStartTime();
        Instant currentEndTime = currentVisit.getEndTime();

        // Find or create a place for the first visit
        List<SignificantPlace> nearbyPlaces = findNearbyPlaces(user, currentVisit.getLatitude(), currentVisit.getLongitude());
        SignificantPlace currentPlace = nearbyPlaces.isEmpty() ?
                createSignificantPlace(user, currentVisit) :
                findClosestPlace(currentVisit, nearbyPlaces);

        for (int i = 1; i < visits.size(); i++) {
            Visit nextVisit = visits.get(i);

            // Find nearby places for the next visit
            nearbyPlaces = findNearbyPlaces(user, nextVisit.getLatitude(), nextVisit.getLongitude());
            SignificantPlace nextPlace = nearbyPlaces.isEmpty() ?
                    createSignificantPlace(user, nextVisit) :
                    findClosestPlace(nextVisit, nearbyPlaces);

            // Check if the next visit is at the same place and within the time threshold
            boolean samePlace = nextPlace.getId().equals(currentPlace.getId());
            boolean withinTimeThreshold = Duration.between(currentEndTime, nextVisit.getStartTime()).getSeconds() <= mergeConfiguration.getMaxMergeTimeBetweenSameVisits();

            boolean shouldMergeWithNextVisit = samePlace && withinTimeThreshold;

            //fluke detections
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
                // Merge this visit with the current one
                currentEndTime = nextVisit.getEndTime().isAfter(currentEndTime) ?
                        nextVisit.getEndTime() : currentEndTime;
            } else {
                // Create a processed visit from the current merged set
                ProcessedVisit processedVisit = createProcessedVisit(currentPlace, currentStartTime, currentEndTime);
                result.add(processedVisit);

                // Start a new merged set with this visit
                currentStartTime = nextVisit.getStartTime();
                currentEndTime = nextVisit.getEndTime();
                currentPlace = nextPlace;
            }
        }

        // Add the last merged set
        ProcessedVisit processedVisit = createProcessedVisit(currentPlace, currentStartTime, currentEndTime);

        result.add(processedVisit);

        return result;
    }

    private SignificantPlace findClosestPlace(Visit visit, List<SignificantPlace> places) {
        return places.stream()
                .min(Comparator.comparingDouble(place ->
                        GeoUtils.distanceInMeters(
                                visit.getLatitude(), visit.getLongitude(),
                                place.getLatitudeCentroid(), place.getLongitudeCentroid())))
                .orElseThrow(() -> new IllegalStateException("No places found"));
    }


    private List<SignificantPlace> findNearbyPlaces(User user, double latitude, double longitude) {
        // Create a point geometry
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        // Find places within the merge distance
        return significantPlaceJdbcService.findNearbyPlaces(user.getId(), point, GeoUtils.metersToDegreesAtPosition(50, latitude)[0]);
    }

    private SignificantPlace createSignificantPlace(User user, Visit visit) {
        SignificantPlace significantPlace = SignificantPlace.create(visit.getLatitude(), visit.getLongitude());
        Optional<ZoneId> timezone = this.timezoneService.getTimezone(significantPlace);
        if (timezone.isPresent()) {
            significantPlace = significantPlace.withTimezone(timezone.get());
        }
        significantPlace = this.significantPlaceJdbcService.create(user, significantPlace);
        publishSignificantPlaceCreatedEvent(significantPlace);
        return significantPlace;
    }

    private ProcessedVisit createProcessedVisit(SignificantPlace place, Instant startTime, Instant endTime) {
        logger.debug("Creating processed visit for place [{}] between [{}] and [{}]", place.getId(), startTime, endTime);
        return new ProcessedVisit(place, startTime, endTime, endTime.getEpochSecond() - startTime.getEpochSecond());
    }

    private void publishSignificantPlaceCreatedEvent(SignificantPlace place) {
        SignificantPlaceCreatedEvent event = new SignificantPlaceCreatedEvent(
                place.getId(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.SIGNIFICANT_PLACE_ROUTING_KEY, event);
        logger.info("Published SignificantPlaceCreatedEvent for place ID: {}", place.getId());
    }
}
