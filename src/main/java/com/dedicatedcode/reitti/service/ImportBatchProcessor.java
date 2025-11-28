package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.LocationDataIngestPipeline;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class ImportBatchProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportBatchProcessor.class);

    private final LocationDataIngestPipeline locationDataIngestPipeline;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final int processingIdleStartTime;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTriggers;
    
    public ImportBatchProcessor(
            LocationDataIngestPipeline locationDataIngestPipeline,
            RabbitTemplate rabbitTemplate,
            @Value("${reitti.import.batch-size:100}") int batchSize,
            @Value("${reitti.import.processing-idle-start-time:15}") int processingIdleStartTime) {
        this.locationDataIngestPipeline = locationDataIngestPipeline;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
        this.processingIdleStartTime = processingIdleStartTime;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pendingTriggers = new ConcurrentHashMap<>();
    }
    
    public void sendToQueue(User user, List<LocationPoint> batch) {
        LocationDataEvent event = new LocationDataEvent(
                user.getUsername(),
                new ArrayList<>(batch),
                UUID.randomUUID().toString()
        );
        logger.info("1 - Sending batch of {} locations for storing", batch.size());
        locationDataIngestPipeline.processLocationData(event);
//        rabbitTemplate.convertAndSend(
//                RabbitMQConfig.EXCHANGE_NAME,
//                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
//                event
//        );
        logger.info("1 - Queued batch of {} locations for processing", batch.size());
        scheduleProcessingTrigger(user.getUsername());
    }
    
    private void scheduleProcessingTrigger(String username) {
        ScheduledFuture<?> existingTrigger = pendingTriggers.get(username);
        if (existingTrigger != null && !existingTrigger.isDone()) {
            existingTrigger.cancel(false);
        }
        
        ScheduledFuture<?> newTrigger = scheduler.schedule(() -> {
            try {
                TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(username, null, UUID.randomUUID().toString());
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY,
                        triggerEvent
                );
                logger.info("1 - Triggered processing for user: {}", username);
                pendingTriggers.remove(username);
            } catch (Exception e) {
                logger.error("Failed to trigger processing for user: {}", username, e);
            }
        }, processingIdleStartTime, TimeUnit.SECONDS);
        
        pendingTriggers.put(username, newTrigger);
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
