package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final GeoPointAnomalyFilter geoPointAnomalyFilter;
    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserNotificationService userNotificationService;
    private final LocationDataDensityNormalizer densityNormalizer;
    private final RabbitTemplate rabbitTemplate;
    private final int processingIdleStartTime;
    private final ScheduledExecutorService scheduler =Executors.newScheduledThreadPool(2);;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTriggers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    @Autowired
    public LocationDataIngestPipeline(GeoPointAnomalyFilter geoPointAnomalyFilter,
                                      UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService,
                                      UserSettingsJdbcService userSettingsJdbcService,
                                      UserNotificationService userNotificationService,
                                      LocationDataDensityNormalizer densityNormalizer,
                                      RabbitTemplate rabbitTemplate,
                                      @Value("${reitti.import.processing-idle-start-time:15}") int processingIdleStartTime) {
        this.geoPointAnomalyFilter = geoPointAnomalyFilter;
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userNotificationService = userNotificationService;
        this.densityNormalizer = densityNormalizer;
        this.rabbitTemplate = rabbitTemplate;
        this.processingIdleStartTime = processingIdleStartTime;
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

    public void processLocationData(LocationDataEvent event) {
        ReentrantLock userLock = userLocks.computeIfAbsent(event.getUsername(), k -> new ReentrantLock());
        
        userLock.lock();
        try {
            long start = System.currentTimeMillis();

            Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

            if (userOpt.isEmpty()) {
                logger.warn("User not found for name: [{}]", event.getUsername());
                return;
            }

            User user = userOpt.get();
            List<LocationPoint> points = event.getPoints();
            List<LocationPoint> filtered = this.geoPointAnomalyFilter.filterAnomalies(points);
            
            // Store real points first
            int updatedRows = rawLocationPointJdbcService.bulkInsert(user, filtered);
            
            // Normalize density around each new point
            for (LocationPoint point : filtered) {
                densityNormalizer.normalizeAroundPoint(user, point);
            }
            
            userSettingsJdbcService.updateNewestData(user, filtered);
            userNotificationService.newRawLocationData(user, filtered);
            logger.info("Finished storing and normalizing points [{}] for user [{}] in [{}]ms. Filtered out [{}] points before database and [{}] after database.", filtered.size(), event.getUsername(), System.currentTimeMillis() - start, points.size() - filtered.size(), filtered.size() - updatedRows);
            scheduleProcessingTrigger(user.getUsername());
        } finally {
            userLock.unlock();
        }
    }

    private void scheduleProcessingTrigger(String username) {
        ScheduledFuture<?> existingTrigger = pendingTriggers.get(username);
        if (existingTrigger != null && !existingTrigger.isDone()) {
            existingTrigger.cancel(false);
        }

        ScheduledFuture<?> newTrigger = scheduler.schedule(() -> {
            try {
                TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(username, null);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY,
                        triggerEvent
                );
                logger.info("Triggered processing for user: {}", username);
                pendingTriggers.remove(username);
            } catch (Exception e) {
                logger.error("Failed to trigger processing for user: {}", username, e);
            }
        }, processingIdleStartTime, TimeUnit.SECONDS);

        pendingTriggers.put(username, newTrigger);
    }
}
