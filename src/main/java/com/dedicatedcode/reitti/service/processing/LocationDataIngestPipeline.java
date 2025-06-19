package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final UserJdbcService userJdbcService;
    private final LocationDataService locationDataService;

    @Autowired
    public LocationDataIngestPipeline(
            UserJdbcService userJdbcService,
            LocationDataService locationDataService) {
        this.userJdbcService = userJdbcService;
        this.locationDataService = locationDataService;
    }

    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "4-16")
    public void processLocationData(LocationDataEvent event) {
        logger.debug("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername());
            return;
        }

        User user = userOpt.get();
        locationDataService.processLocationData(user, event.getPoints());
    }

}
