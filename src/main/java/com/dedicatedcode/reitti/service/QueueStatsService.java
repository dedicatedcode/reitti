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
    private static final long DEFAULT_PROCESSING_TIME = 2000;

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

    public List<QueueStats> getQueueStats() {
        return QUEUES.stream().map(name -> {
            int currentMessageCount = getMessageCount(name);
            updateProcessingHistory(name, currentMessageCount);
            
            long avgProcessingTime = calculateAverageProcessingTime(name);
            long estimatedTime = currentMessageCount * avgProcessingTime;
            
            return new QueueStats(name, currentMessageCount, formatProcessingTime(estimatedTime), calculateProgress(name, currentMessageCount));
        }).toList();
    }

    private void updateProcessingHistory(String queueName, int currentMessageCount) {
        Integer previousCount = previousMessageCounts.get(queueName);
        
        if (previousCount != null && currentMessageCount < previousCount) {
            long processingTimePerMessage = estimateProcessingTimePerMessage(queueName);
            List<ProcessingRecord> history = processingHistory.get(queueName);
            LocalDateTime now = LocalDateTime.now();
            history.add(new ProcessingRecord(now, this.rabbitAdmin.getQueueInfo(queueName).getMessageCount(), processingTimePerMessage));
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

    private int calculateProgress(String queueName, int currentMessageCount) {
        if (currentMessageCount == 0) return 100; // No messages = fully processed
        
        List<ProcessingRecord> history = processingHistory.get(queueName);
        if (history.isEmpty()) {
            // No processing history, base progress on queue size
            // Smaller queues show higher progress
            if (currentMessageCount <= 5) return 80;
            if (currentMessageCount <= 20) return 60;
            if (currentMessageCount <= 100) return 40;
            return 20;
        }
        
        // Calculate processing trend over recent history
        Integer previousCount = previousMessageCounts.get(queueName);
        if (previousCount != null && previousCount > currentMessageCount) {
            // Messages are being processed - higher progress
            int processedRecently = previousCount - currentMessageCount;
            double processingRate = Math.min(100, (processedRecently / (double) Math.max(1, previousCount)) * 100);
            return Math.max(50, (int) (50 + processingRate / 2)); // 50-100% range when actively processing
        }
        
        // Queue is stable or growing - lower progress based on size
        if (currentMessageCount <= 10) return 70;
        if (currentMessageCount <= 50) return 50;
        if (currentMessageCount <= 200) return 30;
        return 10;
    }


    private record ProcessingRecord(LocalDateTime timestamp, long numberOfMessages, long processingTimeMs) { }

}
