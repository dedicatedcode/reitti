package com.dedicatedcode.reitti.service.processing;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ProcessingPipelineTrigger {
    private static final Logger log = LoggerFactory.getLogger(ProcessingPipelineTrigger.class);

    private final ImportStateHolder stateHolder;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    private final UnifiedLocationProcessingService unifiedLocationProcessingService;
    private final int batchSize;
    private final ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    public ProcessingPipelineTrigger(ImportStateHolder stateHolder,
                                     RawLocationPointJdbcService rawLocationPointJdbcService,
                                     PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                                     UserJdbcService userJdbcService,
                                     UnifiedLocationProcessingService unifiedLocationProcessingService,
                                     @Value("${reitti.import.batch-size:100}") int batchSize) {
        this.stateHolder = stateHolder;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
        this.unifiedLocationProcessingService = unifiedLocationProcessingService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${reitti.process-data.schedule}")
    public void start() {
        if (stateHolder.isImportRunning()) {
            log.warn("Data Import is currently running, wil skip this run");
            return;
        }
        for (User user : userJdbcService.findAll()) {
            handleDataForUser(user, null, UUID.randomUUID().toString(), false);
        }
    }

    public void start(User user) {
        handleDataForUser(user, null, UUID.randomUUID().toString(), false);
    }

    public void handle(TriggerProcessingEvent event, boolean immediate) {
        Optional<User> byUsername = this.userJdbcService.findByUsername(event.getUsername());
        if (byUsername.isPresent()) {
            handleDataForUser(byUsername.get(), event.getPreviewId(), event.getTraceId(), immediate);
        } else {
            log.warn("No user found for username: {}", event.getUsername());
        }
    }

    private void handleDataForUser(User user, String previewId, String traceId, boolean immediate) {
        int totalProcessed = 0;

        while (true) {

            try {
                List<RawLocationPoint> currentBatch;
                if (previewId == null) {
                    currentBatch = rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, batchSize, 0);
                } else {
                    currentBatch = previewRawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, previewId, batchSize, 0);
                }

                if (currentBatch.isEmpty()) {
                    break;
                }

                Instant earliest = currentBatch.getFirst().getTimestamp();
                Instant latest = currentBatch.getLast().getTimestamp();
                log.debug("Scheduling stay detection event for user [{}] and points between [{}] and [{}]", user.getId(), earliest, latest);

                currentBatch.forEach(RawLocationPoint::markProcessed);
                if (previewId == null) {
                    rawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
                } else {
                    previewRawLocationPointJdbcService.bulkUpdateProcessedStatus(currentBatch);
                }

                if (!immediate) {
                    executorService.submit(() -> unifiedLocationProcessingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), earliest, latest, previewId, traceId)));
                } else {
                    unifiedLocationProcessingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), earliest, latest, previewId, traceId));
                }
                totalProcessed += currentBatch.size();
            } catch (Exception e) {
                log.error("Error processing batch for user [{}]", user.getId(), e);
            }
        }

        log.debug("Processed [{}] unprocessed points for user [{}]", totalProcessed, user.getId());
    }

    public boolean isIdle() {
        return executorService.getQueue().isEmpty() &&
               executorService.getActiveCount() == 0;
    }
    public int getPendingCount() {
        return executorService.getActiveCount() + executorService.getQueue().size();
    }
}
