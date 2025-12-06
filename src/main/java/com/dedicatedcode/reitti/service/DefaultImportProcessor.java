package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.LocationDataIngestPipeline;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTrigger;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class DefaultImportProcessor implements ImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultImportProcessor.class);

    private final LocationDataIngestPipeline locationDataIngestPipeline;
    private final int batchSize;
    private final int processingIdleStartTime;
    private final ProcessingPipelineTrigger processingPipelineTrigger;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTriggers;

    public DefaultImportProcessor(
            LocationDataIngestPipeline locationDataIngestPipeline,
            @Value("${reitti.import.batch-size:10000}") int batchSize,
            @Value("${reitti.import.processing-idle-start-time:15}") int processingIdleStartTime,
            ProcessingPipelineTrigger processingPipelineTrigger) {
        this.locationDataIngestPipeline = locationDataIngestPipeline;
        this.batchSize = batchSize;
        this.processingIdleStartTime = processingIdleStartTime;
        this.processingPipelineTrigger = processingPipelineTrigger;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pendingTriggers = new ConcurrentHashMap<>();
    }

    @Override
    public void processBatch(User user, List<LocationPoint> batch) {
        logger.debug("Sending batch of {} locations for storing", batch.size());
        locationDataIngestPipeline.processLocationData(user.getUsername(), new ArrayList<>(batch));
        logger.debug("Sending batch of {} locations for processing", batch.size());
        scheduleProcessingTrigger(user.getUsername());
    }

    @Override
    public void scheduleProcessingTrigger(String username) {
        {
            ScheduledFuture<?> existingTrigger = pendingTriggers.get(username);
            if (existingTrigger != null && !existingTrigger.isDone()) {
                existingTrigger.cancel(false);
            }

            ScheduledFuture<?> newTrigger = scheduler.schedule(() -> {
                try {
                    DefaultImportProcessor.logger.debug("Triggered processing for user: {}", username);
                    TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(username, null, UUID.randomUUID().toString());
                    processingPipelineTrigger.handle(triggerEvent, false);

                    pendingTriggers.remove(username);
                } catch (Exception e) {
                    DefaultImportProcessor.logger.error("Failed to trigger processing for user: {}", username, e);
                }
            }, processingIdleStartTime, TimeUnit.SECONDS);

            pendingTriggers.put(username, newTrigger);
        }
    }

    @Override
    public boolean isIdle() {
        return pendingTriggers.isEmpty() || pendingTriggers.values().stream().allMatch(ScheduledFuture::isDone);
    }
    
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    
    public int getBatchSize() {
        return batchSize;
    }
}
