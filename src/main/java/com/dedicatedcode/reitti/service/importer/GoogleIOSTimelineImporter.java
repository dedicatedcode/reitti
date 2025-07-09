package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.importer.dto.ios.IOSSemanticSegment;
import com.dedicatedcode.reitti.service.importer.dto.ios.IOSVisit;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleIOSTimelineImporter extends BaseGoogleTimelineImporter {
    private static final Logger logger = LoggerFactory.getLogger(GoogleIOSTimelineImporter.class);
    private final ImportStateHolder stateHolder;

    public GoogleIOSTimelineImporter(ObjectMapper objectMapper,
                                     ImportStateHolder stateHolder,
                                     ImportBatchProcessor batchProcessor,
                                     @Value("${reitti.staypoint.min-points}") int minStayPointDetectionPoints,
                                     @Value("${reitti.staypoint.distance-threshold-meters}") int distanceThresholdMeters,
                                     @Value("${reitti.visit.merge-threshold-seconds}") int mergeThresholdSeconds) {
        super(objectMapper, batchProcessor, minStayPointDetectionPoints, distanceThresholdMeters, mergeThresholdSeconds);
        this.stateHolder = stateHolder;
    }

    public Map<String, Object> importTimeline(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);

        try {
            stateHolder.importStarted();
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);

            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());

            List<IOSSemanticSegment> semanticSegments = objectMapper.readValue(parser, new TypeReference<>() {});
            logger.info("Found {} semantic segments", semanticSegments.size());
            for (IOSSemanticSegment semanticSegment : semanticSegments) {
                //2024-01-01T00:33:18+01:00
                //2024-01-01T00:33:18+01:00


                ZonedDateTime start = ZonedDateTime.parse(semanticSegment.getStartTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0);
                ZonedDateTime end = ZonedDateTime.parse(semanticSegment.getEndTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0);
                if (semanticSegment.getVisit() != null) {
                    IOSVisit visit = semanticSegment.getVisit();
                    Optional<LatLng> latLng = parseLatLng(visit.getTopCandidate().getPlaceLocation());
                    latLng.ifPresent(lng -> processedCount.addAndGet(handleVisit(user, start, end, lng, batch)));
                }

                if (semanticSegment.getTimelinePath() != null) {
                    List<com.dedicatedcode.reitti.service.importer.dto.ios.TimelinePathPoint> timelinePath = semanticSegment.getTimelinePath();
                    logger.info("Found timeline path from start [{}] to end [{}]. Will insert [{}] synthetic geo locations based on timeline path.", semanticSegment.getStartTime(), semanticSegment.getEndTime(), timelinePath.size());
                    for (com.dedicatedcode.reitti.service.importer.dto.ios.TimelinePathPoint timelinePathPoint : timelinePath) {
                        parseLatLng(timelinePathPoint.getPoint()).ifPresent(location -> {
                            ZonedDateTime current = start.plusMinutes(Long.parseLong(timelinePathPoint.getDurationMinutesOffsetFromStartTime()));
                            createAndScheduleLocationPoint(location, current, user, batch);
                            processedCount.incrementAndGet();
                        });
                    }
                }
            }

            // Process any remaining locations
            if (!batch.isEmpty()) {
                batchProcessor.sendToQueue(user, batch);
            }

            logger.info("Successfully imported and queued {} location points from Google Timeline for user {}",
                    processedCount.get(), user.getUsername());

            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );

        } catch (IOException e) {
            logger.error("Error processing Google Timeline file", e);
            return Map.of("success", false, "error", "Error processing Google Timeline file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
}
