package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.processing.RecalculationState;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class PreviewCleanupJobTest {

    @Autowired
    private PreviewCleanupJob previewCleanupJob;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VisitDetectionPreviewService visitDetectionPreviewService;

    @Autowired
    private TestingService testingService;
    
    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void shouldRunCleanUpJob() {
        Instant now = Instant.now();
        DetectionParameter config = new DetectionParameter(null,
                                                           new DetectionParameter.VisitDetection(100, 100),
                                                           new DetectionParameter.VisitMerging(1, 100, 100),
                                                           new DetectionParameter.LocationDensity(50.0, 100),
                                                           now,
                                                           RecalculationState.NEEDED);
        String previewId = this.visitDetectionPreviewService.startPreview(user, config, now);

        this.jdbcTemplate.update("UPDATE preview_significant_places SET preview_created_at = ? WHERE preview_id = ?", Timestamp.from(now.minusSeconds(259200)), previewId);
        this.jdbcTemplate.update("UPDATE preview_raw_location_points SET preview_created_at = ? WHERE preview_id = ?", Timestamp.from(now.minusSeconds(259200)), previewId);
        this.jdbcTemplate.update("UPDATE preview_processed_visits SET preview_created_at = ? WHERE preview_id = ?", Timestamp.from(now.minusSeconds(259200)), previewId);
        this.jdbcTemplate.update("UPDATE preview_trips SET preview_created_at = ? WHERE preview_id = ?", Timestamp.from(now.minusSeconds(259200)), previewId);
        this.jdbcTemplate.update("UPDATE preview_visit_detection_parameters SET preview_created_at = ? WHERE preview_id = ?", Timestamp.from(now.minusSeconds(259200)), previewId);

        this.previewCleanupJob.cleanUp();

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_processed_visits WHERE preview_id = ?", Integer.class, previewId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_raw_location_points WHERE preview_id = ?", Integer.class, previewId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_significant_places WHERE preview_id = ?", Integer.class, previewId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_trips WHERE preview_id = ?", Integer.class, previewId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_visit_detection_parameters WHERE preview_id = ?", Integer.class, previewId));
    }
}