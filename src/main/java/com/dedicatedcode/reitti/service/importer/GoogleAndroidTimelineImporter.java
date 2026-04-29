package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.importer.dto.GoogleTimelineData;
import com.dedicatedcode.reitti.service.importer.dto.SemanticSegment;
import com.dedicatedcode.reitti.service.importer.dto.TimelinePathPoint;
import com.dedicatedcode.reitti.service.importer.dto.Visit;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobBuilder;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleAndroidTimelineImporter extends BaseGoogleTimelineImporter {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAndroidTimelineImporter.class);
    private final ImportStateHolder stateHolder;
    private final JobMetadataRepository jobMetadataRepository;
    private final PromotionJobHandler promotionJobHandler;
    private final JobScheduler jobScheduler;
    private final int graceTimeSeconds;

    public GoogleAndroidTimelineImporter(ObjectMapper objectMapper,
                                         ImportStateHolder stateHolder,
                                         LocationPointStagingService stagingService,
                                         JobMetadataRepository jobMetadataRepository,
                                         PromotionJobHandler promotionJobHandler,
                                         JobScheduler jobScheduler,
                                         @Value("${reitti.import.grace-time-seconds:300}") int graceTimeSeconds) {
        super(objectMapper, stagingService);
        this.stateHolder = stateHolder;
        this.jobMetadataRepository = jobMetadataRepository;
        this.promotionJobHandler = promotionJobHandler;
        this.jobScheduler = jobScheduler;
        this.graceTimeSeconds = graceTimeSeconds;
    }

    public Map<String, Object> importTimeline(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            String importJobId = UUID.randomUUID().toString();
            this.stagingService.ensurePartitionExists(importJobId);
            // Create non-JobRunr import job metadata
            this.jobMetadataRepository.insert(UUID.fromString(importJobId), user.getId(), JobType.GOOGLE_TIMELINE_IMPORT, 
                    "Google Timeline Import", JobState.PREPARING.name(), Instant.now(), null);

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
                        latLng.ifPresent(lng -> processedCount.addAndGet(handleVisit(importJobId, user, device, start, end, lng, batch)));
                    }
                }

                if (semanticSegment.getTimelinePath() != null) {
                    List<TimelinePathPoint> timelinePath = semanticSegment.getTimelinePath();
                    logger.info("Found timeline path from start [{}] to end [{}]. Will insert [{}] geo locations based on timeline path.", semanticSegment.getStartTime(), semanticSegment.getEndTime(), timelinePath.size());
                    for (TimelinePathPoint timelinePathPoint : timelinePath) {
                        parseLatLng(timelinePathPoint.getPoint()).ifPresent(location -> {
                            createAndScheduleLocationPoint(location, ZonedDateTime.parse(timelinePathPoint.getTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).withNano(0), importJobId, user, device, batch);
                            processedCount.incrementAndGet();
                        });
                    }
                }
            }

            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(importJobId, user, device, batch);
            }

            // Update import job state to AWAITING
            jobMetadataRepository.updateState(UUID.fromString(importJobId), JobState.AWAITING.name(), Instant.now());

            // Create promotion JobRunr job with metadata
            Job promotionJob = JobBuilder.aJob()
                    .withId(UUID.randomUUID())
                    .withName("Promote imported points to devices table")
                    .withDetails(() -> promotionJobHandler.execute(user, device, importJobId, true, JobContext.Null))
                    .withMetadata("userId", user.getId())
                    .withMetadata("jobType", JobType.GOOGLE_TIMELINE_IMPORT.name())
                    .withMetadata("friendlyName", "Google Timeline Import Promotion")
                    .build();

            if (graceTimeSeconds > 0) {
                LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(graceTimeSeconds);
                jobScheduler.schedule(promotionJob, scheduledTime);
            } else {
                jobScheduler.enqueue(promotionJob);
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
