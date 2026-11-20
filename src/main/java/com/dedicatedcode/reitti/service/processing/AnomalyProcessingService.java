package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

@Service
public class AnomalyProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(AnomalyProcessingService.class);

    private final GeoPointFilterConfig config;
    private final SourceLocationPointJdbcService repository;
    private final int batchSize;

    public AnomalyProcessingService(GeoPointFilterConfig config,
                                    SourceLocationPointJdbcService repository,
                                    @Value("${reitti.processing.point-chunk-size:10000}") int batchSize) {
        this.config = config;
        this.repository = repository;
        this.batchSize = Math.max(1, batchSize);
    }

    public TimeRange processAndMarkAnomalies(User user, Device device, Instant start, Instant end) {
        repository.resetInvalidStatus(user, start, end);

        SpeedSeries points = new SpeedSeries(batchSize);
        repository.streamValidPoints(user, device, start, end, batchSize,
                point -> points.add(point, config.getMaxAccuracyMeters()));

        int accuracyAnomalies = markAccuracyAnomalies(points);
        int speedAnomalies = markSpeedAnomalies(points);
        logger.info("Marked {} points as invalid for user {}", accuracyAnomalies + speedAnomalies, user.getUsername());
        return new TimeRange(start, end);
    }

    private int markAccuracyAnomalies(SpeedSeries points) {
        List<Long> anomalyIds = new ArrayList<>(batchSize);
        int marked = 0;
        for (int i = 0; i < points.pointCount(); i++) {
            if (points.isAccuracyAnomaly(i)) {
                anomalyIds.add(points.pointId(i));
                if (anomalyIds.size() == batchSize) {
                    marked += markAnomalies(anomalyIds);
                }
            }
        }
        return marked + markAnomalies(anomalyIds);
    }

    private int markSpeedAnomalies(SpeedSeries points) {
        if (points.speedCount() == 0) {
            return 0;
        }

        double medianSpeed = points.medianSpeed();
        double threshold = Math.max(config.getMaxSpeedKmh() / 3.6, medianSpeed * 3);
        List<Long> anomalyIds = new ArrayList<>(batchSize);
        int marked = 0;

        if (points.speedCount() >= 2 && points.speedAfter(0) > threshold && points.speedAfter(1) <= threshold) {
            anomalyIds.add(points.pointId(0));
        }

        for (int i = 1; i < points.pointCount() - 1; i++) {
            if (points.speedAfter(i - 1) > threshold && points.speedAfter(i) > threshold) {
                anomalyIds.add(points.pointId(i));
                if (anomalyIds.size() == batchSize) {
                    marked += markAnomalies(anomalyIds);
                }
            }
        }

        int lastSpeed = points.speedCount() - 1;
        if (points.speedCount() >= 2 && points.speedAfter(lastSpeed) > threshold
                && points.speedAfter(lastSpeed - 1) <= threshold) {
            anomalyIds.add(points.pointId(points.pointCount() - 1));
        }

        marked += markAnomalies(anomalyIds);
        logger.debug("Filtered {} points due to excessive speed (median: {} m/s, threshold: {} m/s)",
                marked, medianSpeed, threshold);
        return marked;
    }

    private int markAnomalies(List<Long> anomalyIds) {
        if (anomalyIds.isEmpty()) {
            return 0;
        }
        int marked = repository.bulkUpdateInvalidStatus(anomalyIds);
        anomalyIds.clear();
        return marked;
    }

    private static double calculateSpeed(SourceLocationPoint p1, SourceLocationPoint p2) {
        long timeDiffSeconds = Math.abs(ChronoUnit.SECONDS.between(p1.getTimestamp(), p2.getTimestamp()));
        if (timeDiffSeconds == 0) {
            return -1;
        }
        return GeoUtils.distanceInMeters(p1, p2) / timeDiffSeconds;
    }

    private static final class SpeedSeries {
        private long[] pointIds;
        private double[] speeds;
        private final BitSet accuracyAnomalies = new BitSet();
        private SourceLocationPoint previous;
        private int pointCount;
        private int speedCount;

        private SpeedSeries(int initialCapacity) {
            pointIds = new long[initialCapacity];
            speeds = new double[initialCapacity];
        }

        private void add(SourceLocationPoint current, double maxAccuracyMeters) {
            if (pointCount == pointIds.length) {
                pointIds = Arrays.copyOf(pointIds, pointIds.length * 2);
            }
            if (current.getAccuracyMeters() > maxAccuracyMeters) {
                accuracyAnomalies.set(pointCount);
            }
            pointIds[pointCount++] = current.getId();

            if (previous != null) {
                if (speedCount == speeds.length) {
                    speeds = Arrays.copyOf(speeds, speeds.length * 2);
                }
                speeds[speedCount++] = calculateSpeed(previous, current);
            }
            previous = current;
        }

        private int pointCount() {
            return pointCount;
        }

        private int speedCount() {
            return speedCount;
        }

        private long pointId(int index) {
            return pointIds[index];
        }

        private boolean isAccuracyAnomaly(int index) {
            return accuracyAnomalies.get(index);
        }

        private double speedAfter(int index) {
            return speeds[index];
        }

        private double medianSpeed() {
            double[] sortedSpeeds = Arrays.copyOf(speeds, speedCount);
            Arrays.sort(sortedSpeeds);
            int middle = speedCount / 2;
            if (speedCount % 2 == 0) {
                return (sortedSpeeds[middle - 1] + sortedSpeeds[middle]) / 2.0;
            }
            return sortedSpeeds[middle];
        }
    }
}
