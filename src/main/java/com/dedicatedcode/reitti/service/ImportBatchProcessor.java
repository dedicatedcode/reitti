package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.security.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
public class ImportBatchProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportBatchProcessor.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;

    
    public ImportBatchProcessor(
            RabbitTemplate rabbitTemplate,
            @Value("${reitti.import.batch-size:100}") int batchSize
            ) {
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
    }
    
    public void sendToQueue(User user, List<LocationPoint> batch) {
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
    }

    public int getBatchSize() {
        return batchSize;
    }
}
