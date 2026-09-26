package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Gaps failing the interpolation distance check are treated as stationary (device off during
     * a longer stay) and filled with a cluster anchored at the first point, if the gap is at
     * least this long. Shorter gaps are plausibly real movement and stay unfilled.
     */
    static final Duration MIN_STATIONARY_GAP = Duration.ofMinutes(15);

    /**
     * Radius of the deterministic jitter around the anchor point for stationary fills.
     */
    private static final double STATIONARY_JITTER_RADIUS_METERS = 15.0;

    private final LocationDensityConfig config;
    private final RawLocationPointJdbcService rawLocationPointService;
    private final SyntheticLocationPointGenerator syntheticGenerator;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final int maxBatchSize;

    public SyntheticPointInserter(LocationDensityConfig config,
                                  RawLocationPointJdbcService rawLocationPointService,
                                  SyntheticLocationPointGenerator syntheticGenerator,
                                  VisitDetectionParametersService visitDetectionParametersService,
                                  @Value("${reitti.import.batch-size:1000}") int maxBatchSize) {
        this.config = config;
        this.rawLocationPointService = rawLocationPointService;
        this.syntheticGenerator = syntheticGenerator;
        this.visitDetectionParametersService = visitDetectionParametersService;
        this.maxBatchSize = maxBatchSize;
    }

    public void fillGaps(User user, TimeRange inputRange) {
        // 1. Fetch density configuration (using the earliest point time)
        DetectionParameter detectionParams = visitDetectionParametersService.getCurrentConfiguration(
                user, inputRange.start());
        DetectionParameter.LocationDensity densityConfig = detectionParams.getLocationDensity();

        // 2. Delete all existing synthetic points in the range
        rawLocationPointService.deleteSyntheticPointsInRange(user, inputRange.start(), inputRange.end());

        Instant currentStart = inputRange.start();
        RawLocationPoint lastPointOfPreviousBatch = null;
        while (currentStart.isBefore(inputRange.end())) {
            // 3. Fetch all real points in the range
            List<RawLocationPoint> realPoints = rawLocationPointService
                    .findByUserAndTimestampBetweenOrderByTimestampAsc(
                            user,
                            currentStart,
                            inputRange.end(),
                            false,
                            0,
                            maxBatchSize
                    );

            // 4. Sort deterministically (same logic as original)
            realPoints.sort(Comparator
                                    .comparing(RawLocationPoint::getTimestamp)
                                    .thenComparing(p -> p.getGeom().latitude())
                                    .thenComparing(p -> p.getGeom().longitude())
                                    .thenComparing(RawLocationPoint::isSynthetic));

            // 5. Process gaps, carrying over the last point of the previous batch so the
            //    pair spanning the batch boundary is not lost
            List<RawLocationPoint> batch = new ArrayList<>(realPoints.size() + 1);
            if (lastPointOfPreviousBatch != null) {
                batch.add(lastPointOfPreviousBatch);
            }
            batch.addAll(realPoints);
            processGaps(user, batch, densityConfig);

            if (realPoints.isEmpty()) break;
            lastPointOfPreviousBatch = realPoints.getLast();
            currentStart = lastPointOfPreviousBatch.getTimestamp().plus(1, ChronoUnit.MILLIS);
        }

    }

    private void processGaps(User user,
                             List<RawLocationPoint> sortedRealPoints,
                             DetectionParameter.LocationDensity densityConfig) {
        if (sortedRealPoints.size() < 2) return;

        int gapThresholdSeconds = config.getGapThresholdSeconds();
        long maxInterpolationSeconds = densityConfig.getMaxInterpolationGapMinutes() * 60L;
        double maxInterpolationDistanceMeters = densityConfig.getMaxInterpolationDistanceMeters();

        List<LocationPoint> allSyntheticPoints = new ArrayList<>();

        for (int i = 0; i < sortedRealPoints.size() - 1; i++) {
            RawLocationPoint current = sortedRealPoints.get(i);
            RawLocationPoint next = sortedRealPoints.get(i + 1);

            long gapSeconds = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            if (gapSeconds > gapThresholdSeconds && gapSeconds <= maxInterpolationSeconds) {
                List<LocationPoint> syntheticPoints;
                if (GeoUtils.distanceInMeters(current, next) <= maxInterpolationDistanceMeters) {
                    syntheticPoints = syntheticGenerator.generateSyntheticPoints(
                            current, next,
                            config.getTargetPointsPerMinute(),
                            maxInterpolationDistanceMeters
                    );
                } else if (gapSeconds >= MIN_STATIONARY_GAP.getSeconds()) {
                    // Distance too large for interpolation but the gap is long enough to assume
                    // the user stayed in place (e.g. device off during a longer stay): fill with a
                    // stationary cluster anchored at the first point
                    syntheticPoints = syntheticGenerator.generateStationaryPoints(
                            current, next,
                            config.getTargetPointsPerMinute(),
                            STATIONARY_JITTER_RADIUS_METERS
                    );
                } else {
                    syntheticPoints = List.of();
                }
                logger.trace("Gap of {}s between {} and {} -> {} synthetic points",
                        gapSeconds, current.getTimestamp(), next.getTimestamp(), syntheticPoints.size());
                allSyntheticPoints.addAll(syntheticPoints);
            }
        }

        if (!allSyntheticPoints.isEmpty()) {
            int inserted = rawLocationPointService.bulkInsertSynthetic(user, allSyntheticPoints);
            logger.debug("Inserted {} synthetic points for user {} between {} and {}", inserted, user.getUsername(), sortedRealPoints.getFirst().getTimestamp(), sortedRealPoints.getLast().getTimestamp());
        }
    }
}