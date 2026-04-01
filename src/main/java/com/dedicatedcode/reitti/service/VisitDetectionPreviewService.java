package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.queue.RedisQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.dedicatedcode.reitti.service.MessageDispatcherService.TRIGGER_PROCESSING_QUEUE;

@Service
public class VisitDetectionPreviewService {
    private static final Logger log = LoggerFactory.getLogger(VisitDetectionPreviewService.class);
    private static final int MAX_PREVIEW_ENTRIES = 1000;
    private static final long READY_THRESHOLD_SECONDS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final RedisQueueService messageEnqueuer;
    private final Map<String, Instant> previewLastUpdated = new ConcurrentHashMap<>();

    public VisitDetectionPreviewService(JdbcTemplate jdbcTemplate, RedisQueueService messageEnqueuer) {
        this.jdbcTemplate = jdbcTemplate;
        this.messageEnqueuer = messageEnqueuer;
    }

    public String startPreview(User user, DetectionParameter config, Instant date) {
        log.info("Starting preview process for user {}", user.getId());
        LocalDateTime now = LocalDateTime.now();

        String previewId = UUID.randomUUID().toString();
        this.jdbcTemplate.update("""
                        INSERT INTO preview_visit_detection_parameters(user_id, valid_since, detection_minimum_stay_time_seconds,
                        detection_max_merge_time_between_same_stay_points, merging_search_duration_in_hours, merging_max_merge_time_between_same_visits, place_radius_meters, preview_id, preview_created_at)
                        VALUES (?,?,?,?,?,?,?,?,?)""",
                user.getId(),
                config.getValidSince() != null ? Timestamp.from(config.getValidSince()) : null,
                config.getVisitDetection().getMinimumStayTimeInSeconds(),
                config.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
                config.getVisitMerging().getSearchDurationInHours(),
                config.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
                config.getVisitMerging().getPlaceRadiusMeters(),
                previewId,
                Timestamp.valueOf(now)
        );

        Timestamp start = Timestamp.from(date.minus(config.getVisitMerging().getSearchDurationInHours() * 2, ChronoUnit.HOURS));
        Timestamp end = Timestamp.from(date.plus(1, ChronoUnit.DAYS).plus(config.getVisitMerging().getSearchDurationInHours() * 2, ChronoUnit.HOURS));
        this.jdbcTemplate.update("INSERT INTO preview_raw_location_points(accuracy_meters, timestamp, user_id, elevation_meters, geom, processed, version, ignored, synthetic, preview_id, preview_created_at) " +
                "SELECT accuracy_meters, timestamp, user_id, elevation_meters, geom, false, version, ignored, synthetic, ?, ? FROM raw_location_points WHERE timestamp > ? AND timestamp <= ? AND user_id = ? AND invalid = false",
                previewId,
                Timestamp.valueOf(now),
                start,
                end,
                user.getId());

        log.debug("Copied preview data user [{}] with previewId [{}] successfully", user.getId(), previewId);
        TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(user.getUsername(), previewId, UUID.randomUUID().toString());
        messageEnqueuer.enqueue(TRIGGER_PROCESSING_QUEUE, triggerEvent);
        
        // Initialize preview status tracking
        updatePreviewStatus(previewId);
        
        return previewId;
    }

    public boolean isPreviewReady(String previewId) {
        Instant lastUpdate = previewLastUpdated.get(previewId);
        if (lastUpdate == null) {
            return false;
        }
        return Instant.now().minusSeconds(READY_THRESHOLD_SECONDS).isAfter(lastUpdate);
    }


    public void updatePreviewStatus(String previewId) {
        if (previewId != null) {
            log.debug("Updating preview status for previewId: {}", previewId);
            previewLastUpdated.put(previewId, Instant.now());
            
            if (previewLastUpdated.size() > MAX_PREVIEW_ENTRIES) {
                Instant cutoff = Instant.now().minusSeconds(3600);
                previewLastUpdated.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            }
        }
    }
}
