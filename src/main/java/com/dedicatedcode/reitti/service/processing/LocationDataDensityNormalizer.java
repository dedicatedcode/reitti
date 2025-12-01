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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LocationDataDensityNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(LocationDataDensityNormalizer.class);

    private final LocationDensityConfig config;
    private final RawLocationPointJdbcService rawLocationPointService;
    private final SyntheticLocationPointGenerator syntheticGenerator;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    @Autowired
    public LocationDataDensityNormalizer(
            LocationDensityConfig config,
            RawLocationPointJdbcService rawLocationPointService,
            SyntheticLocationPointGenerator syntheticGenerator,
            VisitDetectionParametersService visitDetectionParametersService) {
        this.config = config;
        this.rawLocationPointService = rawLocationPointService;
        this.syntheticGenerator = syntheticGenerator;
        this.visitDetectionParametersService = visitDetectionParametersService;
    }

    public void normalize(User user, List<LocationPoint> newPoints) {
        if (newPoints == null || newPoints.isEmpty()) {
            logger.trace("No points to normalize for user {}", user.getUsername());
            return;
        }

        ReentrantLock userLock = userLocks.computeIfAbsent(user.getUsername(), _ -> new ReentrantLock());

        userLock.lock();
        try {
            logger.debug("Starting batch density normalization for {} points for user {}",
                    newPoints.size(), user.getUsername());

            // Step 1: Compute the time range that encompasses all new points
            TimeRange inputRange = computeTimeRange(newPoints);

            // Step 2: Get detection parameters (use the earliest point's time for config lookup)
            DetectionParameter detectionParams = visitDetectionParametersService.getCurrentConfiguration(user, inputRange.start);
            DetectionParameter.LocationDensity densityConfig = detectionParams.getLocationDensity();

            // Step 3: Expand the time range by the interpolation window to catch boundary gaps
            Duration window = Duration.ofMinutes(densityConfig.getMaxInterpolationGapMinutes());
            TimeRange expandedRange = new TimeRange(
                    inputRange.start.minus(window),
                    inputRange.end.plus(window)
            );

            // Step 4: Delete all synthetic points in the expanded range
            rawLocationPointService.deleteSyntheticPointsInRange(user, expandedRange.start, expandedRange.end);

            // Step 5: Fetch all existing points in the expanded range (single DB query)
            List<RawLocationPoint> existingPoints = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, expandedRange.start, expandedRange.end);

            logger.debug("Found {} existing points in expanded range [{} - {}]",
                    existingPoints.size(), expandedRange.start, expandedRange.end);

            // Step 7: Sort deterministically by timestamp, then by ID (for repeatability)
            existingPoints.sort(Comparator
                    .comparing(RawLocationPoint::getTimestamp)
                    .thenComparing(p -> p.getGeom().latitude())
                    .thenComparing(p -> p.getGeom().longitude())
                    .thenComparing(RawLocationPoint::isSynthetic));

            logger.trace("Processing {} total points after merge", existingPoints.size());

            // Step 8: Process gaps (generate synthetic points)
            processGaps(user, existingPoints, densityConfig);

            // Step 9: Re-fetch and handle excess density
            // We need to re-fetch because synthetic points were just inserted
            List<RawLocationPoint> updatedPoints = rawLocationPointService
                    .findByUserAndTimestampBetweenOrderByTimestampAsc(user, expandedRange.start, expandedRange.end);

            updatedPoints.sort(Comparator
                    .comparing(RawLocationPoint::getTimestamp)
                    .thenComparing(p -> p.getGeom().latitude())
                    .thenComparing(p -> p.getGeom().longitude())
                    .thenComparing(RawLocationPoint::isSynthetic));

            handleExcessDensity(user, updatedPoints);

            logger.debug("Completed batch density normalization for user {}", user.getUsername());

        } catch (Exception e) {
            logger.error("Error during batch density normalization for user {}: {}",
                    user.getUsername(), e.getMessage(), e);
        } finally {
            userLock.unlock();
        }
    }

    /**
     * Computes the minimal time range that encompasses all given points.
     */
    private TimeRange computeTimeRange(List<LocationPoint> points) {
        Instant minTime = null;
        Instant maxTime = null;

        for (LocationPoint point : points) {
            Instant timestamp = Instant.parse(point.getTimestamp());
            if (minTime == null || timestamp.isBefore(minTime)) {
                minTime = timestamp;
            }
            if (maxTime == null || timestamp.isAfter(maxTime)) {
                maxTime = timestamp;
            }
        }

        return new TimeRange(minTime, maxTime);
    }

    /**
     * Processes gaps between points and generates synthetic points where needed.
     * Only processes each gap once, regardless of how many input points touch it.
     */
    private void processGaps(
            User user,
            List<RawLocationPoint> points,
            DetectionParameter.LocationDensity densityConfig) {

        if (points.size() < 2) {
            return;
        }

        int gapThresholdSeconds = config.getGapThresholdSeconds();
        long maxInterpolationSeconds = densityConfig.getMaxInterpolationGapMinutes() * 60L;

        List<LocationPoint> allSyntheticPoints = new ArrayList<>();
        Set<GapKey> processedGaps = new HashSet<>();

        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint current = points.get(i);
            RawLocationPoint next = points.get(i + 1);

            // Skip if either point is already ignored
            if (current.isIgnored() || next.isIgnored()) {
                continue;
            }

            // Create a deterministic gap key to avoid reprocessing
            GapKey gapKey = new GapKey(current.getTimestamp(), next.getTimestamp());
            if (processedGaps.contains(gapKey)) {
                continue;
            }
            processedGaps.add(gapKey);

            long gapSeconds = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();

            if (gapSeconds > gapThresholdSeconds && gapSeconds <= maxInterpolationSeconds) {
                logger.trace("Found gap of {} seconds between {} and {}",
                        gapSeconds, current.getTimestamp(), next.getTimestamp());

                List<LocationPoint> syntheticPoints = syntheticGenerator.generateSyntheticPoints(
                        current,
                        next,
                        config.getTargetPointsPerMinute(),
                        densityConfig.getMaxInterpolationDistanceMeters()
                );

                allSyntheticPoints.addAll(syntheticPoints);
            }
        }

        if (!allSyntheticPoints.isEmpty()) {
            // Sort synthetic points by timestamp for deterministic insertion order
            allSyntheticPoints.sort(Comparator.comparing(LocationPoint::getTimestamp));

            int inserted = rawLocationPointService.bulkInsertSynthetic(user, allSyntheticPoints);
            logger.debug("Inserted {} synthetic points for user {}", inserted, user.getUsername());
        }
    }

    /**
     * Handles excess density by marking redundant points as ignored.
     * Uses deterministic rules for selecting which point to ignore.
     */
    private void handleExcessDensity(User user, List<RawLocationPoint> points) {
        if (points.size() < 2) {
            return;
        }

        int toleranceSeconds = config.getToleranceSeconds();
        Set<Long> pointsToIgnore = new LinkedHashSet<>(); // Preserve order for debugging
        Set<Long> alreadyConsidered = new HashSet<>();

        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint current = points.get(i);
            RawLocationPoint next = points.get(i + 1);

            // Skip points without IDs (not persisted) or already ignored
            if (current.getId() == null || next.getId() == null) {
                continue;
            }
            if (current.isIgnored() || next.isIgnored()) {
                continue;
            }
            if (alreadyConsidered.contains(current.getId()) || alreadyConsidered.contains(next.getId())) {
                continue;
            }

            long timeDiff = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();

            if (timeDiff < toleranceSeconds) {
                RawLocationPoint toIgnore = selectPointToIgnore(current, next);

                if (toIgnore != null && toIgnore.getId() != null) {
                    pointsToIgnore.add(toIgnore.getId());
                    alreadyConsidered.add(toIgnore.getId());
                    logger.trace("Marking point {} as ignored due to excess density", toIgnore.getId());
                }
            }
        }

        if (!pointsToIgnore.isEmpty()) {
            rawLocationPointService.bulkUpdateIgnoredStatus(new ArrayList<>(pointsToIgnore), true);
            logger.debug("Marked {} points as ignored for user {}", pointsToIgnore.size(), user.getUsername());
        }
    }

    /**
     * Selects which point to ignore when two points are too close together.
     * Rules (in priority order):
     * 1. Prefer real points over synthetic points
     * 2. Prefer points with better accuracy (lower accuracy value)
     * 3. Prefer points with accuracy info over those without
     * 4. Prefer points with lower ID (earlier insertion = more authoritative)
     */
    private RawLocationPoint selectPointToIgnore(RawLocationPoint point1, RawLocationPoint point2) {
        // Rule 1: Never ignore real points if the other is synthetic
        if (!point1.isSynthetic() && point2.isSynthetic()) {
            return point2;
        }
        if (point1.isSynthetic() && !point2.isSynthetic()) {
            return point1;
        }

        // Rule 2 & 3: Prefer points with better accuracy
        Double acc1 = point1.getAccuracyMeters();
        Double acc2 = point2.getAccuracyMeters();

        if (acc1 != null && acc2 != null) {
            if (!acc1.equals(acc2)) {
                return acc1 < acc2 ? point2 : point1;
            }
        } else if (acc1 != null) {
            return point2;
        } else if (acc2 != null) {
            return point1;
        }

        int timestampCompare = point1.getTimestamp().compareTo(point2.getTimestamp());
        if (timestampCompare != 0) {
            return timestampCompare < 0 ? point2 : point1;
        }

        // Tiebreaker: use coordinates (immutable, deterministic)
        int latCompare = Double.compare(point1.getGeom().latitude(), point2.getGeom().latitude());
        if (latCompare != 0) {
            return latCompare < 0 ? point2 : point1;
        }

        int lonCompare = Double.compare(point1.getGeom().longitude(), point2.getGeom().longitude());
        return lonCompare < 0 ? point2 : point1;

    }

    /**
     * Represents a time range with start and end instants.
     */
    private static class TimeRange {
        final Instant start;
        final Instant end;

        TimeRange(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Represents a unique gap between two timestamps.
     * Used to avoid processing the same gap multiple times.
     */
    private static class GapKey {
        final Instant start;
        final Instant end;

        GapKey(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GapKey gapKey = (GapKey) o;
            return Objects.equals(start, gapKey.start) && Objects.equals(end, gapKey.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }
}