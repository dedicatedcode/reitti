package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SyntheticPointInserter {

    private static final Logger logger = LoggerFactory.getLogger(SyntheticPointInserter.class);

    private final LocationDensityConfig config;
    private final RawLocationPointJdbcService rawLocationPointService;
    private final SyntheticLocationPointGenerator syntheticGenerator;
    private final VisitDetectionParametersService visitDetectionParametersService;

    public SyntheticPointInserter(LocationDensityConfig config,
                                  RawLocationPointJdbcService rawLocationPointService,
                                  SyntheticLocationPointGenerator syntheticGenerator,
                                  VisitDetectionParametersService visitDetectionParametersService) {
        this.config = config;
        this.rawLocationPointService = rawLocationPointService;
        this.syntheticGenerator = syntheticGenerator;
        this.visitDetectionParametersService = visitDetectionParametersService;
    }

    /**
     * Processes the given time range: deletes old synthetic points, then
     * inserts new synthetic points where real-point gaps are too large.
     *
     * @param user       the owning user
     * @param inputRange the time range that covers the newly arrived points
     */
    public void fillGaps(User user, TimeRange inputRange) {
        // 1. Fetch density configuration (using the earliest point time)
        DetectionParameter detectionParams = visitDetectionParametersService.getCurrentConfiguration(
                user, inputRange.start());
        DetectionParameter.LocationDensity densityConfig = detectionParams.getLocationDensity();

        // 2. Expand range to catch boundary gaps
        long maxInterpolationGapMinutes = densityConfig.getMaxInterpolationGapMinutes();
        Duration window = Duration.ofMinutes(maxInterpolationGapMinutes);
        TimeRange expandedRange = new TimeRange(
                inputRange.start().minus(window),
                inputRange.end().plus(window)
        );

        // 3. Delete all existing synthetic points in the expanded range
        rawLocationPointService.deleteSyntheticPointsInRange(user, expandedRange.start(), expandedRange.end());

        // 4. Fetch all real points in the expanded range
        List<RawLocationPoint> realPoints = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(
                        user,
                        expandedRange.start(),
                        expandedRange.end()
                );

        // 5. Sort deterministically (same logic as original)
        realPoints.sort(Comparator
                .comparing(RawLocationPoint::getTimestamp)
                .thenComparing(p -> p.getGeom().latitude())
                .thenComparing(p -> p.getGeom().longitude())
                .thenComparing(RawLocationPoint::isSynthetic));

        // 6. Process gaps
        processGaps(user, realPoints, densityConfig);
    }

    private void processGaps(User user,
                             List<RawLocationPoint> sortedRealPoints,
                             DetectionParameter.LocationDensity densityConfig) {
        if (sortedRealPoints.size() < 2) return;

        int gapThresholdSeconds = config.getGapThresholdSeconds();
        long maxInterpolationSeconds = densityConfig.getMaxInterpolationGapMinutes() * 60L;

        List<LocationPoint> allSyntheticPoints = new ArrayList<>();

        for (int i = 0; i < sortedRealPoints.size() - 1; i++) {
            RawLocationPoint current = sortedRealPoints.get(i);
            RawLocationPoint next = sortedRealPoints.get(i + 1);

            // Skip ignored or synthetic (shouldn't happen after deletion, but safe)
            if (current.isIgnored() || next.isIgnored() || current.isSynthetic()) continue;

            long gapSeconds = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            if (gapSeconds > gapThresholdSeconds && gapSeconds <= maxInterpolationSeconds) {
                List<LocationPoint> syntheticPoints = syntheticGenerator.generateSyntheticPoints(
                        current, next,
                        config.getTargetPointsPerMinute(),
                        densityConfig.getMaxInterpolationDistanceMeters()
                );
                logger.trace("Gap of {}s between {} and {} -> {} synthetic points",
                        gapSeconds, current.getTimestamp(), next.getTimestamp(), syntheticPoints.size());
                allSyntheticPoints.addAll(syntheticPoints);
            }
        }

        if (!allSyntheticPoints.isEmpty()) {
            int inserted = rawLocationPointService.bulkInsertSynthetic(user, allSyntheticPoints);
            logger.debug("Inserted {} synthetic points for user {}", inserted, user.getUsername());
        }
    }
}