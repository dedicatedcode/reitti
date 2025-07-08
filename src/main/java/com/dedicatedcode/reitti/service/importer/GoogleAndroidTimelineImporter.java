package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.importer.dto.GoogleTimelineData;
import com.dedicatedcode.reitti.service.importer.dto.SemanticSegment;
import com.dedicatedcode.reitti.service.importer.dto.TimelinePathPoint;
import com.dedicatedcode.reitti.service.importer.dto.Visit;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
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
public class GoogleAndroidTimelineImporter extends BaseGoogleTimelineImporter {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAndroidTimelineImporter.class);
    private final ImportStateHolder stateHolder;

    public GoogleAndroidTimelineImporter(ObjectMapper objectMapper,
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
            this.stateHolder.importStarted();
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());

            GoogleTimelineData timelineData = objectMapper.readValue(parser, GoogleTimelineData.class);
            List<SemanticSegment> semanticSegments = timelineData.getSemanticSegments();
            logger.info("Found {} semantic segments", semanticSegments.size());
            for (SemanticSegment semanticSegment : semanticSegments) {
                ZonedDateTime start = ZonedDateTime.parse(semanticSegment.getStartTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0);
                ZonedDateTime end = ZonedDateTime.parse(semanticSegment.getEndTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0);
                if (semanticSegment.getVisit() != null) {
                    Visit visit = semanticSegment.getVisit();
                    Optional<LatLng> latLng = parseLatLng(visit.getTopCandidate().getPlaceLocation().getLatLng());
                    if (latLng.isPresent()) {
                        latLng.ifPresent(lng -> processedCount.addAndGet(handleVisit(user, start, end, lng, batch)));
                    }
                }

                if (semanticSegment.getTimelinePath() != null) {
                    List<TimelinePathPoint> timelinePath = semanticSegment.getTimelinePath();
                    logger.info("Found timeline path from start [{}] to end [{}]. Will insert [{}] synthetic geo locations based on timeline path.", semanticSegment.getStartTime(), semanticSegment.getEndTime(), timelinePath.size());
                    for (TimelinePathPoint timelinePathPoint : timelinePath) {
                        parseLatLng(timelinePathPoint.getPoint()).ifPresent(location -> {
                            createAndScheduleLocationPoint(location, ZonedDateTime.parse(timelinePathPoint.getTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0), user, batch);
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
