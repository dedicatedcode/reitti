package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcessDensityHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExcessDensityHandler.class);

    private final LocationDensityConfig config;
    private final SourceLocationPointJdbcService rawLocationPointService;
    private final int batchSize;

    public ExcessDensityHandler(LocationDensityConfig config,
                                SourceLocationPointJdbcService rawLocationPointService,
                                @Value("${reitti.processing.point-chunk-size:10000}") int batchSize) {
        this.config = config;
        this.rawLocationPointService = rawLocationPointService;
        this.batchSize = Math.max(1, batchSize);
    }

    public TimeRange handleExcess(User user, Device device, TimeRange inputRange) {
        int toleranceSeconds = config.getToleranceSeconds() - 1;
        List<Long> pointsToIgnore = new ArrayList<>(batchSize);
        SourceLocationPoint previous = null;
        Long lastIgnoredId = null;
        InstantCursor cursor = null;
        long pointsSeen = 0;
        int totalIgnored = 0;

        while (true) {
            List<SourceLocationPoint> points = rawLocationPointService.findValidPointsAfter(user, device,
                    inputRange.start(), inputRange.end(), cursor == null ? null : cursor.timestamp(),
                    cursor == null ? null : cursor.id(), batchSize);
            if (points.isEmpty()) {
                break;
            }

            for (SourceLocationPoint current : points) {
                pointsSeen++;
                if (previous != null && (lastIgnoredId == null
                        || (!lastIgnoredId.equals(previous.getId()) && !lastIgnoredId.equals(current.getId())))) {
                    long timeDiff = Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
                    if (timeDiff < toleranceSeconds) {
                        SourceLocationPoint toIgnore = selectPointToIgnore(previous, current);
                        pointsToIgnore.add(toIgnore.getId());
                        lastIgnoredId = toIgnore.getId();
                        logger.trace("Marking point {} as ignored due to excess density", toIgnore.getId());
                    }
                }
                previous = current;
            }

            SourceLocationPoint last = points.getLast();
            cursor = new InstantCursor(last.getTimestamp(), last.getId());
            if (!pointsToIgnore.isEmpty()) {
                rawLocationPointService.bulkUpdateIgnoredStatus(user, pointsToIgnore);
                totalIgnored += pointsToIgnore.size();
                pointsToIgnore.clear();
            }
            if (points.size() < batchSize) {
                break;
            }
        }

        if (pointsSeen < 2) {
            return TimeRange.empty();
        }
        if (totalIgnored > 0) {
            logger.debug("Marked {} points as ignored for user {}", totalIgnored, user.getUsername());
        }
        return inputRange;
    }

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

    private record InstantCursor(Instant timestamp, Long id) {
    }
}
