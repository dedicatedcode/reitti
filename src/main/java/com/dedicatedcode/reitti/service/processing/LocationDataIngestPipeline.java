package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationQueueService;
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
    private final UserNotificationQueueService userNotificationQueueService;

    @Autowired
    public LocationDataIngestPipeline(GeoPointAnomalyFilter geoPointAnomalyFilter,
                                      UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService,
                                      UserSettingsJdbcService userSettingsJdbcService,
                                      UserNotificationQueueService userNotificationQueueService) {
        this.geoPointAnomalyFilter = geoPointAnomalyFilter;
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userNotificationQueueService = userNotificationQueueService;
    }

    public void processLocationData(LocationDataEvent event) {
        long start = System.currentTimeMillis();

        Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: [{}]", event.getUsername());
            return;
        }

        User user = userOpt.get();
        List<LocationDataRequest.LocationPoint> points = event.getPoints();
        List<LocationDataRequest.LocationPoint> filtered = this.geoPointAnomalyFilter.filterAnomalies(points);
        rawLocationPointJdbcService.bulkInsert(user, filtered);
        userSettingsJdbcService.updateNewestData(user, filtered);
        userNotificationQueueService.newRawLocationData(user, filtered);
        logger.info("Finished storing points [{}] for user [{}] in [{}]ms. Filtered out [{}] points.", filtered.size(), event.getUsername(), System.currentTimeMillis() - start, points.size() - filtered.size());
    }
}
