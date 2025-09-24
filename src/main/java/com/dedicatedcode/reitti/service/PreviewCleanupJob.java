package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class PreviewCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PreviewCleanupJob.class);
    private final JdbcTemplate jdbcTemplate;

    public PreviewCleanupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${reitti.data-management.preview-cleanup.cron}")
    public void cleanUp() {
        log.debug("Cleaning up preview data");
        long start = System.currentTimeMillis();
        Timestamp lastDay = Timestamp.valueOf(LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay());
        int params = this.jdbcTemplate.update("DELETE FROM preview_visit_detection_parameters WHERE preview_created_at < ?", lastDay);
        int trips = this.jdbcTemplate.update("DELETE FROM preview_trips WHERE preview_created_at < ?", lastDay);
        int processedVisits = this.jdbcTemplate.update("DELETE FROM preview_processed_visits WHERE preview_created_at < ?", lastDay);
        int visits = this.jdbcTemplate.update("DELETE FROM preview_visits WHERE preview_created_at < ?", lastDay);
        int points = this.jdbcTemplate.update("DELETE FROM preview_raw_location_points WHERE preview_created_at < ?", lastDay);
        log.debug("Preview data cleanup finished in [{}] ms with [{}] records", System.currentTimeMillis() - start, params + trips + processedVisits + visits + points);
    }
}
