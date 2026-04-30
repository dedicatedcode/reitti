package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.importer.dto.GoogleTimelineData;
import com.dedicatedcode.reitti.service.importer.dto.SemanticSegment;
import com.dedicatedcode.reitti.service.importer.dto.TimelinePathPoint;
import com.dedicatedcode.reitti.service.importer.dto.Visit;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleAndroidTimelineImporter extends BaseGoogleTimelineImporter {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAndroidTimelineImporter.class);
    private final ImportStateHolder stateHolder;
    private final Task<PromotionJobHandler.PromotionTaskData> promotionTask;
    private final JobSchedulingService jobSchedulingService;
    private final int graceTimeSeconds;

    public GoogleAndroidTimelineImporter(ObjectMapper objectMapper,
                                         ImportStateHolder stateHolder,
                                         LocationPointStagingService stagingService,
                                         Task<PromotionJobHandler.PromotionTaskData> promotionTask,
                                         JobSchedulingService jobSchedulingService,
                                         @Value("${reitti.import.grace-time-seconds:300}") int graceTimeSeconds) {
        super(objectMapper, stagingService);
        this.stateHolder = stateHolder;
        this.promotionTask = promotionTask;
        this.jobSchedulingService = jobSchedulingService;
        this.graceTimeSeconds = graceTimeSeconds;
    }

    public Map<String, Object> importTimeline(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            String partitionKey = UUID.randomUUID().toString();
            this.stagingService.ensurePartitionExists(partitionKey);

            logger.info("Importing Google Timeline Android file for user {}", user.getUsername());
            this.stateHolder.importStarted();
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);

            List<LocationPoint> batch = new ArrayList<>(stagingService.getBatchSize());

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
                        latLng.ifPresent(lng -> processedCount.addAndGet(handleVisit(partitionKey, user, device, start, end, lng, batch)));
                    }
                }

                if (semanticSegment.getTimelinePath() != null) {
                    List<TimelinePathPoint> timelinePath = semanticSegment.getTimelinePath();
                    logger.info("Found timeline path from start [{}] to end [{}]. Will insert [{}] geo locations based on timeline path.", semanticSegment.getStartTime(), semanticSegment.getEndTime(), timelinePath.size());
                    for (TimelinePathPoint timelinePathPoint : timelinePath) {
                        parseLatLng(timelinePathPoint.getPoint()).ifPresent(location -> {
                            createAndScheduleLocationPoint(location, ZonedDateTime.parse(timelinePathPoint.getTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0), partitionKey, user, device, batch);
                            processedCount.incrementAndGet();
                        });
                    }
                }
            }

            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(partitionKey, user, device, batch);
            }

            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.GOOGLE_TIMELINE_IMPORT)
                    .friendlyName("GPS Data Promotion").build();
            jobSchedulingService.scheduleTask(promotionTask,
                                              new PromotionJobHandler.PromotionTaskData(user, device, partitionKey, true),
                                              Instant.now().plusSeconds(graceTimeSeconds),
                                              metadata);

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
