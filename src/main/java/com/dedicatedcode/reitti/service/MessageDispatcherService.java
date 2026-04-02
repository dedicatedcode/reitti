package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SignificantPlaceCreatedEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.geocoding.ReverseGeocodingListener;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import com.dedicatedcode.reitti.service.queue.RedisQueueListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageDispatcherService {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcherService.class);
    public static final String PLACE_CREATED_QUEUE = "reitti.place.created.v2";
    public static final String USER_EVENT_QUEUE = "reitti.user.events.v2";
    public static final String TRIGGER_PROCESSING_QUEUE = "reitti.processing.v2";

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

    @RedisQueueListener(value = PLACE_CREATED_QUEUE, deadLetterQueue = "reitti.dlq.v2")
    public void handleSignificantPlaceCreated(SignificantPlaceCreatedEvent event) {
        logger.info("Dispatching SignificantPlaceCreatedEvent: {}", event);
        reverseGeocodingListener.handleSignificantPlaceCreated(event);
        visitDetectionPreviewService.updatePreviewStatus(event.previewId());
    }

    @RedisQueueListener(value = USER_EVENT_QUEUE)
    public void handleUserNotificationEvent(SSEEvent event) {
        logger.trace("Dispatching SSEEvent: {}", event);
        this.userJdbcService.findById(event.getUserId()).ifPresentOrElse(user -> this.userSseEmitterService.sendEventToUser(user, event), () -> logger.warn("User not found for user: {}", event.getUserId()));
    }

    @RedisQueueListener(value = TRIGGER_PROCESSING_QUEUE)
    public void handleTriggerProcessingEvent(TriggerProcessingEvent event) {
        logger.info("Dispatching TriggerProcessingEvent {}", event);
        processingPipelineTrigger.handle(event, false);
        visitDetectionPreviewService.updatePreviewStatus(event.getPreviewId());
    }
}
