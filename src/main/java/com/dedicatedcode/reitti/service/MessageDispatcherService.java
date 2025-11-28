package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.*;
import com.dedicatedcode.reitti.repository.UserJdbcService;
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
    private final UnifiedLocationProcessingService unifiedLocationProcessingService;
    private final ReverseGeocodingListener reverseGeocodingListener;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final UserSseEmitterService userSseEmitterService;
    private final UserJdbcService  userJdbcService;
    private final VisitDetectionPreviewService visitDetectionPreviewService;

    @Autowired
    public MessageDispatcherService(LocationDataIngestPipeline locationDataIngestPipeline,
                                    UnifiedLocationProcessingService unifiedLocationProcessingService,
                                    ReverseGeocodingListener reverseGeocodingListener,
                                    ProcessingPipelineTrigger processingPipelineTrigger,
                                    UserSseEmitterService userSseEmitterService,
                                    UserJdbcService userJdbcService,
                                    VisitDetectionPreviewService visitDetectionPreviewService) {
        this.locationDataIngestPipeline = locationDataIngestPipeline;
        this.unifiedLocationProcessingService = unifiedLocationProcessingService;
        this.reverseGeocodingListener = reverseGeocodingListener;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.userSseEmitterService = userSseEmitterService;
        this.userJdbcService = userJdbcService;
        this.visitDetectionPreviewService = visitDetectionPreviewService;
    }

    @RabbitListener(queues = RabbitMQConfig.STAY_DETECTION_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleStayDetection(LocationProcessEvent event) {
        logger.info("4 - Dispatching LocationProcessEvent: {}", event);
        unifiedLocationProcessingService.processLocationEvent(event);
        visitDetectionPreviewService.updatePreviewStatus(event.getPreviewId());
    }

    @RabbitListener(queues = RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Dispatching SignificantPlaceCreatedEvent: {}", event);
        reverseGeocodingListener.handleSignificantPlaceCreated(event);
        visitDetectionPreviewService.updatePreviewStatus(event.previewId());
    }

    @RabbitListener(queues = RabbitMQConfig.USER_EVENT_QUEUE)
    public void handleUserNotificationEvent(SSEEvent event) {
        logger.debug("Dispatching SSEEvent: {}", event);
        this.userJdbcService.findById(event.getUserId()).ifPresentOrElse(user -> this.userSseEmitterService.sendEventToUser(user, event), () -> logger.warn("User not found for user: {}", event.getUserId()));
    }

    @RabbitListener(queues = RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleTriggerProcessingEvent(TriggerProcessingEvent event) {
        logger.info("3 - Dispatching TriggerProcessingEvent {}", event);
        processingPipelineTrigger.handle(event);
        visitDetectionPreviewService.updatePreviewStatus(event.getPreviewId());
    }
}
