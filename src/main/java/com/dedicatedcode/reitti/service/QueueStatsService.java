package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueueStatsService {

    private final RabbitAdmin rabbitAdmin;

    private static final int LOOKBACK_HOURS = 24;
    private static final long DEFAULT_PROCESSING_TIME = 2;

    private final List<String> QUEUES = List.of(
            RabbitMQConfig.LOCATION_DATA_QUEUE,
            RabbitMQConfig.STAY_DETECTION_QUEUE,
            RabbitMQConfig.MERGE_VISIT_QUEUE,
            RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE,
            RabbitMQConfig.DETECT_TRIP_QUEUE);

    private final Map<String, List<ProcessingRecord>> processingHistory = new ConcurrentHashMap<>();
    
    private final Map<String, Integer> previousMessageCounts = new ConcurrentHashMap<>();

    @Autowired
    public QueueStatsService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
        QUEUES.forEach(queue -> {
            processingHistory.put(queue, new ArrayList<>());
            previousMessageCounts.put(queue, 0);
        });
    }

    private static class ProcessingRecord {
        final LocalDateTime timestamp;
        final long processingTimeMs;
        
        ProcessingRecord(LocalDateTime timestamp, long processingTimeMs) {
            this.timestamp = timestamp;
            this.processingTimeMs = processingTimeMs;
        }
    }

    public List<QueueStats> getQueueStats() {
        return QUEUES.stream().map(name -> {
            int currentMessageCount = getMessageCount(name);
            updateProcessingHistory(name, currentMessageCount);
            
            long avgProcessingTime = calculateAverageProcessingTime(name);
            long estimatedTime = currentMessageCount * avgProcessingTime;
            
            return new QueueStats(name, currentMessageCount, formatProcessingTime(estimatedTime), calculateProgress(currentMessageCount, 100));
        }).toList();
    }

    private void updateProcessingHistory(String queueName, int currentMessageCount) {
        Integer previousCount = previousMessageCounts.get(queueName);
        
        if (previousCount != null && currentMessageCount < previousCount) {
            long processingTimePerMessage = estimateProcessingTimePerMessage(queueName);
            List<ProcessingRecord> history = processingHistory.get(queueName);
            LocalDateTime now = LocalDateTime.now();
            history.add(new ProcessingRecord(now, processingTimePerMessage));
            cleanupOldRecords(history, now);
        }
        
        previousMessageCounts.put(queueName, currentMessageCount);
    }

    private long estimateProcessingTimePerMessage(String queueName) {
        List<ProcessingRecord> history = processingHistory.get(queueName);
        
        if (history.isEmpty()) {
            return DEFAULT_PROCESSING_TIME;
        }
        
        return calculateAverageFromHistory(history);
    }

    private long calculateAverageProcessingTime(String queueName) {
        List<ProcessingRecord> history = processingHistory.get(queueName);
        
        if (history.isEmpty()) {
            return DEFAULT_PROCESSING_TIME;
        }
        
        return calculateAverageFromHistory(history);
    }

    private long calculateAverageFromHistory(List<ProcessingRecord> history) {
        if (history.isEmpty()) {
            return DEFAULT_PROCESSING_TIME;
        }
        
        return (long) history.stream()
                .mapToLong(record -> record.processingTimeMs)
                .average()
                .orElse(DEFAULT_PROCESSING_TIME);
    }

    private void cleanupOldRecords(List<ProcessingRecord> history, LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(LOOKBACK_HOURS);
        history.removeIf(record -> record.timestamp.isBefore(cutoff));
    }

    private int getMessageCount(String queueName) {
        Properties properties = rabbitAdmin.getQueueProperties(queueName);
        if (properties.containsKey(RabbitAdmin.QUEUE_MESSAGE_COUNT)) {
            return (int) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        }
        return 0;
    }

    private String formatProcessingTime(long milliseconds) {
        if (milliseconds < 60000) {
            return (milliseconds / 1000) + " sec";
        } else if (milliseconds < 3600000) {
            return (milliseconds / 60000) + " min";
        } else {
            long hours = milliseconds / 3600000;
            long minutes = (milliseconds % 3600000) / 60000;
            return hours + " hr " + minutes + " min";
        }
    }

    private int calculateProgress(int count, int maxExpected) {
        if (count <= 0) return 0;
        if (count >= maxExpected) return 100;
        return (int) ((count / (double) maxExpected) * 100);
    }
}
