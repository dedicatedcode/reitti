package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ImportBatchProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportBatchProcessor.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTriggers;
    
    public ImportBatchProcessor(
            RabbitTemplate rabbitTemplate,
            @Value("${reitti.import.batch-size:100}") int batchSize) {
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pendingTriggers = new ConcurrentHashMap<>();
    }
    
    public void sendToQueue(User user, List<LocationDataRequest.LocationPoint> batch) {
        LocationDataEvent event = new LocationDataEvent(
                user.getUsername(),
                new ArrayList<>(batch)
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                event
        );
        logger.info("Queued batch of {} locations for processing", batch.size());

        scheduleProcessingTrigger(user.getUsername());
    }
    
    private void scheduleProcessingTrigger(String username) {
        // Cancel any existing trigger for this user
        ScheduledFuture<?> existingTrigger = pendingTriggers.get(username);
        if (existingTrigger != null && !existingTrigger.isDone()) {
            existingTrigger.cancel(false);
        }
        
        // Schedule new trigger for 30 seconds from now
        ScheduledFuture<?> newTrigger = scheduler.schedule(() -> {
            try {
                TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(username);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        "trigger-processing",
                        triggerEvent
                );
                logger.info("Triggered processing for user: {}", username);
                pendingTriggers.remove(username);
            } catch (Exception e) {
                logger.error("Failed to trigger processing for user: {}", username, e);
            }
        }, 30, TimeUnit.SECONDS);
        
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
