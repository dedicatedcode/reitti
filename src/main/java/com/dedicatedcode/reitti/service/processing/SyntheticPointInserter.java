package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
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
        long maxInterpolatedGapSeconds = config.getMaxInterpolationGapHours() * 3600L;

        List<LocationPoint> allSyntheticPoints = new ArrayList<>();

        for (int i = 0; i < sortedRealPoints.size() - 1; i++) {
            RawLocationPoint current = sortedRealPoints.get(i);
            RawLocationPoint next = sortedRealPoints.get(i + 1);

            long gapSeconds = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            if (gapSeconds > gapThresholdSeconds && gapSeconds <= maxInterpolationSeconds) {
                // Manually deleted points act as walls inside the gap: only sub-segments between
                // two real points get filled, segments touching a deleted point stay empty
                List<Instant> ignoredTimestamps = rawLocationPointService.findManuallyIgnoredTimestampsIn(
                        user, current.getTimestamp(), next.getTimestamp());
                List<RawLocationPoint> anchors = new ArrayList<>();
                anchors.add(current);
                for (Instant ignored : ignoredTimestamps) {
                    // wall marker, never persisted; synthetic is true, so fill logic skips segments touching it
                    anchors.add(new RawLocationPoint(null, null, ignored, current.getGeom(), null, null, true, true, 0L));
                }
                anchors.add(next);
                for (int a = 0; a < anchors.size() - 1; a++) {
                    RawLocationPoint from = anchors.get(a);
                    RawLocationPoint to = anchors.get(a + 1);
                    long segmentSeconds = Duration.between(from.getTimestamp(), to.getTimestamp()).getSeconds();
                    List<LocationPoint> syntheticPoints;
                    if (from.isSynthetic() || to.isSynthetic()) {
                        // wall at a manually deleted point: this sub-segment stays unfilled
                        syntheticPoints = List.of();
                    } else {
                        double distanceMeters = GeoUtils.distanceInMeters(from, to);
                        double impliedSpeedMps = distanceMeters / segmentSeconds;
                        if (impliedSpeedMps <= config.getMaxStationarySpeedMps()) {
                            // Too slow to have travelled. The device simply stopped reporting while the
                            // user stayed put (battery saving, or a plain gap), so fill with a stationary
                            // cluster anchored at the first point.
                            syntheticPoints = syntheticGenerator.generateStationaryPoints(
                                    from, to,
                                    config.getTargetPointsPerMinute(),
                                    STATIONARY_JITTER_RADIUS_METERS
                            );
                        } else if (segmentSeconds <= maxInterpolatedGapSeconds) {
                            // Movement over a gap short enough that a straight line between the
                            // endpoints is honest.
                            syntheticPoints = syntheticGenerator.generateSyntheticPoints(
                                    from, to,
                                    config.getTargetPointsPerMinute()
                            );
                        } else {
                            // Movement over a gap longer than we are willing to interpolate: nothing
                            // plausible to draw between the endpoints, so leave the hole rather than
                            // fabricate a straight line across it.
                            syntheticPoints = List.of();
                        }
                        logger.trace("Gap of {}s over {}m ({} km/h) between {} and {} -> {} synthetic points [{}]",
                                segmentSeconds, String.format("%.0f", distanceMeters),
                                String.format("%.2f", impliedSpeedMps * 3.6),
                                from.getTimestamp(), to.getTimestamp(), syntheticPoints.size(),
                                impliedSpeedMps <= config.getMaxStationarySpeedMps() ? "stationary"
                                        : segmentSeconds <= maxInterpolatedGapSeconds ? "interpolated" : "unfilled");
                    }
                    allSyntheticPoints.addAll(syntheticPoints);
                }
            }
        }

        if (!allSyntheticPoints.isEmpty()) {
            int inserted = rawLocationPointService.bulkInsertSynthetic(user, allSyntheticPoints);
            logger.debug("Inserted {} synthetic points for user {} between {} and {}", inserted, user.getUsername(), sortedRealPoints.getFirst().getTimestamp(), sortedRealPoints.getLast().getTimestamp());
        }
    }
}