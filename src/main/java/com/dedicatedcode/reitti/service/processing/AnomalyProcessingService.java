package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AnomalyProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(AnomalyProcessingService.class);

    private final GeoPointAnomalyFilter detector;
    private final SourceLocationPointJdbcService repository;

    public AnomalyProcessingService(GeoPointAnomalyFilter geoPointAnomalyFilter, SourceLocationPointJdbcService repository) {
        this.detector = geoPointAnomalyFilter;
        this.repository = repository;
    }

    public TimeRange processAndMarkAnomalies(User user, Device device, Instant start, Instant end) {
        repository.resetInvalidStatus(user, start, end);
        List<SourceLocationPoint> pointsToCheck = repository.findByUserAndTimestampBetweenOrderByTimestampAsc(user, device, start, end, false, true);
        logger.debug("Found {} points to check for user {}", pointsToCheck.size(), user.getUsername());
        List<SourceLocationPoint> anomalousPoints = detector.detectAnomalies(pointsToCheck);
        repository.bulkUpdateInvalidStatus(anomalousPoints);
        logger.info("Marked {} points as invalid for user {}", anomalousPoints.size(), user.getUsername());
        return new TimeRange(start, end);
    }
}
