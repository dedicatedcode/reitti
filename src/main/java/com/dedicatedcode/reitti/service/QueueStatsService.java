package com.dedicatedcode.reitti.service;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class QueueStatsService {

    private final RabbitAdmin rabbitAdmin;
    
    // Average processing times in milliseconds per item
    private static final long AVG_LOCATION_PROCESSING_TIME = 500; // 500ms per location point
    private static final long AVG_VISIT_PROCESSING_TIME = 2000;   // 2s per visit
    private static final long AVG_TRIP_PROCESSING_TIME = 3000;    // 3s per trip
    
    // Queue names
    private static final String LOCATION_QUEUE = " location-data-queue";
    private static final String VISIT_QUEUE = "significant-place-queue";

    @Autowired
    public QueueStatsService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get queue counts
        int locationCount = getMessageCount(LOCATION_QUEUE);
        int visitCount = getMessageCount(VISIT_QUEUE);

        // Calculate estimated processing times
        String locationTime = formatProcessingTime(locationCount * AVG_LOCATION_PROCESSING_TIME);
        String visitTime = formatProcessingTime(visitCount * AVG_VISIT_PROCESSING_TIME);

        // Calculate progress percentages (assuming some max values)
        int locationProgress = calculateProgress(locationCount, 1000);
        int visitProgress = calculateProgress(visitCount, 100);

        // Populate stats map
        stats.put("locationDataCount", locationCount);
        stats.put("visitCount", visitCount);

        stats.put("locationDataTime", locationTime);
        stats.put("visitTime", visitTime);

        stats.put("locationDataProgress", locationProgress);
        stats.put("visitProgress", visitProgress);

        return stats;
    }
    
    private int getMessageCount(String queueName) {
        Properties properties = rabbitAdmin.getQueueProperties(queueName);
        if (properties != null && properties.containsKey(RabbitAdmin.QUEUE_MESSAGE_COUNT)) {
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
