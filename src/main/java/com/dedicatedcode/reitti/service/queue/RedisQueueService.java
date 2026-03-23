package com.dedicatedcode.reitti.service.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {
    private static final Logger log = LoggerFactory.getLogger(RedisQueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisQueueKeys keys;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Map<String, RedisQueueService.QueueMetadata> registeredQueues = new ConcurrentHashMap<>();

    @Autowired
    public RedisQueueService(@Qualifier("redisQueueTemplate") RedisTemplate<String, Object> redisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${spring.cache.redis.key-prefix:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keys = new RedisQueueKeys(keyPrefix);
        this.objectMapper = objectMapper;
        this.executorService = Executors.newCachedThreadPool();
    }

    @SuppressWarnings("unchecked")
    private void updateQueueMetadata(String queueName, long delta, boolean isEnqueue) {
        String metaKey = keys.metadataKey(queueName);

        // Use Redis transactions for atomic updates
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();

                // Check if metadata exists
                Boolean exists = operations.hasKey(metaKey);
                if (Boolean.FALSE.equals(exists)) {
                    Map<String, String> initialMetadata = new HashMap<>();
                    initialMetadata.put("createdAt", Instant.now().toString());
                    initialMetadata.put("totalEnqueued", "0");
                    initialMetadata.put("totalProcessed", "0");
                    initialMetadata.put("totalFailed", "0");
                    initialMetadata.put("totalRetried", "0");
                    initialMetadata.put("queueName", queueName);
                    initialMetadata.put("status", "ACTIVE");
                    initialMetadata.put("concurrency", "1"); // Default

                    operations.opsForHash().putAll(metaKey, initialMetadata);
                }

                String now = Instant.now().toString();

                if (isEnqueue) {
                    // For enqueue operations - use increment which works with Long/String
                    operations.opsForHash().increment(metaKey, "totalEnqueued", delta);
                    operations.opsForHash().put(metaKey, "lastEnqueuedAt", now);

                    // Update pending count (enqueued - processed)
                    Long totalEnqueued = (Long) operations.opsForHash().get(metaKey, "totalEnqueued");
                    Long totalProcessed = (Long) operations.opsForHash().get(metaKey, "totalProcessed");
                    if (totalEnqueued != null && totalProcessed != null) {
                        operations.opsForHash().put(metaKey, "pendingCount", totalEnqueued - totalProcessed);
                    }
                } else {
                    // For processing completion/failure
                    operations.opsForHash().increment(metaKey, "totalProcessed", delta);
                    operations.opsForHash().put(metaKey, "lastProcessedAt", now);
                }

                operations.opsForHash().put(metaKey, "lastActivityAt", now);

                return operations.exec();
            }
        });
    }

    private void updateQueueMetadata(String queueName, long delta) {
        updateQueueMetadata(queueName, delta, true);
    }

    private void updateProcessingSuccess(String queueName) {
        updateQueueMetadata(queueName, 1, false);
    }

    private void updateFailureStats(String queueName) {
        String metaKey = keys.metadataKey(queueName);
        redisTemplate.opsForHash().increment(metaKey, "totalFailed", 1);
        redisTemplate.opsForHash().put(metaKey, "lastFailedAt", Instant.now().toString());
    }

    private void updateRetryStats(String queueName) {
        String metaKey = keys.metadataKey(queueName);
        redisTemplate.opsForHash().increment(metaKey, "totalRetried", 1);
    }

    public QueueStatistics getQueueStats(String queueName) {
        String metaKey = keys.metadataKey(queueName);

        if (!redisTemplate.hasKey(metaKey)) {
            return QueueStatistics.empty(queueName);
        }

        Map<Object, Object> rawHash = redisTemplate.opsForHash().entries(metaKey);
        Map<String, Object> stats = new HashMap<>();

        rawHash.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                if (value instanceof String stringValue) {
                    try {
                        if (stringValue.matches("\\d+")) {
                            stats.put(stringKey, Long.parseLong(stringValue));
                        } else if (stringValue.matches("\\d+\\.\\d+")) {
                            stats.put(stringKey, Double.parseDouble(stringValue));
                        } else {
                            stats.put(stringKey, stringValue);
                        }
                    } catch (NumberFormatException e) {
                        stats.put(stringKey, stringValue);
                    }
                } else {
                    stats.put(stringKey, value);
                }
            }
        });

        long totalEnqueued = ((Number) stats.getOrDefault("totalEnqueued", 0L)).longValue();
        long totalProcessed = ((Number) stats.getOrDefault("totalProcessed", 0L)).longValue();

        stats.put("totalEnqueued", totalEnqueued);
        stats.put("totalProcessed", totalProcessed);
        stats.put("pendingCount", totalEnqueued - totalProcessed);

        if (totalEnqueued > 0) {
            stats.put("processingRate", (double) totalProcessed / totalEnqueued * 100);
        }

        Long currentQueueLength = redisTemplate.opsForList().size(keys.queueKey(queueName));
        Long currentProcessingLength = redisTemplate.opsForList().size(keys.processingKey(queueName));

        stats.put("currentQueueLength", currentQueueLength != null ? currentQueueLength : 0L);
        stats.put("currentProcessingLength", currentProcessingLength != null ? currentProcessingLength : 0L);

        // Ensure required fields exist
        if (!stats.containsKey("pendingCount")) {
            stats.put("pendingCount", 0L);
        }
        if (!stats.containsKey("processingRate")) {
            stats.put("processingRate", 0.0);
        }
        if (!stats.containsKey("status")) {
            stats.put("status", "ACTIVE");
        }
        if (!stats.containsKey("concurrency")) {
            stats.put("concurrency", 1);
        }

        return QueueStatistics.fromMap(queueName, stats);
    }

    public <T> void enqueue(String queueName, T payload) {
        QueueMessage<T> message = new QueueMessage<>(
                UUID.randomUUID().toString(),
                payload,
                Instant.now(),
                queueName,
                0,
                queueName
        );

        String json = serialize(message);

        // Push to queue
        redisTemplate.opsForList().rightPush(keys.queueKey(queueName), json);

        // Update metadata
        updateQueueMetadata(queueName, 1);

        log.trace("Enqueued message {} to queue {}", message.getId(), queueName);
    }

    public <T> void registerHandler(String queueName,
                                    Class<? extends T> payloadType,
                                    MessageHandler<? extends T> handler,
                                    int concurrency,
                                    int maxRetries,
                                    String deadLetterQueue) {

        QueueMetadata metadata = new QueueMetadata(
                queueName, payloadType, handler, maxRetries,
                deadLetterQueue != null ? deadLetterQueue : queueName + ".dlq"
        );

        registeredQueues.put(queueName, metadata);

        for (int i = 0; i < concurrency; i++) {
            executorService.submit(() -> processMessages(queueName));
        }
    }

    private void processMessages(String queueName) {
        QueueMetadata metadata = registeredQueues.get(queueName);
        if (metadata == null) {
            throw new IllegalStateException("Queue not registered: " + queueName);
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Object json = redisTemplate.opsForList()
                        .rightPopAndLeftPush(
                                keys.queueKey(queueName),
                                keys.processingKey(queueName),
                                30, TimeUnit.SECONDS);

                if (json != null) {
                    processMessage(json, metadata);
                }
            } catch (IllegalStateException | RedisSystemException e) {
                if (e.getMessage() != null && (
                        e.getMessage().contains("LettuceConnectionFactory has been STOPPED") ||
                                e.getMessage().contains("was destroyed and cannot be used anymore") ||
                                e.getCause().getMessage().contains("Connection closed"))) {
                    log.debug("Redis connection closed during shutdown, stopping queue {}", queueName);
                    break;
                }
                log.error("Error processing messages from queue {}", queueName, e);
            } catch (Exception e) {
                log.error("Error processing messages from queue {}", queueName, e);
            }
        }

        log.info("Shutting down processing thread for queue {}", queueName);
    }

    @SuppressWarnings("unchecked")
    private <T> void processMessage(Object json, QueueMetadata metadata) {
        QueueMessage<T> message = (QueueMessage<T>) deserializeMessage(json, metadata.payloadType());

        try {
            MessageHandler<T> handler = (MessageHandler<T>) metadata.handler();
            handler.handle(message.getPayload());

            redisTemplate.opsForList().remove(
                    keys.processingKey(message.getQueueName()),
                    1, json
            );

            updateProcessingSuccess(message.getQueueName());

            log.trace("Successfully processed message {}", message.getId());

        } catch (Exception e) {
            updateFailureStats(message.getQueueName());
            handleFailure(message, e, metadata);
        }
    }

    private <T> void handleFailure(QueueMessage<T> message,
                                   Exception error,
                                   QueueMetadata metadata) {
        int currentRetry = message.getRetryCount();
        String json = serialize(message);

        if (currentRetry < metadata.maxRetries()) {
            // Retry: move back to main queue with incremented retry count
            QueueMessage<T> retryMessage = message.withRetryCount(currentRetry + 1);
            String retryJson = serialize(retryMessage);

            redisTemplate.opsForList().rightPush(
                    keys.queueKey(message.getOriginalQueue()),
                    retryJson
            );
            updateRetryStats(message.getQueueName());
        } else {
            redisTemplate.opsForList().rightPush(
                    keys.deadLetterKey(metadata.deadLetterQueue()),
                    json
            );
        }

        // Remove from processing queue
        redisTemplate.opsForList().remove(
                keys.processingKey(message.getQueueName()),
                1, json
        );

        log.error("Failed to process message after {} retries: {}",
                  currentRetry, message.getId(), error);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> QueueMessage<T> deserializeMessage(Object json, Class<T> payloadType) {
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(QueueMessage.class, payloadType);
            return objectMapper.readValue(json.toString(), type);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    public Map<String, QueueStatistics> getAllQueueStats() {
        Map<String, QueueStatistics> allStats = new HashMap<>();

        for (String queueName : registeredQueues.keySet()) {
            allStats.put(queueName, getQueueStats(queueName));
        }

        return allStats;
    }

    public QueueSummary getQueueSummary() {
        Map<String, QueueStatistics> allStats = getAllQueueStats();

        long totalMessages = allStats.values().stream()
                .mapToLong(QueueStatistics::currentQueueLength)
                .sum();

        long totalProcessing = allStats.values().stream()
                .mapToLong(QueueStatistics::currentProcessingLength)
                .sum();

        long totalPending = allStats.values().stream()
                .mapToLong(QueueStatistics::pendingCount)
                .sum();

        return new QueueSummary(totalMessages, totalProcessing, totalPending, allStats.size());
    }

    /**
     * Purges all messages from the specified queue (main and processing queues).
     * Does not affect dead letter queue or metadata.
     *
     * @param queueName the name of the queue to purge
     */
    public void purgeQueue(String queueName) {
        purgeQueue(queueName, false, false);
    }

    /**
     * Purges all messages from the specified queue with options.
     *
     * @param queueName              the name of the queue to purge
     * @param includeDeadLetterQueue if true, also purges the dead letter queue
     */
    public void purgeQueue(String queueName, boolean includeDeadLetterQueue) {
        purgeQueue(queueName, includeDeadLetterQueue, false);
    }

    /**
     * Purges all messages from the specified queue with full control.
     *
     * @param queueName              the name of the queue to purge
     * @param includeDeadLetterQueue if true, also purges the dead letter queue
     * @param resetMetadata          if true, resets queue statistics to zero
     */
    public void purgeQueue(String queueName, boolean includeDeadLetterQueue, boolean resetMetadata) {
        log.info("Purging queue {} (includeDeadLetterQueue: {}, resetMetadata: {})",
                 queueName, includeDeadLetterQueue, resetMetadata);

        // Delete main queue
        Boolean mainDeleted = redisTemplate.delete(keys.queueKey(queueName));

        // Delete processing queue
        Boolean processingDeleted = redisTemplate.delete(keys.processingKey(queueName));

        Boolean dlqDeleted = false;
        if (includeDeadLetterQueue) {
            String dlqName = getDeadLetterQueueName(queueName);
            dlqDeleted = redisTemplate.delete(keys.deadLetterKey(dlqName));
        }

        if (resetMetadata) {
            resetQueueMetadata(queueName);
        }

        log.debug("Purged queue {}: main deleted={}, processing deleted={}, dlq deleted={}",
                  queueName, mainDeleted, processingDeleted, dlqDeleted);
    }

    /**
     * Resets all queue statistics to zero while preserving the queue structure.
     *
     * @param queueName the name of the queue to reset
     */
    public void resetQueueStatistics(String queueName) {
        resetQueueMetadata(queueName);
        log.info("Reset statistics for queue {}", queueName);
    }

    /**
     * Purges all messages from all registered queues.
     * Use with caution - this is a destructive operation.
     */
    public void purgeAllQueues() {
        log.warn("Purging all registered queues");

        for (String queueName : registeredQueues.keySet()) {
            purgeQueue(queueName, true, false);
        }

        log.info("Purged all registered queues");
    }

    /**
     * Gets the dead letter queue name for a given queue.
     *
     * @param queueName the main queue name
     * @return the dead letter queue name
     */
    private String getDeadLetterQueueName(String queueName) {
        QueueMetadata metadata = registeredQueues.get(queueName);
        if (metadata != null) {
            return metadata.deadLetterQueue();
        } else {
            // Default naming convention
            return queueName + ".dlq";
        }
    }

    /**
     * Resets queue metadata statistics to zero.
     *
     * @param queueName the name of the queue
     */
    private void resetQueueMetadata(String queueName) {
        String metaKey = keys.metadataKey(queueName);

        if (redisTemplate.hasKey(metaKey)) {
            // Reset all counters to zero, preserve other metadata
            Map<String, String> resetValues = new HashMap<>();
            resetValues.put("totalEnqueued", "0");
            resetValues.put("totalProcessed", "0");
            resetValues.put("totalFailed", "0");
            resetValues.put("totalRetried", "0");
            resetValues.put("pendingCount", "0");
            resetValues.put("lastActivityAt", Instant.now().toString());

            // Update the reset values
            redisTemplate.opsForHash().putAll(metaKey, resetValues);

            log.debug("Reset metadata for queue {}", queueName);
        } else {
            updateQueueMetadata(queueName, 1);
        }
    }

    /**
     * Gets the number of messages currently in the queue (main + processing).
     *
     * @param queueName the name of the queue
     * @return total message count
     */
    public long getMessageCount(String queueName) {
        Long mainCount = redisTemplate.opsForList().size(keys.queueKey(queueName));
        Long processingCount = redisTemplate.opsForList().size(keys.processingKey(queueName));

        return (mainCount != null ? mainCount : 0L) +
                (processingCount != null ? processingCount : 0L);
    }

    /**
     * Gets the number of messages in the dead letter queue.
     *
     * @param queueName the main queue name
     * @return dead letter queue message count
     */
    public long getDeadLetterCount(String queueName) {
        String dlqName = getDeadLetterQueueName(queueName);
        Long dlqCount = redisTemplate.opsForList().size(keys.deadLetterKey(dlqName));

        return dlqCount != null ? dlqCount : 0L;
    }

    // Summary record
    public record QueueSummary(
            long totalMessages,
            long totalProcessing,
            long totalPending,
            int activeQueues
    ) {
    }

    private record QueueMetadata(String queueName, Class<?> payloadType, MessageHandler<?> handler, int maxRetries,
                                 String deadLetterQueue) {
    }

}