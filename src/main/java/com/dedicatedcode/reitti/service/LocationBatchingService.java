package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.dedicatedcode.reitti.service.importer.PromotionJobHandler;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.github.kagkarlsson.scheduler.task.Task;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationBatchingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationBatchingService.class);
    
    private final Map<String, UserBatch> userBatches = new ConcurrentHashMap<>();
    private final Set<String> initializedPartitions = ConcurrentHashMap.newKeySet();
    private final LocationPointStagingService locationPointStagingService;
    private final Task<PromotionJobHandler.PromotionTaskData> promotionTask;
    private final JobSchedulingService jobScheduler;

    private final int maxBatchSize;
    private final long maxWaitTimeMs;
    
    @Autowired
    public LocationBatchingService(LocationPointStagingService locationPointStagingService,
                                   Task<PromotionJobHandler.PromotionTaskData> promotionTask,
                                   JobSchedulingService jobScheduler,
                                   @Value("${reitti.batching.max-batch-size:100}") int maxBatchSize,
                                   @Value("${reitti.batching.max-wait-time-ms:5000}") int maxWaitTimeMs) {
        this.locationPointStagingService = locationPointStagingService;
        this.promotionTask = promotionTask;
        this.jobScheduler = jobScheduler;
        this.maxBatchSize = maxBatchSize;
        this.maxWaitTimeMs = maxWaitTimeMs;
    }

    public void addLocationPoint(User user, Device device, LocationPoint locationPoint) {
        String sessionKey = getSessionKey(user, device);

        userBatches.compute(sessionKey, (key, existingBatch) -> {
            if (existingBatch == null) {
                existingBatch = new UserBatch(user, device, key);
            }

            existingBatch.addLocationPoint(locationPoint);

            if (existingBatch.shouldFlush(maxBatchSize, maxWaitTimeMs)) {
                executeFlush(existingBatch);
                return null;
            }
            return existingBatch;
        });
    }

    private String getSessionKey(User user, Device device) {
        return String.format("stream_%d_%s_%s",
                             user.getId(),
                             device != null ? device.id() : "main",
                             LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))
                .toLowerCase();
    }

    private void executeFlush(UserBatch batch) {
        if (batch.isEmpty()) return;

        try {
            String pKey = batch.getPartitionKey();

            if (!initializedPartitions.contains(pKey)) {
                locationPointStagingService.ensurePartitionExists(pKey);
                initializedPartitions.add(pKey);
            }

            logger.debug("Flushing batch of {} location points for partition {}", batch.getLocationPoints().size(), pKey);
            locationPointStagingService.insertBatch(pKey,
                                                    batch.getUser(),
                                                    batch.getDevice(),
                                                    batch.getLocationPoints()
            );
            batch.clear();
            this.jobScheduler.enqueueTask(promotionTask,
                                          new PromotionJobHandler.PromotionTaskData(batch.user, batch.device, pKey, false),
                                          JobSchedulingService.Metadata.builder()
                                                  .user(batch.user)
                                                  .jobType(JobType.GPS_INGESTION)
                                                  .friendlyName("GPS Data Promotion").build());
        } catch (Exception e) {
            logger.error("Failed to flush batch for partition {}", batch.getPartitionKey(), e);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void flushExpiredBatches() {
        userBatches.forEach((key, batch) -> {
            if (batch.shouldFlush(maxBatchSize, maxWaitTimeMs)) {
                userBatches.computeIfPresent(key, (ignored, b) -> {
                    executeFlush(b);
                    return null;
                });
            }
        });
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("Flushing batches on shutdown...");
        userBatches.forEach((ignored, batch) -> executeFlush(batch));
    }

    private static class UserBatch {
        private final User user;
        private final Device device;
        private final String partitionKey;
        private final List<LocationPoint> locationPoints = new ArrayList<>();
        private final long createdAt = System.currentTimeMillis();

        public UserBatch(User user, Device device, String partitionKey) {
            this.user = user;
            this.device = device;
            this.partitionKey = partitionKey;
        }

        public void addLocationPoint(LocationPoint point) {
            locationPoints.add(point);
        }

        public boolean shouldFlush(int size, long ms) {
            return locationPoints.size() >= size || (System.currentTimeMillis() - createdAt) >= ms;
        }

        public boolean isEmpty() {
            return locationPoints.isEmpty();
        }

        public List<LocationPoint> getLocationPoints() {
            return locationPoints;
        }

        public String getPartitionKey() {
            return partitionKey;
        }

        public User getUser() {
            return user;
        }

        public Device getDevice() {
            return device;
        }

        public void clear() {
            this.locationPoints.clear();
        }
    }
}
