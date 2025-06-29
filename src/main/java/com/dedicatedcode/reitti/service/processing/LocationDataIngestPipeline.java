package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationDataIngestPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataIngestPipeline.class);

    private final UserJdbcService userJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final long accuracyInMetersThreshold;

    @Autowired
    public LocationDataIngestPipeline(UserJdbcService userJdbcService,
                                      RawLocationPointJdbcService rawLocationPointJdbcService,
                                      @Value("${reitti.process-data.accuracy-in-meters-threshold}") long accuracyInMetersThreshold) {
        this.userJdbcService = userJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.accuracyInMetersThreshold = accuracyInMetersThreshold;
    }

    public void processLocationData(LocationDataEvent event) {
        long start = System.currentTimeMillis();

        Optional<User> userOpt = userJdbcService.findByUsername(event.getUsername());

        if (userOpt.isEmpty()) {
            logger.warn("User not found for name: {}", event.getUsername());
            return;
        }

        User user = userOpt.get();
        List<LocationDataRequest.LocationPoint> points = event.getPoints();
        List<LocationDataRequest.LocationPoint> filtered = points.stream().filter(locationPoint -> locationPoint.getAccuracyMeters() <= this.accuracyInMetersThreshold).collect(Collectors.toList());
        rawLocationPointJdbcService.bulkInsert(user, filtered);
        logger.info("Finished storing points [{}]for user [{}] in [{}]ms. Filtered out [{}] points.", filtered.size(), event.getUsername(), System.currentTimeMillis() - start, points.size() - filtered.size());
    }

}
