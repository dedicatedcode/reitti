package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final UserRepository userRepository;
    private final LocationDataService locationDataService;

    @Autowired
    public LocationDataIngestPipeline(
            UserRepository userRepository,
            LocationDataService locationDataService) {
        this.userRepository = userRepository;
        this.locationDataService = locationDataService;
    }

    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE, concurrency = "4-16")
    public void processLocationData(LocationDataEvent event) {
        logger.debug("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userRepository.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername());
            return;
        }

        User user = userOpt.get();

        List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, event.getPoints());

        if (savedPoints.isEmpty()) {
            logger.debug("No new points to process for user {}", user.getUsername());
        } else {
            logger.info("Saved {} new location points for user {}", savedPoints.size(), user.getUsername());
        }
    }

}
