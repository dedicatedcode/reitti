package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;

/**
 * Pure in-memory generation of synthetic gap-fill points.
 */
@Service
public class SyntheticPointGeneratorService {
    private static final double STATIONARY_JITTER_RADIUS_METERS = 15.0;

    private final LocationDensityConfig config;
    private final SyntheticLocationPointGenerator syntheticGenerator;

    public SyntheticPointGeneratorService(LocationDensityConfig config,
                                          SyntheticLocationPointGenerator syntheticGenerator) {
        this.config = config;
        this.syntheticGenerator = syntheticGenerator;
    }

    public List<RawLocationPoint> generateGap(RawLocationPoint from,
                                              RawLocationPoint to,
                                              DetectionParameter.LocationDensity density,
                                              NavigableSet<Instant> walls) {
        long gapSeconds = Duration.between(from.getTimestamp(), to.getTimestamp()).getSeconds();
        int gapThresholdSeconds = config.getGapThresholdSeconds();
        long maxInterpolationSeconds = density.getMaxInterpolationGapMinutes() * 60L;
        long maxInterpolatedGapSeconds = config.getMaxInterpolationGapHours() * 3600L;

        if (gapSeconds <= gapThresholdSeconds || gapSeconds > maxInterpolationSeconds) {
            return List.of();
        }
        if (!walls.subSet(from.getTimestamp(), false, to.getTimestamp(), false).isEmpty()) {
            return List.of();
        }

        List<LocationPoint> generated;
        double distanceMeters = GeoUtils.distanceInMeters(from, to);
        double impliedSpeedMps = distanceMeters / gapSeconds;
        if (impliedSpeedMps <= config.getMaxStationarySpeedMps()) {
            generated = syntheticGenerator.generateStationaryPoints(
                    from, to, config.getTargetPointsPerMinute(), STATIONARY_JITTER_RADIUS_METERS);
        } else if (gapSeconds <= maxInterpolatedGapSeconds) {
            generated = syntheticGenerator.generateSyntheticPoints(
                    from, to, config.getTargetPointsPerMinute());
        } else {
            return List.of();
        }

        List<RawLocationPoint> result = new ArrayList<>(generated.size());
        for (LocationPoint point : generated) {
            result.add(toRawLocationPoint(point));
        }
        return result;
    }

    private RawLocationPoint toRawLocationPoint(LocationPoint point) {
        return new RawLocationPoint(
                -point.getTimestamp().toEpochMilli(),
                null,
                point.getTimestamp(),
                new GeoPoint(point.getLatitude(), point.getLongitude()),
                point.getAccuracyMeters(),
                point.getElevationMeters(),
                true,
                0L
        );
    }
}
