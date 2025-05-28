package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.service.processing.LocationProcessingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class LocationDataProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataProcessingService.class);
    
    private final LocationProcessingPipeline processingPipeline;
    
    public LocationDataProcessingService(LocationProcessingPipeline processingPipeline) {
        this.processingPipeline = processingPipeline;
    }
    
    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "4-16")
    public void handleLocationDataEvent(LocationDataEvent event) {
        logger.info("Received location data event from RabbitMQ for user {} with {} points", 
                event.getUsername(), event.getPoints().size());
        
        try {
            processingPipeline.processLocationData(event);
        } catch (Exception e) {
            logger.error("Error processing location data event", e);
            throw new RuntimeException(e);
        }
    }
}
