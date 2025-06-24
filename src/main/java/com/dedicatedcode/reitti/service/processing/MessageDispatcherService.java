package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.ProcessedVisitCreatedEvent;
import com.dedicatedcode.reitti.event.VisitUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageDispatcherService {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcherService.class);

    private final LocationDataIngestPipeline locationDataIngestPipeline;
    private final VisitDetectionService visitDetectionService;
    private final VisitMergingService visitMergingService;
    private final TripDetectionService tripDetectionService;

    @Autowired
    public MessageDispatcherService(LocationDataIngestPipeline locationDataIngestPipeline,
                                    VisitDetectionService visitDetectionService,
                                    VisitMergingService visitMergingService,
                                    TripDetectionService tripDetectionService) {
        this.locationDataIngestPipeline = locationDataIngestPipeline;
        this.visitDetectionService = visitDetectionService;
        this.visitMergingService = visitMergingService;
        this.tripDetectionService = tripDetectionService;
    }

    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "4-16")
    public void handleLocationData(LocationDataEvent event) {
        logger.debug("Dispatching LocationDataEvent for user: {}", event.getUsername());
        locationDataIngestPipeline.processLocationData(event);
    }

    @RabbitListener(queues = RabbitMQConfig.STAY_DETECTION_QUEUE, concurrency = "1-16")
    public void handleStayDetection(LocationProcessEvent event) {
        logger.debug("Dispatching LocationProcessEvent for user: {}", event.getUsername());
        visitDetectionService.detectStayPoints(event);
    }

    @RabbitListener(queues = RabbitMQConfig.MERGE_VISIT_QUEUE, concurrency = "1-16")
    public void handleVisitMerging(VisitUpdatedEvent event) {
        logger.debug("Dispatching VisitUpdatedEvent for user: {}", event.getUsername());
        visitMergingService.visitUpdated(event);
    }

    @RabbitListener(queues = RabbitMQConfig.DETECT_TRIP_QUEUE, concurrency = "1-16")
    public void handleTripDetection(ProcessedVisitCreatedEvent event) {
        logger.debug("Dispatching ProcessedVisitCreatedEvent for user: {}", event.getUsername());
        tripDetectionService.visitCreated(event);
    }
}
