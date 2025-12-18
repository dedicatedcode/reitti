package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LocationBatchingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationBatchingService.class);
    
    private final ImportProcessor importProcessor;
    private final Map<String, UserBatch> userBatches = new ConcurrentHashMap<>();
    private final ReentrantLock flushLock = new ReentrantLock();
    
    @Value("${reitti.batching.max-batch-size:100}")
    private int maxBatchSize;
    
    @Value("${reitti.batching.max-wait-time-ms:5000}")
    private long maxWaitTimeMs;
    
    @Autowired
    public LocationBatchingService(ImportProcessor importProcessor) {
        this.importProcessor = importProcessor;
    }
    
    public void addLocationPoint(User user, LocationPoint locationPoint) {
        String username = user.getUsername();
        
        userBatches.compute(username, (key, existingBatch) -> {
            if (existingBatch == null) {
                existingBatch = new UserBatch(user);
            }
            
            existingBatch.addLocationPoint(locationPoint);
            
            // Check if we should flush this batch immediately
            if (existingBatch.shouldFlush(maxBatchSize, maxWaitTimeMs)) {
                flushBatch(username, existingBatch);
                return new UserBatch(user); // Return new empty batch
            }
            
            return existingBatch;
        });
    }
    
    @Scheduled(fixedDelayString = "${reitti.batching.flush-interval-ms:2000}")
    public void flushExpiredBatches() {
        flushLock.lock();
        try {
            List<String> usersToFlush = new ArrayList<>();
            
            userBatches.forEach((username, batch) -> {
                if (batch.shouldFlush(maxBatchSize, maxWaitTimeMs)) {
                    usersToFlush.add(username);
                }
            });
            
            for (String username : usersToFlush) {
                UserBatch batch = userBatches.remove(username);
                if (batch != null && !batch.isEmpty()) {
                    flushBatch(username, batch);
                }
            }
        } finally {
            flushLock.unlock();
        }
    }
    
    @PreDestroy
    @EventListener(ContextClosedEvent.class)
    public void flushAllBatches() {
        logger.info("Application shutting down, flushing all pending location batches");
        flushLock.lock();
        try {
            userBatches.forEach((username, batch) -> {
                if (!batch.isEmpty()) {
                    flushBatch(username, batch);
                }
            });
            userBatches.clear();
        } finally {
            flushLock.unlock();
        }
    }
    
    private void flushBatch(String username, UserBatch batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            List<LocationPoint> points = batch.getLocationPoints();
            logger.debug("Flushing batch of {} location points for user {}", points.size(), username);
            importProcessor.processBatch(batch.getUser(), points);
        } catch (Exception e) {
            logger.error("Error flushing batch for user {}", username, e);
        }
    }
    
    private static class UserBatch {
        private final User user;
        private final List<LocationPoint> locationPoints;
        private final long createdAt;
        
        public UserBatch(User user) {
            this.user = user;
            this.locationPoints = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
        }
        
        public void addLocationPoint(LocationPoint point) {
            locationPoints.add(point);
        }
        
        public boolean shouldFlush(int maxBatchSize, long maxWaitTimeMs) {
            return locationPoints.size() >= maxBatchSize || 
                   (System.currentTimeMillis() - createdAt) >= maxWaitTimeMs;
        }
        
        public boolean isEmpty() {
            return locationPoints.isEmpty();
        }
        
        public List<LocationPoint> getLocationPoints() {
            return new ArrayList<>(locationPoints);
        }
        
        public User getUser() {
            return user;
        }
    }
}
