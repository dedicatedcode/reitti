package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.TriggerProcessingEvent;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class VisitDetectionPreviewService {
    private static final Logger log = LoggerFactory.getLogger(VisitDetectionPreviewService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    public VisitDetectionPreviewService(JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public String startPreview(User user, DetectionParameter config, Instant date) {
        log.info("Starting preview process for user {}", user.getId());
        LocalDateTime now = LocalDateTime.now();

        String previewId = UUID.randomUUID().toString();
        this.jdbcTemplate.update("""
                        INSERT INTO preview_visit_detection_parameters(user_id, valid_since, detection_search_distance_meters, detection_minimum_adjacent_points, detection_minimum_stay_time_seconds,
                        detection_max_merge_time_between_same_stay_points, merging_search_duration_in_hours, merging_max_merge_time_between_same_visits, merging_min_distance_between_visits, preview_id, preview_created_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                user.getId(),
                config.getValidSince() != null ? Timestamp.from(config.getValidSince()) : null,
                config.getVisitDetection().getSearchDistanceInMeters(),
                config.getVisitDetection().getMinimumAdjacentPoints(),
                config.getVisitDetection().getMinimumStayTimeInSeconds(),
                config.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
                config.getVisitMerging().getSearchDurationInHours(),
                config.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
                config.getVisitMerging().getMinDistanceBetweenVisits(),
                previewId,
                Timestamp.valueOf(now)
        );

        Timestamp start = Timestamp.from(date.minus(config.getVisitMerging().getSearchDurationInHours(), ChronoUnit.HOURS));
        Timestamp end = Timestamp.from(date.plus(1, ChronoUnit.DAYS).plus(config.getVisitMerging().getSearchDurationInHours(), ChronoUnit.HOURS));
        this.jdbcTemplate.update("INSERT INTO preview_raw_location_points(accuracy_meters, timestamp, user_id, geom, processed, version, preview_id, preview_created_at) " +
                "SELECT accuracy_meters, timestamp, user_id, geom, false, version, ?, ? FROM raw_location_points WHERE timestamp > ? AND timestamp <= ? AND user_id = ?",
                previewId,
                Timestamp.valueOf(now),
                start,
                end,
                user.getId());

        log.debug("Copied preview data user [{}] with previewId [{}] successfully", user.getId(), previewId);
        TriggerProcessingEvent triggerEvent = new TriggerProcessingEvent(user.getUsername(), previewId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY,
                triggerEvent
        );
        return previewId;
    }
}
