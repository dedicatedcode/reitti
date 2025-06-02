package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.LocationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final VisitService visitService;
    private final RabbitMessageOperations rabbitTemplate;
    private final int tripVisitMergeTimeRange;

    @Autowired
    public LocationProcessingPipeline(
            UserRepository userRepository,
            LocationDataService locationDataService,
            StayPointDetectionService stayPointDetectionService,
            VisitService visitService,
            RabbitMessageOperations rabbitTemplate,
            @Value("${reitti.process-visits-trips.merge-time-range:1}") int tripVisitMergeTimeRange) {
        this.userRepository = userRepository;
        this.locationDataService = locationDataService;
        this.stayPointDetectionService = stayPointDetectionService;
        this.visitService = visitService;
        this.rabbitTemplate = rabbitTemplate;
        this.tripVisitMergeTimeRange = tripVisitMergeTimeRange;
    }

    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "4-16")
    public void processLocationData(LocationDataEvent event) {
        logger.debug("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userRepository.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername ());
            return;
        }

        User user = userOpt.get();

        // Step 1: Save raw location points (with duplicate checking)
        List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, event.getPoints());

        if (savedPoints.isEmpty()) {
            logger.debug("No new points to process for user {}", user.getUsername());
            return;
        }

        logger.info("Saved {} new location points for user {}", savedPoints.size(), user.getUsername());

        // Step 2: Detect stay points from the new data
        List<StayPoint> stayPoints = stayPointDetectionService.detectStayPoints(user, savedPoints);

        if (!stayPoints.isEmpty()) {
            logger.trace("Detected {} stay points", stayPoints.size());
            visitService.processStayPoints(user, stayPoints);

            Instant startTime = savedPoints.stream().map(RawLocationPoint::getTimestamp).min(Instant::compareTo).orElse(Instant.now());
            Instant endTime = savedPoints.stream().map(RawLocationPoint::getTimestamp).max(Instant::compareTo).orElse(Instant.now());
            long searchStart = startTime.minus(tripVisitMergeTimeRange, ChronoUnit.DAYS).toEpochMilli();
            long searchEnd = endTime.plus(tripVisitMergeTimeRange, ChronoUnit.DAYS).toEpochMilli();
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_VISIT_ROUTING_KEY, new MergeVisitEvent(user.getUsername(), searchStart, searchEnd));
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.DETECT_TRIP_ROUTING_KEY, new MergeVisitEvent(user.getUsername(), searchStart, searchEnd));
        }

        logger.info("Completed processing pipeline for user {}", user.getUsername());
    }
}
