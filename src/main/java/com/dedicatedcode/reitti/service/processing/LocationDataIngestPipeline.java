package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final AnomalyProcessingService anomalyProcessingService;
    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserNotificationService userNotificationService;
    private final LocationDataDensityNormalizer densityNormalizer;

    @Autowired
    public LocationDataIngestPipeline(AnomalyProcessingService anomalyProcessingService,
                                      UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService,
                                      UserSettingsJdbcService userSettingsJdbcService,
                                      UserNotificationService userNotificationService,
                                      LocationDataDensityNormalizer densityNormalizer) {
        this.anomalyProcessingService = anomalyProcessingService;
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userNotificationService = userNotificationService;
        this.densityNormalizer = densityNormalizer;
    }

    @PreDestroy
    public void shutdown() {
    }

    public void processLocationData(String username, List<LocationPoint> points) {
        try {
            long start = System.currentTimeMillis();
            logger.debug("starting processing");

            Optional<User> userOpt = userJdbcService.findByUsername(username);

            if (userOpt.isEmpty()) {
                logger.warn("User not found for name: [{}]", username);
                return;
            }

            User user = userOpt.get();

            // Store all points first
            int updatedRows = rawLocationPointJdbcService.bulkInsert(user, points);
            List<Instant> timestamp = points.stream().map(LocationPoint::getTimestamp).map(ZonedDateTime::parse).map(ChronoZonedDateTime::toInstant).sorted().toList();

            anomalyProcessingService.processAndMarkAnomalies(user, timestamp.getFirst(), timestamp.getLast());

            densityNormalizer.normalize(user, points);
            userSettingsJdbcService.updateNewestData(user, points);
            userNotificationService.newRawLocationData(user, points);
            logger.info("Finished storing and normalizing points [{}] for user [{}] in [{}]ms. Filtered out [{}] after database.", points.size(), user, System.currentTimeMillis() - start, points.size() - updatedRows);
        } catch (Exception e) {
            logger.error("Error during processing: ", e);
        }
    }

}
