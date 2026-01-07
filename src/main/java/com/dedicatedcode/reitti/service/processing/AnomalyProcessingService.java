package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AnomalyProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(AnomalyProcessingService.class);

    private final GeoPointAnomalyFilter detector;
    private final GeoPointAnomalyFilterConfig config;
    private final RawLocationPointJdbcService repository;

    public AnomalyProcessingService(GeoPointAnomalyFilter geoPointAnomalyFilter, GeoPointAnomalyFilterConfig config, RawLocationPointJdbcService repository) {
        this.detector = geoPointAnomalyFilter;
        this.config = config;
        this.repository = repository;
    }

    public void processAndMarkAnomalies(User user, Instant start, Instant end) {
        Instant startTime = start.minus(config.getHistoryLookback(), ChronoUnit.HOURS);
        Instant endTime = end.plus(config.getHistoryLookback(), ChronoUnit.HOURS);

        repository.resetInvalidStatus(user, startTime, endTime);
        List<RawLocationPoint> pointsToCheck = repository.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startTime, endTime, false, false, true);
        logger.debug("Found {} points to check for user {}", pointsToCheck.size(), user.getUsername());
        List<RawLocationPoint> anomalousPoints = detector.detectAnomalies(pointsToCheck);
        repository.bulkUpdateInvalidStatus(anomalousPoints);
        logger.info("Marked {} points as invalid for user {}", anomalousPoints.size(), user.getUsername());
    }
}
