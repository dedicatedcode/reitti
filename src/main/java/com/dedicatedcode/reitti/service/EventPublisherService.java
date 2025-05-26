package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.event.LocationDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class EventPublisherService {
    private static final Logger logger = LoggerFactory.getLogger(EventPublisherService.class);
    
    private final ApplicationEventPublisher eventPublisher;
    
    public EventPublisherService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    public void publishLocationDataEvent(LocationDataEvent event) {
        logger.info("Publishing location data event for user {} with {} points", 
                event.getUsername(), event.getPoints().size());
        eventPublisher.publishEvent(event);
    }
}
