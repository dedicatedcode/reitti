package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ChunkFeatureExtractor {

    static final long CHUNK_SECONDS = 60;
    private static final long WINDOW_PADDING_SECONDS = 30;
    private static final long MAX_WINDOW_EXPANSION_SECONDS = 300;
    private static final double STOP_SPEED_KMH = 2.0;

    public List<ChunkFeatures> extract(List<RawLocationPoint> points, Instant first, Instant last) {
        if (points.size() < 2) {
            return List.of();
        }
        long totalDuration = Math.max(1, Duration.between(first, last).getSeconds());
        List<ChunkFeatures> result = new ArrayList<>();
        for (long offset = 0; offset < totalDuration; offset += CHUNK_SECONDS) {
            long chunkEnd = Math.min(offset + CHUNK_SECONDS, totalDuration);
            List<RawLocationPoint> window = window(points, first, totalDuration, offset, chunkEnd);
            result.add(features(window, first, offset, chunkEnd));
        }
        return result;
    }

    private List<RawLocationPoint> window(List<RawLocationPoint> points, Instant first, long totalDuration,
                                          long offset, long chunkEnd) {
        long start = Math.max(0, offset - WINDOW_PADDING_SECONDS);
        long end = Math.min(totalDuration, chunkEnd + WINDOW_PADDING_SECONDS);
        List<RawLocationPoint> window = pointsBetween(points, first, start, end);
        long expansion = WINDOW_PADDING_SECONDS;
        while (window.size() < 2 && expansion < MAX_WINDOW_EXPANSION_SECONDS) {
            expansion += WINDOW_PADDING_SECONDS;
            start = Math.max(0, offset - expansion);
            end = Math.min(totalDuration, chunkEnd + expansion);
            window = pointsBetween(points, first, start, end);
        }
        return window;
    }

    private List<RawLocationPoint> pointsBetween(List<RawLocationPoint> points, Instant first, long start, long end) {
        Instant startTime = first.plusSeconds(start);
        Instant endTime = first.plusSeconds(end);
        List<RawLocationPoint> result = new ArrayList<>();
        for (RawLocationPoint point : points) {
            Instant timestamp = point.getTimestamp();
            if (!timestamp.isBefore(startTime) && !timestamp.isAfter(endTime)) {
                result.add(point);
            }
        }
        return result;
    }

    private ChunkFeatures features(List<RawLocationPoint> window, Instant first, long offset, long chunkEnd) {
        if (window.size() < 2) {
            return new ChunkFeatures(offset, chunkEnd, 0, 0, 0, 1, 0, 0, 0, window.size());
        }

        Instant coreStart = first.plusSeconds(offset);
        Instant coreEnd = first.plusSeconds(chunkEnd);

        List<SpeedSample> windowSamples = new ArrayList<>();
        List<SpeedSample> coreSamples = new ArrayList<>();
        double pathLength = 0;
        double stopSeconds = 0;
        double totalSeconds = 0;
        double accuracySum = 0;

        for (int i = 1; i < window.size(); i++) {
            RawLocationPoint previous = window.get(i - 1);
            RawLocationPoint current = window.get(i);
            double distance = GeoUtils.distanceInMeters(previous, current);
            double seconds = Duration.between(previous.getTimestamp(), current.getTimestamp()).toMillis() / 1000.0;
            pathLength += distance;

            double accuracy = averageAccuracy(previous, current);
            accuracySum += accuracy;

            if (seconds <= 0) {
                continue;
            }
            double speed = distance / seconds * 3.6;
            SpeedSample sample = new SpeedSample(speed, 1.0 / (accuracy * accuracy));
            windowSamples.add(sample);
            if (!current.getTimestamp().isBefore(coreStart) && !current.getTimestamp().isAfter(coreEnd)) {
                coreSamples.add(sample);
            }
            totalSeconds += seconds;
            if (speed < STOP_SPEED_KMH) {
                stopSeconds += seconds;
            }
        }

        double netDisplacement = GeoUtils.distanceInMeters(window.getFirst(), window.getLast());
        double sinuosity = netDisplacement > 0.5 ? pathLength / netDisplacement : 1.0;
        double speed = weightedPercentile(windowSamples, 0.5);
        // The peak is taken from the chunk itself, not the padded window, so a neighbouring
        // mode change does not leak into this chunk's peak speed.
        List<SpeedSample> peakSamples = coreSamples.size() >= 2 ? coreSamples : windowSamples;
        double p85 = weightedPercentile(peakSamples, 0.85);
        double variance = weightedVariance(windowSamples, speed);
        double stopFraction = totalSeconds > 0 ? stopSeconds / totalSeconds : 0;
        double meanAccuracy = accuracySum / Math.max(1, window.size() - 1);
        double distance = distanceWithin(window, first, offset, chunkEnd);

        return new ChunkFeatures(offset, chunkEnd, speed, p85, stopFraction, sinuosity, variance, meanAccuracy, distance, window.size());
    }

    private double distanceWithin(List<RawLocationPoint> window, Instant first, long offset, long chunkEnd) {
        Instant start = first.plusSeconds(offset);
        Instant end = first.plusSeconds(chunkEnd);
        double distance = 0;
        for (int i = 1; i < window.size(); i++) {
            RawLocationPoint previous = window.get(i - 1);
            RawLocationPoint current = window.get(i);
            long total = Duration.between(previous.getTimestamp(), current.getTimestamp()).toMillis();
            if (total <= 0) {
                continue;
            }
            long overlapStart = Math.max(previous.getTimestamp().toEpochMilli(), start.toEpochMilli());
            long overlapEnd = Math.min(current.getTimestamp().toEpochMilli(), end.toEpochMilli());
            long overlap = overlapEnd - overlapStart;
            if (overlap > 0) {
                distance += GeoUtils.distanceInMeters(previous, current) * ((double) overlap / total);
            }
        }
        return distance;
    }

    private double averageAccuracy(RawLocationPoint a, RawLocationPoint b) {
        double aAccuracy = a.getAccuracyMeters() == null ? 10.0 : a.getAccuracyMeters();
        double bAccuracy = b.getAccuracyMeters() == null ? 10.0 : b.getAccuracyMeters();
        return (aAccuracy + bAccuracy) / 2.0;
    }

    private double weightedPercentile(List<SpeedSample> samples, double percentile) {
        if (samples.isEmpty()) {
            return 0;
        }
        List<SpeedSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(SpeedSample::speed));
        double totalWeight = sorted.stream().mapToDouble(SpeedSample::weight).sum();
        double target = totalWeight * percentile;
        double cumulative = 0;
        for (SpeedSample sample : sorted) {
            cumulative += sample.weight();
            if (cumulative >= target) {
                return sample.speed();
            }
        }
        return sorted.getLast().speed();
    }

    private double weightedVariance(List<SpeedSample> samples, double mean) {
        if (samples.isEmpty()) {
            return 0;
        }
        double totalWeight = samples.stream().mapToDouble(SpeedSample::weight).sum();
        double sum = 0;
        for (SpeedSample sample : samples) {
            double delta = sample.speed() - mean;
            sum += sample.weight() * delta * delta;
        }
        return totalWeight > 0 ? sum / totalWeight : 0;
    }

    private record SpeedSample(double speed, double weight) {
    }
}
