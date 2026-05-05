package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class ExcessDensityHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExcessDensityHandler.class);

    private final LocationDensityConfig config;
    private final VisitDetectionParametersService visitDetectionParametersService;
    private final SourceLocationPointJdbcService rawLocationPointService;

    public ExcessDensityHandler(LocationDensityConfig config, VisitDetectionParametersService visitDetectionParametersService,
                                SourceLocationPointJdbcService rawLocationPointService) {
        this.config = config;
        this.visitDetectionParametersService = visitDetectionParametersService;
        this.rawLocationPointService = rawLocationPointService;
    }

    public TimeRange handleExcess(User user, Device device, TimeRange inputRange) {
        DetectionParameter detectionParams = visitDetectionParametersService.getCurrentConfiguration(user, inputRange.start());
        DetectionParameter.LocationDensity densityConfig = detectionParams.getLocationDensity();

        // Step 2: Expand the time range by the interpolation window to catch boundary gaps
        long maxInterpolationGapMinutes = densityConfig.getMaxInterpolationGapMinutes();
        Duration window = Duration.ofMinutes(maxInterpolationGapMinutes);
        TimeRange expandedRange = new TimeRange(
                inputRange.start().minus(window),
                inputRange.end().plus(window)
        );
        List<SourceLocationPoint> points = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, device, expandedRange.start(), expandedRange.end());
        if (points.size() < 2) {
            return TimeRange.empty();
        }

        int toleranceSeconds = config.getToleranceSeconds() - 1;
        Set<Long> pointsToIgnore = new LinkedHashSet<>();
        Set<Long> alreadyConsidered = new HashSet<>();

        for (int i = 0; i < points.size() - 1; i++) {
            SourceLocationPoint current = points.get(i);
            SourceLocationPoint next = points.get(i + 1);

            // Safety filters
            if (current.getId() == null || next.getId() == null) continue;
            if (current.isIgnored() || next.isIgnored()) continue;
            if (alreadyConsidered.contains(current.getId()) || alreadyConsidered.contains(next.getId())) continue;

            long timeDiff = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            if (timeDiff < toleranceSeconds) {
                SourceLocationPoint toIgnore = selectPointToIgnore(current, next);
                if (toIgnore.getId() != null) {
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
        return expandedRange;
    }

    // The selection logic is unchanged from the original, kept here for completeness.
    private SourceLocationPoint selectPointToIgnore(SourceLocationPoint p1, SourceLocationPoint p2) {
        Double acc1 = p1.getAccuracyMeters();
        Double acc2 = p2.getAccuracyMeters();
        if (acc1 != null && acc2 != null) {
            if (!acc1.equals(acc2)) return acc1 < acc2 ? p2 : p1;
        } else if (acc1 != null) {
            return p2;
        } else if (acc2 != null) {
            return p1;
        }

        int timeCmp = p1.getTimestamp().compareTo(p2.getTimestamp());
        if (timeCmp != 0) return timeCmp < 0 ? p2 : p1;

        int latCmp = Double.compare(p1.getGeom().latitude(), p2.getGeom().latitude());
        if (latCmp != 0) return latCmp < 0 ? p2 : p1;

        int lonCmp = Double.compare(p1.getGeom().longitude(), p2.getGeom().longitude());
        return lonCmp < 0 ? p2 : p1;
    }
}