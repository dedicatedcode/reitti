package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.LocationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class LocationProcessingPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationProcessingPipeline.class);

    private final UserRepository userRepository;
    private final LocationDataService locationDataService;
    private final StayPointDetectionService stayPointDetectionService;
    private final SignificantPlaceService significantPlaceService;
    private final TripDetectionService tripDetectionService;
    private final TripMergingService tripMergingService;
    private final VisitMergingService visitMergingService;
    private final RabbitMessageOperations rabbitTemplate;

    @Autowired
    public LocationProcessingPipeline(
            UserRepository userRepository,
            LocationDataService locationDataService,
            StayPointDetectionService stayPointDetectionService,
            SignificantPlaceService significantPlaceService,
            TripDetectionService tripDetectionService,
            TripMergingService tripMergingService,
            VisitMergingService visitMergingService,
            RabbitMessageOperations rabbitTemplate) {
        this.userRepository = userRepository;
        this.locationDataService = locationDataService;
        this.stayPointDetectionService = stayPointDetectionService;
        this.significantPlaceService = significantPlaceService;
        this.tripDetectionService = tripDetectionService;
        this.tripMergingService = tripMergingService;
        this.visitMergingService = visitMergingService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void processLocationData(LocationDataEvent event) {
        logger.info("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userRepository.findById(event.getUserId());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for ID: {}", event.getUserId());
            return;
        }

        Instant minTime = event.getPoints().stream().map(LocationDataRequest.LocationPoint::getTimestamp).map(Instant::parse).min(Instant::compareTo).orElse(null);
        Instant maxTime = event.getPoints().stream().map(LocationDataRequest.LocationPoint::getTimestamp).map(Instant::parse).max(Instant::compareTo).orElse(null);
        User user = userOpt.get();

        // Step 1: Save raw location points (with duplicate checking)
        List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, event.getPoints());

        if (savedPoints.isEmpty()) {
            logger.info("No new points to process for user {}", user.getUsername());
            return;
        }

        logger.info("Saved {} new location points for user {}", savedPoints.size(), user.getUsername());

        // Step 2: Detect stay points from the new data
        List<StayPoint> stayPoints = stayPointDetectionService.detectStayPoints(user, savedPoints);

        if (!stayPoints.isEmpty()) {
            logger.info("Detected {} stay points", stayPoints.size());

            // Step 3: Update significant places based on stay points
            List<SignificantPlace> updatedPlaces = significantPlaceService.processStayPoints(user, stayPoints);
            logger.info("Updated {} significant places", updatedPlaces.size());

            long start = System.nanoTime();
            // Step 4: update Processed Visits

            visitMergingService.processAndMergeVisits(user);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, user.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, user.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_TRIP_ROUTING_KEY, user.getId());

        }

        logger.info("Completed processing pipeline for user {}", user.getUsername());
    }
}
