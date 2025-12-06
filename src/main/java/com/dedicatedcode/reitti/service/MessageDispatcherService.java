package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageDispatcherService {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcherService.class);

    private final ReverseGeocodingListener reverseGeocodingListener;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final UserSseEmitterService userSseEmitterService;
    private final UserJdbcService  userJdbcService;
    private final VisitDetectionPreviewService visitDetectionPreviewService;

    @Autowired
    public MessageDispatcherService(ReverseGeocodingListener reverseGeocodingListener,
                                    ProcessingPipelineTrigger processingPipelineTrigger,
                                    UserSseEmitterService userSseEmitterService,
                                    UserJdbcService userJdbcService,
                                    VisitDetectionPreviewService visitDetectionPreviewService) {
        this.reverseGeocodingListener = reverseGeocodingListener;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.userSseEmitterService = userSseEmitterService;
        this.userJdbcService = userJdbcService;
        this.visitDetectionPreviewService = visitDetectionPreviewService;
    }

    @RabbitListener(queues = RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Dispatching SignificantPlaceCreatedEvent: {}", event);
        reverseGeocodingListener.handleSignificantPlaceCreated(event);
        visitDetectionPreviewService.updatePreviewStatus(event.previewId());
    }

    @RabbitListener(queues = RabbitMQConfig.USER_EVENT_QUEUE)
    public void handleUserNotificationEvent(SSEEvent event) {
        logger.trace("Dispatching SSEEvent: {}", event);
        this.userJdbcService.findById(event.getUserId()).ifPresentOrElse(user -> this.userSseEmitterService.sendEventToUser(user, event), () -> logger.warn("User not found for user: {}", event.getUserId()));
    }

    @RabbitListener(queues = RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_QUEUE, concurrency = "${reitti.events.concurrency}")
    public void handleTriggerProcessingEvent(TriggerProcessingEvent event) {
        logger.info("Dispatching TriggerProcessingEvent {}", event);
        processingPipelineTrigger.handle(event, false);
        visitDetectionPreviewService.updatePreviewStatus(event.getPreviewId());
    }
}
