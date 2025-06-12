package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.ProcessedVisitCreatedEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.event.VisitCreatedEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VisitMergingService {

    private static final Logger logger = LoggerFactory.getLogger(VisitMergingService.class);

    private final VisitRepository visitRepository;
    private final ProcessedVisitRepository processedVisitRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SignificantPlaceRepository significantPlaceRepository;
    private final RawLocationPointRepository rawLocationPointRepository;
    private final GeometryFactory geometryFactory;
    @Value("${reitti.visit.merge-threshold-seconds:300}")
    private long mergeThresholdSeconds;
    @Value("${reitti.visit.merge-threshold-meters:100}")
    private long mergeThresholdMeters;

    @Autowired
    public VisitMergingService(VisitRepository visitRepository,
                               ProcessedVisitRepository processedVisitRepository,
                               UserRepository userRepository,
                               RabbitTemplate rabbitTemplate,
                               SignificantPlaceRepository significantPlaceRepository,
                               RawLocationPointRepository rawLocationPointRepository,
                               GeometryFactory geometryFactory) {
        this.visitRepository = visitRepository;
        this.processedVisitRepository = processedVisitRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.significantPlaceRepository = significantPlaceRepository;
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.geometryFactory = geometryFactory;
    }

    @RabbitListener(queues = RabbitMQConfig.MERGE_VISIT_QUEUE)
    public void visitCreated(VisitCreatedEvent event) {
        try {
            handleEvent(event.getUsername(), event.getVisitId());
        } catch (Exception e) {
            logger.error("Could not handle event: {}", event);
        }
    }


    @RabbitListener(queues = RabbitMQConfig.MERGE_VISIT_QUEUE)
    public void visitUpdated(VisitUpdatedEvent event) {
        try {
            handleEvent(event.getUsername(), event.getVisitId());
        } catch (Exception e) {
            logger.debug("Could not handle event: {}", event);
        }
    }

    private void handleEvent(String username, long visitId) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            logger.warn("User not found for userName: {}", username);
            return;
        }

        Optional<Visit> visitOpt = this.visitRepository.findById(visitId);
        if (visitOpt.isEmpty()) {
            logger.debug("Visit not found for visitId: {}", visitId);
            return;
        }


        Visit visit = visitOpt.get();
        Instant searchStart = visit.getStartTime().minus(1, ChronoUnit.DAYS);
        Instant searchEnd = visit.getEndTime().plus(1, ChronoUnit.DAYS);
        processAndMergeVisits(user.get(), searchStart.toEpochMilli(), searchEnd.toEpochMilli());
    }

    private void processAndMergeVisits(User user, Long start, Long end) {
        logger.info("Processing and merging visits for user: {}", user.getUsername());


        Instant searchStart = Instant.ofEpochMilli(start);
        Instant searchEnd = Instant.ofEpochMilli(end);
        List<ProcessedVisit> visitsAtStart = this.processedVisitRepository.findByUserAndStartTimeBetweenOrderByStartTimeAsc(user, searchStart, searchEnd);
        List<ProcessedVisit> visitsAtEnd = this.processedVisitRepository.findByUserAndEndTimeBetweenOrderByStartTimeAsc(user, searchStart, searchEnd);

        if (!visitsAtStart.isEmpty()) {
            searchStart = visitsAtStart.getFirst().getStartTime().minus(1, ChronoUnit.DAYS);
        }
        if (!visitsAtEnd.isEmpty()) {
            searchEnd = visitsAtEnd.getLast().getEndTime().plus(1, ChronoUnit.DAYS);
        }
        logger.debug("found {} processed visits at start and {} processed visits at end, will extend search window to {} and {}", visitsAtStart.size(), visitsAtEnd.size(), searchStart, searchEnd);


        List<ProcessedVisit> allProcessedVisitsInRange = this.processedVisitRepository.findByUserAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(user, searchStart, searchEnd);
        this.processedVisitRepository.deleteAll(allProcessedVisitsInRange);
        List<Visit> allVisits = this.visitRepository.findByUserAndStartTimeBetweenOrderByStartTimeAsc(user, searchStart, searchEnd);
        if (allVisits.isEmpty()) {
            logger.info("No visits found for user: {}", user.getUsername());
            return;
        }

        // Sort all visits chronologically
        allVisits.sort(Comparator.comparing(Visit::getStartTime));

        // Process all visits chronologically to avoid overlaps
        List<ProcessedVisit> processedVisits = mergeVisitsChronologically(user, allVisits);

        processedVisits.stream()
                .sorted(Comparator.comparing(ProcessedVisit::getStartTime))
                .forEach(processedVisit -> this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, new ProcessedVisitCreatedEvent(user.getUsername(), processedVisit.getId())));
        logger.debug("Processed {} visits into {} merged visits for user: {}",
                allVisits.size(), processedVisits.size(), user.getUsername());
    }

    private List<ProcessedVisit> mergeVisitsChronologically(User user, List<Visit> visits) {
        List<ProcessedVisit> result = new ArrayList<>();

        if (visits.isEmpty()) {
            return result;
        }

        // Sort visits chronologically
        visits.sort(Comparator.comparing(Visit::getStartTime));

        // Start with the first visit
        Visit currentVisit = visits.getFirst();
        Instant currentStartTime = currentVisit.getStartTime();
        Instant currentEndTime = currentVisit.getEndTime();
        Set<Long> mergedVisitIds = new HashSet<>();
        mergedVisitIds.add(currentVisit.getId());

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
            boolean withinTimeThreshold = Duration.between(currentEndTime, nextVisit.getStartTime()).getSeconds() <= mergeThresholdSeconds;

            boolean shouldMergeWithNextVisit = samePlace && withinTimeThreshold;

            //fluke detections
            if (samePlace && !withinTimeThreshold) {
                List<RawLocationPoint> pointsBetweenVisits = this.rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(user, currentEndTime, nextVisit.getStartTime());
                if (pointsBetweenVisits.size() > 2) {
                    double travelledDistanceInMeters = GeoUtils.calculateTripDistance(pointsBetweenVisits);
                    shouldMergeWithNextVisit = travelledDistanceInMeters < mergeThresholdMeters;
                } else {
                    logger.debug("Skipping creation of new visit because there are no points tracked between {} and {}", currentEndTime, nextVisit.getStartTime());
                    shouldMergeWithNextVisit = true;
                }
            }

            if (shouldMergeWithNextVisit) {
                // Merge this visit with the current one
                currentEndTime = nextVisit.getEndTime().isAfter(currentEndTime) ?
                        nextVisit.getEndTime() : currentEndTime;
                mergedVisitIds.add(nextVisit.getId());
            } else {
                // Create a processed visit from the current merged set
                ProcessedVisit processedVisit = createProcessedVisit(user, currentPlace, currentStartTime,
                        currentEndTime, mergedVisitIds);
                result.add(processedVisit);

                // Start a new merged set with this visit
                currentStartTime = nextVisit.getStartTime();
                currentEndTime = nextVisit.getEndTime();
                currentPlace = nextPlace;
                mergedVisitIds = new HashSet<>();
                mergedVisitIds.add(nextVisit.getId());
            }
        }

        // Add the last merged set
        ProcessedVisit processedVisit = createProcessedVisit(user, currentPlace, currentStartTime,
                currentEndTime, mergedVisitIds);

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
        return significantPlaceRepository.findNearbyPlaces(user.getId(), point, GeoUtils.metersToDegreesAtPosition(50, latitude)[0]);
    }

    private SignificantPlace createSignificantPlace(User user, Visit visit) {
        // Create a point geometry
        Point point = geometryFactory.createPoint(new Coordinate(visit.getLongitude(), visit.getLatitude()));

        SignificantPlace significantPlace = new SignificantPlace(
                user,
                null, // name will be set later through reverse geocoding or user input
                null, // address will be set later through reverse geocoding
                visit.getLatitude(),
                visit.getLongitude(),
                point,
                null
        );
        this.significantPlaceRepository.saveAndFlush(significantPlace);
        publishSignificantPlaceCreatedEvent(significantPlace);
        return significantPlace;
    }

    private ProcessedVisit createProcessedVisit(User user,
                                                SignificantPlace place,
                                                Instant startTime, Instant endTime,
                                                Set<Long> originalVisitIds) {
        // Create a new processed visit
        ProcessedVisit processedVisit = new ProcessedVisit(user, place, startTime, endTime);
        processedVisit.setMergedCount(originalVisitIds.size());

        // Store original visit IDs as comma-separated string
        String visitIdsStr = originalVisitIds.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        processedVisit.setOriginalVisitIds(visitIdsStr);

        return processedVisitRepository.save(processedVisit);
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
