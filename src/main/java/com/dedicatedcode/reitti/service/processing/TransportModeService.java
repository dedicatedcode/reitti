package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointStream;
import com.dedicatedcode.reitti.repository.TransportModeJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService;
import com.dedicatedcode.reitti.repository.TransportModeOverrideJdbcService.TransportModeOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransportModeService {
    private static final Logger log = LoggerFactory.getLogger(TransportModeService.class);
    private static final long MIN_SEGMENT_SECONDS = 120;
    private static final double MIN_MOTORIZED_AVERAGE_SPEED_KMH = 30.0;
    private static final long MAX_BOUNDARY_ABSORPTION_SECONDS = 300;
    private final TransportModeJdbcService transportModeJdbcService;
    private final TransportModeOverrideJdbcService transportModeOverrideJdbcService;
    private final TransportModeClassifier classifier;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService,
                                TransportModeOverrideJdbcService transportModeOverrideJdbcService,
                                TransportModeClassifier classifier) {
        this.transportModeJdbcService = transportModeJdbcService;
        this.transportModeOverrideJdbcService = transportModeOverrideJdbcService;
        this.classifier = classifier;
    }

    public void overrideTransportModeSegment(User user, TransportMode transportMode, Trip trip, long offsetSeconds, long durationSeconds) {
        Instant segmentStart = trip.getStartTime().plusSeconds(offsetSeconds);
        Instant segmentEnd = segmentStart.plusSeconds(durationSeconds);
        transportModeOverrideJdbcService.addTransportModeOverride(user, transportMode, segmentStart, segmentEnd);
    }

    public boolean isManualOverride(User user, Instant segmentStart, Instant segmentEnd) {
        return transportModeOverrideJdbcService.getTransportModeOverride(user, segmentStart, segmentEnd).isPresent();
    }

    public List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd) {
        List<TransportModeConfig> configs = configuredModes(user);
        double totalDistanceMeters = GeoUtils.calculateTripDistance(points);
        return segmentTrip(user, points, tripStart, tripEnd, configs, totalDistanceMeters);
    }

    public List<TransportModeSegment> segmentTrip(User user, RawLocationPointStream points, Instant tripStart, Instant tripEnd) {
        List<TransportModeConfig> configs = configuredModes(user);
        double totalDistanceMeters = GeoUtils.calculateTripDistance(points.iterator());
        List<RawLocationPoint> collected = new ArrayList<>();
        points.iterator().forEachRemaining(collected::add);
        return segmentTrip(user, collected, tripStart, tripEnd, configs, totalDistanceMeters);
    }

    private List<TransportModeSegment> segmentTrip(User user, List<RawLocationPoint> points, Instant tripStart, Instant tripEnd,
                                                   List<TransportModeConfig> configs, double totalDistanceMeters) {
        if (points.size() < 2) {
            long duration = Math.max(1, Duration.between(tripStart, tripEnd).getSeconds());
            TransportMode fallbackMode = slowestConfiguredMode(configs);
            log.debug("segmentTrip: only {} point(s), returning single {} segment, duration={}s", points.size(), fallbackMode, duration);
            return List.of(new TransportModeSegment(fallbackMode, 0L, duration, totalDistanceMeters, 0.0));
        }

        List<TransportModeOverride> overrides = transportModeOverrideJdbcService.getTransportModeOverrides(user, tripStart, tripEnd);
        double sensitivity = transportModeJdbcService.getModeChangeSensitivity(user);

        List<TransportModeSegment> result = classifier.classify(
                points, points.getFirst().getTimestamp(), points.getLast().getTimestamp(), configs, sensitivity);

        if (result.isEmpty()) {
            long duration = Math.max(1, Duration.between(tripStart, tripEnd).getSeconds());
            TransportMode fallbackMode = slowestConfiguredMode(configs);
            log.debug("segmentTrip: classifier returned no segments, returning single {} segment, duration={}s", fallbackMode, duration);
            return List.of(new TransportModeSegment(fallbackMode, 0L, duration, totalDistanceMeters, 0.0));
        }

        long tripDuration = Math.max(1, Duration.between(tripStart, tripEnd).getSeconds());
        result = mergeSameModeSegments(result);
        result = mergeShortInteriorSegments(result, configs);
        result = absorbSlowerRunsBetweenSameMode(result, configs);
        result = absorbSlowerBoundaryRuns(result, configs, totalDistanceMeters, tripDuration);

        long firstPointOffset = Duration.between(tripStart, points.getFirst().getTimestamp()).getSeconds();
        if (firstPointOffset > 0) {
            result = result.stream()
                    .map(segment -> new TransportModeSegment(
                            segment.mode(),
                            segment.offsetSeconds() + firstPointOffset,
                            segment.durationSeconds(),
                            segment.distanceMeters(),
                            segment.confidence()))
                    .toList();
        }

        result = fillTripBounds(result, tripDuration);
        if (result.isEmpty()) {
            TransportMode fallbackMode = slowestConfiguredMode(configs);
            return List.of(new TransportModeSegment(fallbackMode, 0L, tripDuration, totalDistanceMeters, 0.0));
        }
        result = applyOverrides(result, tripStart, overrides);

        log.trace("segmentTrip: {} result segments: {}", result.size(), segmentSummary(result));
        return result;
    }

    private List<TransportModeConfig> configuredModes(User user) {
        List<TransportModeConfig> configs = transportModeJdbcService.getTransportModeConfigs(user);
        List<TransportModeConfig> enabled = configs.stream().filter(TransportModeConfig::enabled).toList();
        return enabled.isEmpty() ? configs : enabled;
    }

    private List<TransportModeSegment> fillTripBounds(List<TransportModeSegment> segments, long tripDuration) {
        if (segments.isEmpty()) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>(segments);

        TransportModeSegment first = result.getFirst();
        if (first.offsetSeconds() > 0) {
            result.set(0, new TransportModeSegment(
                    first.mode(),
                    0L,
                    first.durationSeconds() + first.offsetSeconds(),
                    first.distanceMeters(),
                    first.confidence()));
        }

        TransportModeSegment last = result.getLast();
        long end = last.offsetSeconds() + last.durationSeconds();
        if (last.offsetSeconds() >= tripDuration) {
            result.remove(result.size() - 1);
        } else if (end > tripDuration) {
            result.set(result.size() - 1, new TransportModeSegment(
                    last.mode(),
                    last.offsetSeconds(),
                    tripDuration - last.offsetSeconds(),
                    last.distanceMeters(),
                    last.confidence()));
        } else if (end < tripDuration) {
            result.set(result.size() - 1, new TransportModeSegment(
                    last.mode(),
                    last.offsetSeconds(),
                    last.durationSeconds() + (tripDuration - end),
                    last.distanceMeters(),
                    last.confidence()));
        }
        return result;
    }

    private List<TransportModeSegment> applyOverrides(List<TransportModeSegment> segments, Instant tripStart,
                                                      List<TransportModeOverride> overrides) {
        if (overrides.isEmpty()) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>();
        for (TransportModeSegment segment : segments) {
            Instant segmentStart = tripStart.plusSeconds(segment.offsetSeconds());
            Instant segmentEnd = segmentStart.plusSeconds(segment.durationSeconds());

            TransportMode overriddenMode = null;
            for (TransportModeOverride override : overrides) {
                if (!override.time().isBefore(segmentStart) && override.time().isBefore(segmentEnd)) {
                    overriddenMode = override.mode();
                }
            }
            if (overriddenMode != null) {
                log.trace("applyOverrides: segment at +{}s+{}s overridden {} → {}",
                        segment.offsetSeconds(), segment.durationSeconds(), segment.mode(), overriddenMode);
                result.add(new TransportModeSegment(overriddenMode, segment.offsetSeconds(),
                        segment.durationSeconds(), segment.distanceMeters(), 1.0));
            } else {
                result.add(segment);
            }
        }
        return mergeSameModeSegments(result);
    }

    private TransportMode slowestConfiguredMode(List<TransportModeConfig> configs) {
        return configs.stream()
                .min(Comparator.comparingDouble(TransportModeConfig::minKmh)
                        .thenComparingDouble(TransportModeConfig::maxKmh))
                .map(TransportModeConfig::mode)
                .orElse(TransportMode.WALKING);
    }

    private String segmentSummary(List<TransportModeSegment> segments) {
        return segments.stream()
                .map(s -> s.mode() + "@" + s.offsetSeconds() + "+" + s.durationSeconds() + "s")
                .collect(Collectors.joining(", "));
    }

    public List<TransportModeSegment> mergeSameModeSegments(List<TransportModeSegment> segments) {
        if (segments.size() < 2) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>();
        TransportModeSegment current = segments.getFirst();
        for (int i = 1; i < segments.size(); i++) {
            TransportModeSegment next = segments.get(i);
            if (current.mode() == next.mode()) {
                current = new TransportModeSegment(
                        current.mode(),
                        current.offsetSeconds(),
                        current.durationSeconds() + next.durationSeconds(),
                        current.distanceMeters() + next.distanceMeters(),
                        weightedConfidence(current, next));
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    private List<TransportModeSegment> mergeShortInteriorSegments(List<TransportModeSegment> segments,
                                                                  List<TransportModeConfig> configs) {
        if (segments.size() < 3) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>(segments);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 1; i < result.size() - 1; i++) {
                TransportModeSegment current = result.get(i);
                if (current.durationSeconds() >= MIN_SEGMENT_SECONDS) {
                    continue;
                }
                TransportModeSegment left = result.get(i - 1);
                TransportModeSegment right = result.get(i + 1);
                int leftDistance = Math.abs(band(current.mode(), configs) - band(left.mode(), configs));
                int rightDistance = Math.abs(band(current.mode(), configs) - band(right.mode(), configs));
                boolean mergeLeft = leftDistance < rightDistance
                        || (leftDistance == rightDistance && left.durationSeconds() >= right.durationSeconds());
                if (mergeLeft) {
                    result.set(i - 1, mergeWithLeft(left, current));
                    result.remove(i);
                } else {
                    result.set(i + 1, mergeWithRight(current, right));
                    result.remove(i);
                }
                changed = true;
                break;
            }
            if (changed) {
                result = mergeSameModeSegments(result);
            }
        }
        return result;
    }

    /**
     * A slower mode sandwiched between two runs of the same faster mode is almost always a
     * classification artefact rather than a real mode change (for example leaving the motorway
     * and driving slower for a while). Keep the surrounding mode instead.
     */
    private List<TransportModeSegment> absorbSlowerRunsBetweenSameMode(List<TransportModeSegment> segments,
                                                                       List<TransportModeConfig> configs) {
        if (segments.size() < 3) {
            return segments;
        }
        List<TransportModeSegment> result = new ArrayList<>(segments);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 1; i < result.size() - 1; i++) {
                TransportModeSegment left = result.get(i - 1);
                TransportModeSegment current = result.get(i);
                TransportModeSegment right = result.get(i + 1);
                if (left.mode() == right.mode()
                        && band(current.mode(), configs) < band(left.mode(), configs)) {
                    result.set(i - 1, mergeWithLeft(left, current));
                    result.remove(i);
                    changed = true;
                    break;
                }
            }
            if (changed) {
                result = mergeSameModeSegments(result);
            }
        }
        return result;
    }

    /**
     * On a clearly motorized trip a short slow lead-in or tail is usually the start or end of
     * the drive (city traffic, parking), not a separate bicycle leg. Walking is left alone so
     * that a real walk to or from the car is preserved.
     */
    private List<TransportModeSegment> absorbSlowerBoundaryRuns(List<TransportModeSegment> segments,
                                                                List<TransportModeConfig> configs,
                                                                double totalDistanceMeters,
                                                                long tripDuration) {
        if (segments.size() < 2 || tripDuration <= 0) {
            return segments;
        }
        double averageSpeedKmh = totalDistanceMeters / tripDuration * 3.6;
        if (averageSpeedKmh < MIN_MOTORIZED_AVERAGE_SPEED_KMH) {
            return segments;
        }

        long maxBoundaryDuration = Math.min(MAX_BOUNDARY_ABSORPTION_SECONDS,
                Math.max(60, (long) (tripDuration * 0.1)));
        List<TransportModeSegment> result = new ArrayList<>(segments);

        TransportModeSegment first = result.getFirst();
        TransportModeSegment second = result.get(1);
        if (first.mode() == TransportMode.CYCLING
                && first.durationSeconds() <= maxBoundaryDuration
                && band(first.mode(), configs) < band(second.mode(), configs)) {
            result.set(1, mergeWithRight(first, second));
            result.remove(0);
        }

        if (result.size() >= 2) {
            TransportModeSegment last = result.getLast();
            TransportModeSegment previous = result.get(result.size() - 2);
            if (last.mode() == TransportMode.CYCLING
                    && last.durationSeconds() <= maxBoundaryDuration
                    && band(last.mode(), configs) < band(previous.mode(), configs)) {
                result.set(result.size() - 2, mergeWithLeft(previous, last));
                result.remove(result.size() - 1);
            }
        }
        return mergeSameModeSegments(result);
    }

    private TransportModeSegment mergeWithLeft(TransportModeSegment kept, TransportModeSegment absorbed) {
        return new TransportModeSegment(
                kept.mode(),
                kept.offsetSeconds(),
                kept.durationSeconds() + absorbed.durationSeconds(),
                kept.distanceMeters() + absorbed.distanceMeters(),
                kept.confidence());
    }

    private TransportModeSegment mergeWithRight(TransportModeSegment absorbed, TransportModeSegment kept) {
        return new TransportModeSegment(
                kept.mode(),
                absorbed.offsetSeconds(),
                absorbed.durationSeconds() + kept.durationSeconds(),
                absorbed.distanceMeters() + kept.distanceMeters(),
                kept.confidence());
    }

    private int band(TransportMode mode, List<TransportModeConfig> configs) {
        List<TransportModeConfig> sorted = new ArrayList<>(configs);
        sorted.sort(Comparator.comparingDouble(config -> (config.minKmh() + config.maxKmh()) / 2.0));
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).mode() == mode) {
                return i;
            }
        }
        return sorted.size();
    }

    private double weightedConfidence(TransportModeSegment first, TransportModeSegment second) {
        long total = first.durationSeconds() + second.durationSeconds();
        if (total <= 0) {
            return (first.confidence() + second.confidence()) / 2.0;
        }
        return (first.confidence() * first.durationSeconds() + second.confidence() * second.durationSeconds()) / total;
    }
}
