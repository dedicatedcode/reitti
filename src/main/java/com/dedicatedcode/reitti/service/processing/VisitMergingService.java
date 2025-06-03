package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.SignificantPlaceRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
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
public class VisitMergingService {

    private static final Logger logger = LoggerFactory.getLogger(VisitMergingService.class);

    private static final int SRID = 4326;

    private final VisitRepository visitRepository;
    private final ProcessedVisitRepository processedVisitRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SignificantPlaceRepository significantPlaceRepository;
    private final GeometryFactory geometryFactory;
    @Value("${reitti.visit.merge-threshold-seconds:300}")
    private long mergeThresholdSeconds;
    @Value("${reitti.detect-trips-after-merging:true}")
    private boolean detectTripsAfterMerging;

    @Autowired
    public VisitMergingService(VisitRepository visitRepository,
                               ProcessedVisitRepository processedVisitRepository,
                               UserRepository userRepository, RabbitTemplate rabbitTemplate,
                               SignificantPlaceRepository significantPlaceRepository) {
        this.visitRepository = visitRepository;
        this.processedVisitRepository = processedVisitRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.significantPlaceRepository = significantPlaceRepository;
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    }

    @RabbitListener(queues = RabbitMQConfig.MERGE_VISIT_QUEUE)
    public void mergeVisits(MergeVisitEvent event) {
        Optional<User> user = userRepository.findByUsername(event.getUserName());
        if (user.isEmpty()) {
            logger.warn("User not found for userName: {}", event.getUserName());
            return;
        }
        processAndMergeVisits(user.get(), event.getStartTime(), event.getEndTime());
    }

    private List<ProcessedVisit> processAndMergeVisits(User user, Long startTime, Long endTime) {
        logger.info("Processing and merging visits for user: {}", user.getUsername());

        List<Visit> allVisits;

        // Get all unprocessed visits for the user
        if (startTime == null || endTime == null) {
            allVisits = this.visitRepository.findByUserAndProcessedFalse(user);
        } else {
            allVisits = this.visitRepository.findByUserAndStartTimeBetweenAndProcessedFalseOrderByStartTimeAsc(user, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime));
        }

        if (allVisits.isEmpty()) {
            logger.info("No visits found for user: {}", user.getUsername());
            return Collections.emptyList();
        }

        // Sort all visits chronologically
        allVisits.sort(Comparator.comparing(Visit::getStartTime));

        // Process all visits chronologically to avoid overlaps
        List<ProcessedVisit> processedVisits = mergeVisitsChronologically(user, allVisits);

        // Mark all visits as processed
        if (!allVisits.isEmpty()) {
            allVisits.forEach(visit -> visit.setProcessed(true));
            visitRepository.saveAll(allVisits);
            logger.info("Marked {} visits as processed for user: {}", allVisits.size(), user.getUsername());
        }

        logger.info("Processed {} visits into {} merged visits for user: {}",
                allVisits.size(), processedVisits.size(), user.getUsername());

