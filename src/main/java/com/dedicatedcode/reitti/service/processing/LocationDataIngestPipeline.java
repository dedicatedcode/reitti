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

import java.util.List;
import java.util.Optional;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final GeoPointAnomalyFilter geoPointAnomalyFilter;
    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserNotificationService userNotificationService;
    private final LocationDataDensityNormalizer densityNormalizer;

    @Autowired
    public LocationDataIngestPipeline(GeoPointAnomalyFilter geoPointAnomalyFilter,
                                      UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService,
                                      UserSettingsJdbcService userSettingsJdbcService,
                                      UserNotificationService userNotificationService,
                                      LocationDataDensityNormalizer densityNormalizer) {
        this.geoPointAnomalyFilter = geoPointAnomalyFilter;
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userNotificationService = userNotificationService;
        this.densityNormalizer = densityNormalizer;
    }

    @PreDestroy
    public void shutdown() {
    }

    public void processLocationData(LocationDataEvent event) {
        try {
            long start = System.currentTimeMillis();
            logger.debug("starting processing of event: {}", event);

            Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

            if (userOpt.isEmpty()) {
                logger.warn("User not found for name: [{}]", event.getUsername());
                return;
            }

            User user = userOpt.get();
            List<LocationPoint> points = event.getPoints();
            List<LocationPoint> filtered = this.geoPointAnomalyFilter.filterAnomalies(points);

            // Store real points first
            int updatedRows = rawLocationPointJdbcService.bulkInsert(user, filtered);

            // Normalize density around each new point
            densityNormalizer.normalize(user, filtered);

            userSettingsJdbcService.updateNewestData(user, filtered);
            userNotificationService.newRawLocationData(user, filtered);
            logger.info("Finished storing and normalizing points [{}] for user [{}] in [{}]ms. Filtered out [{}] points before database and [{}] after database.", filtered.size(), event.getUsername(), System.currentTimeMillis() - start, points.size() - filtered.size(), filtered.size() - updatedRows);
        } catch (Exception e) {
            logger.error("Error during processing of event: {}", event, e);
        }
    }
}
