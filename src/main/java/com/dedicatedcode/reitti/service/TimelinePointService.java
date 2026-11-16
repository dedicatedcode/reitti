package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PreviewRawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointStream;
import com.dedicatedcode.reitti.service.processing.SyntheticPointGeneratorService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides point streams for the curated timeline. Synthetic gap-fill points
 * are generated in memory and never persisted in the main point tables.
 */
@Service
public class TimelinePointService {
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    private final SyntheticPointGeneratorService syntheticPointGeneratorService;
    private final VisitDetectionParametersService visitDetectionParametersService;

    public TimelinePointService(RawLocationPointJdbcService rawLocationPointJdbcService,
                                PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService,
                                SyntheticPointGeneratorService syntheticPointGeneratorService,
                                VisitDetectionParametersService visitDetectionParametersService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.previewRawLocationPointJdbcService = previewRawLocationPointJdbcService;
        this.syntheticPointGeneratorService = syntheticPointGeneratorService;
        this.visitDetectionParametersService = visitDetectionParametersService;
    }

    public RawLocationPointStream stream(User user, Instant start, Instant end,
                                         boolean includeSynthetic, boolean includeIgnored) {
        if (!includeSynthetic) {
            return rawLocationPointJdbcService.streamByUserAndTimestampBetween(user, start, end, includeIgnored);
        }

        RawLocationPointStream base = rawLocationPointJdbcService
                .streamByUserAndTimestampBetween(user, start, end, true);
        DetectionParameter.LocationDensity density = densityAt(user, start);
        RawLocationPoint leftAnchor = rawLocationPointJdbcService.findLastRealBefore(user, start).orElse(null);
        RawLocationPoint rightAnchor = rawLocationPointJdbcService.findFirstRealAtOrAfter(user, end).orElse(null);
        Instant wallStart = leftAnchor != null ? leftAnchor.getTimestamp() : start;
        Instant wallEnd = rightAnchor != null ? rightAnchor.getTimestamp() : end;
        NavigableSet<Instant> walls = new TreeSet<>(
                rawLocationPointJdbcService.findManuallyIgnoredTimestampsIn(user, wallStart, wallEnd));
        Set<Instant> ignoredOutput = includeIgnored
                ? Collections.emptySet()
                : Set.copyOf(rawLocationPointJdbcService.findIgnoredTimestampsIn(user, start, end));

        return base.withDecorator(iterator -> new GapFillingIterator(
                iterator, syntheticPointGeneratorService, density, walls,
                ignoredOutput, includeIgnored, start, end, leftAnchor, rightAnchor));
    }

    public RawLocationPointStream streamPreview(User user, String previewId, Instant start, Instant end,
                                                boolean includeSynthetic) {
        RawLocationPointStream base = previewRawLocationPointJdbcService.streamByUserAndTimestampBetween(user, previewId, start, end);
        if (!includeSynthetic) {
            return base;
        }

        DetectionParameter.LocationDensity density = densityAt(user, start);
        RawLocationPoint leftAnchor = previewRawLocationPointJdbcService.findLastBefore(user, previewId, start).orElse(null);
        RawLocationPoint rightAnchor = previewRawLocationPointJdbcService.findFirstAtOrAfter(user, previewId, end).orElse(null);
        Instant wallStart = leftAnchor != null ? leftAnchor.getTimestamp() : start;
        Instant wallEnd = rightAnchor != null ? rightAnchor.getTimestamp() : end;
        NavigableSet<Instant> walls = new TreeSet<>(
                rawLocationPointJdbcService.findManuallyIgnoredTimestampsIn(user, wallStart, wallEnd));

        return base.withDecorator(iterator -> new GapFillingIterator(
                iterator, syntheticPointGeneratorService, density, walls,
                Collections.emptySet(), true, start, end, leftAnchor, rightAnchor));
    }

    public List<RawLocationPoint> list(User user, Instant start, Instant end,
                                       boolean includeSynthetic, boolean includeIgnored) {
        List<RawLocationPoint> result = new java.util.ArrayList<>();
        for (RawLocationPoint point : stream(user, start, end, includeSynthetic, includeIgnored)) {
            result.add(point);
        }
        return result;
    }

    private DetectionParameter.LocationDensity densityAt(User user, Instant start) {
        return visitDetectionParametersService.getCurrentConfiguration(user, start).getLocationDensity();
    }

    private static final class GapFillingIterator implements Iterator<RawLocationPoint> {
        private final Iterator<RawLocationPoint> base;
        private final SyntheticPointGeneratorService generator;
        private final DetectionParameter.LocationDensity density;
        private final NavigableSet<Instant> walls;
        private final Set<Instant> ignoredOutput;
        private final boolean includeIgnored;
        private final Instant start;
        private final Instant end;
        private final RawLocationPoint leftAnchor;
        private final RawLocationPoint rightAnchor;
        private final ArrayDeque<RawLocationPoint> pending = new ArrayDeque<>();

        private RawLocationPoint current;
        private boolean initialized;
        private boolean finished;

        private GapFillingIterator(Iterator<RawLocationPoint> base,
                                   SyntheticPointGeneratorService generator,
                                   DetectionParameter.LocationDensity density,
                                   NavigableSet<Instant> walls,
                                   Set<Instant> ignoredOutput,
                                   boolean includeIgnored,
                                   Instant start,
                                   Instant end,
                                   RawLocationPoint leftAnchor,
                                   RawLocationPoint rightAnchor) {
            this.base = base;
            this.generator = generator;
            this.density = density;
            this.walls = walls;
            this.ignoredOutput = ignoredOutput;
            this.includeIgnored = includeIgnored;
            this.start = start;
            this.end = end;
            this.leftAnchor = leftAnchor;
            this.rightAnchor = rightAnchor;
        }

        @Override
        public boolean hasNext() {
            if (!initialized) {
                initialize();
            }
            while (pending.isEmpty() && !finished) {
                advance();
            }
            return !pending.isEmpty();
        }

        @Override
        public RawLocationPoint next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.poll();
        }

        private void initialize() {
            initialized = true;
            if (!base.hasNext()) {
                finished = true;
                return;
            }
            current = base.next();
            enqueueGap(leftAnchor, current);
        }

        private void advance() {
            if (current == null) {
                finished = true;
                return;
            }
            if (includeIgnored || !ignoredOutput.contains(current.getTimestamp())) {
                pending.add(current);
            }
            if (base.hasNext()) {
                RawLocationPoint next = base.next();
                enqueueGap(current, next);
                current = next;
            } else {
                enqueueGap(current, rightAnchor);
                current = null;
                finished = true;
            }
        }

        private void enqueueGap(RawLocationPoint from, RawLocationPoint to) {
            if (from == null || to == null) {
                return;
            }
            for (RawLocationPoint point : generator.generateGap(from, to, density, walls)) {
                if (!point.getTimestamp().isBefore(start) && point.getTimestamp().isBefore(end)) {
                    pending.add(point);
                }
            }
        }
    }
}
