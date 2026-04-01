package com.dedicatedcode.reitti.service.queue;

import java.time.Instant;
import java.util.Map;

public record QueueStatistics(
    String queueName,
    long totalEnqueued,
    long totalProcessed,
    long totalFailed,
    long totalRetried,
    long pendingCount,
    double processingRate,
    long currentQueueLength,
    long currentProcessingLength,
    Instant createdAt,
    Instant lastEnqueuedAt,
    Instant lastProcessedAt,
    Instant lastFailedAt,
    Instant lastActivityAt,
    String status,
    int concurrency
) {
    
    public static QueueStatistics empty(String queueName) {
        return new QueueStatistics(
            queueName,
            0L, 0L, 0L, 0L, 0L, 0.0,
            0L, 0L,
            null, null, null, null, null,
            "INACTIVE", 1
        );
    }
    
    public static QueueStatistics fromMap(String queueName, Map<String, Object> rawStats) {
        return new QueueStatistics(
            queueName,
            getLong(rawStats, "totalEnqueued", 0L),
            getLong(rawStats, "totalProcessed", 0L),
            getLong(rawStats, "totalFailed", 0L),
            getLong(rawStats, "totalRetried", 0L),
            getLong(rawStats, "pendingCount", 0L),
            getDouble(rawStats, "processingRate", 0.0),
            getLong(rawStats, "currentQueueLength", 0L),
            getLong(rawStats, "currentProcessingLength", 0L),
            getInstant(rawStats, "createdAt"),
            getInstant(rawStats, "lastEnqueuedAt"),
            getInstant(rawStats, "lastProcessedAt"),
            getInstant(rawStats, "lastFailedAt"),
            getInstant(rawStats, "lastActivityAt"),
            getString(rawStats, "status", "UNKNOWN"),
            getInt(rawStats, "concurrency", 1)
        );
    }
    
    private static Long getLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static Double getDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static Integer getInt(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String) return (String) value;
        return defaultValue;
    }
    
    private static Instant getInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}