        if (!processedVisits.isEmpty() && detectTripsAfterMerging) {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, new MergeVisitEvent(user.getUsername(), startTime, endTime));

        }
        return processedVisits;
    }

    private List<ProcessedVisit> mergeVisitsChronologically(User user, List<Visit> visits) {
        List<ProcessedVisit> result = new ArrayList<>();
        Map<Long, List<Visit>> placeVisitMap = new HashMap<>();

        if (visits.isEmpty()) {
            return result;
        }

        // First, group visits by nearby significant places
        for (Visit visit : visits) {
            List<SignificantPlace> nearbyPlaces = findNearbyPlaces(user, visit.getLatitude(), visit.getLongitude());
            
            if (nearbyPlaces.isEmpty()) {
                // Create a new place for this visit
                SignificantPlace newPlace = createSignificantPlace(user, visit);
                List<Visit> placeVisits = new ArrayList<>();
                placeVisits.add(visit);
                placeVisitMap.put(newPlace.getId(), placeVisits);
            } else {
                // Add to the closest existing place
                SignificantPlace closestPlace = findClosestPlace(visit, nearbyPlaces);
                placeVisitMap.computeIfAbsent(closestPlace.getId(), k -> new ArrayList<>()).add(visit);
            }
        }

        // Now process each place's visits separately
        for (Map.Entry<Long, List<Visit>> entry : placeVisitMap.entrySet()) {
            Long placeId = entry.getKey();
            List<Visit> placeVisits = entry.getValue();
            
            // Sort visits by start time
            placeVisits.sort(Comparator.comparing(Visit::getStartTime));
            
            // Get the place
            Optional<SignificantPlace> placeOpt = significantPlaceRepository.findById(placeId);
            if (placeOpt.isEmpty()) {
                logger.warn("Place not found for ID: {}", placeId);
                continue;
            }
            SignificantPlace place = placeOpt.get();
            
            // Merge consecutive visits at this place
            List<ProcessedVisit> placeProcessedVisits = mergeConsecutiveVisits(user, placeVisits, place);
            result.addAll(placeProcessedVisits);
        }

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
    
    private List<ProcessedVisit> mergeConsecutiveVisits(User user, List<Visit> visits, SignificantPlace place) {
        List<ProcessedVisit> result = new ArrayList<>();
        
        if (visits.isEmpty()) {
            return result;
        }
        
        // Start with the first visit
        Visit currentVisit = visits.getFirst();
        Instant currentStartTime = currentVisit.getStartTime();
        Instant currentEndTime = currentVisit.getEndTime();
        Set<Long> mergedVisitIds = new HashSet<>();
        mergedVisitIds.add(currentVisit.getId());

        for (int i = 1; i < visits.size(); i++) {
            Visit nextVisit = visits.get(i);

            // If within merge threshold, merge the visits
            if (Duration.between(currentEndTime, nextVisit.getStartTime()).getSeconds() <= mergeThresholdSeconds) {
                // Merge this visit with the current one
                currentEndTime = nextVisit.getEndTime().isAfter(currentEndTime) ?
                        nextVisit.getEndTime() : currentEndTime;
                mergedVisitIds.add(nextVisit.getId());
            } else {
                // Create a processed visit from the current merged set
                ProcessedVisit processedVisit = createProcessedVisit(user, place, currentStartTime,
                        currentEndTime, mergedVisitIds);
                result.add(processedVisit);

                // Start a new merged set with this visit
                currentStartTime = nextVisit.getStartTime();
                currentEndTime = nextVisit.getEndTime();
                mergedVisitIds = new HashSet<>();
                mergedVisitIds.add(nextVisit.getId());
            }
        }

        // Add the last merged set
        ProcessedVisit processedVisit = createProcessedVisit(user, place, currentStartTime,
                currentEndTime, mergedVisitIds);
        result.add(processedVisit);

        return result;
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
        // Check if a processed visit already exists for this time range and place
        List<ProcessedVisit> existingVisits = processedVisitRepository.findByUserAndPlaceAndTimeOverlap(
                user, place, startTime, endTime);

        if (!existingVisits.isEmpty()) {
            // Use the existing processed visit
            ProcessedVisit existingVisit = existingVisits.getFirst();
            logger.debug("Found existing processed visit for place ID {}", place.getId());

            // Update the existing visit if needed (e.g., extend time range)
            if (startTime.isBefore(existingVisit.getStartTime())) {
                existingVisit.setStartTime(startTime);
            }
            if (endTime.isAfter(existingVisit.getEndTime())) {
                existingVisit.setEndTime(endTime);
            }

            // Add original visit IDs to the existing one
            String existingIds = existingVisit.getOriginalVisitIds();
            String newIds = originalVisitIds.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));

            if (existingIds == null || existingIds.isEmpty()) {
                existingVisit.setOriginalVisitIds(newIds);
            } else {
                existingVisit.setOriginalVisitIds(existingIds + "," + newIds);
            }

            existingVisit.setMergedCount(existingVisit.getMergedCount() + originalVisitIds.size());
            return processedVisitRepository.save(existingVisit);
        } else {
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
