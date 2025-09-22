package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PreviewRawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ProcessingPipelineTrigger {
    private static final Logger log = LoggerFactory.getLogger(ProcessingPipelineTrigger.class);
    private static final int BATCH_SIZE = 100;

    private final ImportStateHolder stateHolder;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final RabbitTemplate rabbitTemplate;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ProcessingPipelineTrigger(ImportStateHolder stateHolder,
                                     RawLocationPointJdbcService rawLocationPointJdbcService,
                                     PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                                     UserJdbcService userJdbcService,
                                     RabbitTemplate rabbitTemplate) {
        this.stateHolder = stateHolder;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(cron = "${reitti.process-data.schedule}")
    public void start() {
        if (isBusy()) return;

        isRunning.set(true);
        try {
            for (User user : userJdbcService.findAll()) {
                handleDataForUser(user, null);
            }
        } finally {
            isRunning.set(false);
        }
    }

    public void handle(TriggerProcessingEvent event) {
        if (isBusy()) return;

        isRunning.set(true);
        try {
            Optional<User> byUsername = this.userJdbcService.findByUsername(event.getUsername());
            if (byUsername.isPresent()) {
                handleDataForUser(byUsername.get(), event.getPreviewId());
            } else {
                log.warn("No user found for username: {}", event.getUsername());
            }
        } finally {
            isRunning.set(false);
        }
    }

    private void handleDataForUser(User user, String previewId) {
        int offset = 0;
        int totalProcessed = 0;
        
        while (true) {
            List<RawLocationPoint> currentBatch;
            if (previewId == null) {
                currentBatch = rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, BATCH_SIZE, offset);
            } else {
                currentBatch = previewRawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, previewId, BATCH_SIZE, offset);
            }
            
            if (currentBatch.isEmpty()) {
                break;
            }
            
            Instant earliest = currentBatch.getFirst().getTimestamp();
            Instant latest = currentBatch.getLast().getTimestamp();
            log.debug("Scheduling stay detection event for user [{}] and points between [{}] and [{}]", user.getId(), earliest, latest);

            currentBatch.forEach(RawLocationPoint::markProcessed);
            if (previewId != null) {
                previewRawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
            } else {
                rawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
            }
            
            this.rabbitTemplate
                    .convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                            RabbitMQConfig.STAY_DETECTION_ROUTING_KEY,
                            new LocationProcessEvent(user.getUsername(), earliest, latest, previewId));
            
            totalProcessed += currentBatch.size();
            offset += BATCH_SIZE;
        }
        
        log.debug("Processed [{}] unprocessed points for user [{}]", totalProcessed, user.getId());
    }

    private boolean isBusy() {
        if (isRunning.get()) {
            log.warn("Processing is already running, wil skip this run");
            return true;
        }

        if (stateHolder.isImportRunning()) {
            log.warn("Data Import is currently running, wil skip this run");
            return true;
        }
        return false;
    }

}
