package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.*;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.processing.*;
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
    private final ReverseGeocodingListener reverseGeocodingListener;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final UserSseEmitterService userSseEmitterService;

    @Autowired
    public MessageDispatcherService(LocationDataIngestPipeline locationDataIngestPipeline,
                                    VisitDetectionService visitDetectionService,
                                    VisitMergingService visitMergingService,
                                    TripDetectionService tripDetectionService,
                                    ReverseGeocodingListener reverseGeocodingListener,
                                    ProcessingPipelineTrigger processingPipelineTrigger,
                                    UserSseEmitterService userSseEmitterService) {
        this.locationDataIngestPipeline = locationDataIngestPipeline;
        this.visitDetectionService = visitDetectionService;
        this.visitMergingService = visitMergingService;
        this.tripDetectionService = tripDetectionService;
        this.reverseGeocodingListener = reverseGeocodingListener;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.userSseEmitterService = userSseEmitterService;
    }

    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleLocationData(LocationDataEvent event) {
        logger.debug("Dispatching LocationDataEvent for user: {}", event.getUsername());
        locationDataIngestPipeline.processLocationData(event);
    }

    @RabbitListener(queues = RabbitMQConfig.STAY_DETECTION_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleStayDetection(LocationProcessEvent event) {
        logger.debug("Dispatching LocationProcessEvent for user: {}", event.getUsername());
        visitDetectionService.detectStayPoints(event);
    }

    @RabbitListener(queues = RabbitMQConfig.MERGE_VISIT_QUEUE, concurrency = "1")
    public void handleVisitMerging(VisitUpdatedEvent event) {
        logger.debug("Dispatching VisitUpdatedEvent for user: {}", event.getUsername());
        visitMergingService.visitUpdated(event);
    }

    @RabbitListener(queues = RabbitMQConfig.DETECT_TRIP_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleTripDetection(ProcessedVisitCreatedEvent event) {
        logger.debug("Dispatching ProcessedVisitCreatedEvent for user: {}", event.getUsername());
        tripDetectionService.visitCreated(event);
    }

    @RabbitListener(queues = RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.debug("Dispatching SignificantPlaceCreatedEvent for place: {}", event.getPlaceId());
        reverseGeocodingListener.handleSignificantPlaceCreated(event);
    }

    @RabbitListener(queues = RabbitMQConfig.USER_EVENT_QUEUE)
    public void handleUserNotificationEvent(SSEEvent event) {
        logger.debug("Dispatching SSEEvent for user: {}", event.getUserId());
        this.userSseEmitterService.sendEventToUser(event.getUserId(), event);
    }

    @RabbitListener(queues = RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleTriggerProcessingEvent(TriggerProcessingEvent event) {
        logger.debug("Dispatching TriggerProcessingEvent for user: {}", event.getUsername());
        processingPipelineTrigger.handle(event);
    }
}
