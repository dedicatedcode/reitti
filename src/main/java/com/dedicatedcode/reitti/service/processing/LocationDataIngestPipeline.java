package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    public LocationDataIngestPipeline(UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    public void processLocationData(LocationDataEvent event) {
        long start = System.currentTimeMillis();

        logger.debug("Starting processing pipeline for user {} with {} points",
                event.getUsername(), event.getPoints().size());

        Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername());
            return;
        }

        User user = userOpt.get();
        List<LocationDataRequest.LocationPoint> points = event.getPoints();
        rawLocationPointJdbcService.bulkInsert(user, points);
        logger.debug("Finished processing pipeline for user [{}] in [{}]ms", event.getUsername(), System.currentTimeMillis() - start);
    }

}
