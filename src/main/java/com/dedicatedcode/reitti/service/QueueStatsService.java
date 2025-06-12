package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class QueueStatsService {

    private final RabbitAdmin rabbitAdmin;

    // Average processing times in milliseconds per item
    private static final long AVG_LOCATION_PROCESSING_TIME = 2; // 500ms per location point

    private final List<String> QUEUES = List.of(
            RabbitMQConfig.LOCATION_DATA_QUEUE,
            RabbitMQConfig.STAY_DETECTION_QUEUE,
            RabbitMQConfig.MERGE_VISIT_QUEUE,
            RabbitMQConfig.SIGNIFICANT_PLACE_QUEUE,
            RabbitMQConfig.DETECT_TRIP_QUEUE);

    @Autowired
    public QueueStatsService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    public List<QueueStats> getQueueStats() {

        return QUEUES.stream().map(name -> {
            int messageCount = getMessageCount(name);
            return new QueueStats(name, messageCount, formatProcessingTime(messageCount * AVG_LOCATION_PROCESSING_TIME), calculateProgress(messageCount, 100));
        }).toList();
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
