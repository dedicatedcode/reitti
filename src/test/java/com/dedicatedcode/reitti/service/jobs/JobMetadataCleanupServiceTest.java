package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the #1212 orphan-trigger fix: the metadata cleanup must not delete the metadata of
 * jobs whose Quartz triggers are still pending. Deleting that metadata left live triggers
 * behind without any tracking row (8,683 orphaned promotion-job triggers in the reported
 * instance), which let qrtz_triggers grow unbounded.
 */
@IntegrationTest
class JobMetadataCleanupServiceTest {

    @Autowired
    private JobMetadataCleanupService cleanupService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void keepsMetadataOfPendingJobsAndDeletesTerminalOnes() {
        UUID pendingId = insertJob("AWAITING", Instant.now().minus(48, java.time.temporal.ChronoUnit.HOURS));
        UUID runningId = insertJob("RUNNING", Instant.now().minus(48, java.time.temporal.ChronoUnit.HOURS));
        UUID completedId = insertJob("COMPLETED", Instant.now().minus(48, java.time.temporal.ChronoUnit.HOURS));
        UUID failedId = insertJob("FAILED", Instant.now().minus(48, java.time.temporal.ChronoUnit.HOURS));
        UUID recentPendingId = insertJob("AWAITING", Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));

        cleanupService.cleanUpOldJobs();

        assertTrue(exists(pendingId), "pending trigger would become an orphan if its metadata was deleted");
        assertTrue(exists(runningId));
        assertTrue(exists(recentPendingId));
        assertFalse(exists(completedId));
        assertFalse(exists(failedId));
    }

    private UUID insertJob(String status, Instant enqueuedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO job_meta_data (id, user_id, task_id, type, friendly_name, status, enqueued_at, scheduled_at, parent_job_id, created_at, updated_at)
                VALUES (?, null, 'promotion-job', 'GPS_INGESTION', 'JobMetadataCleanupServiceTest', ?, ?, ?, null, NOW(), NOW())
                """, id, status, Timestamp.from(enqueuedAt), Timestamp.from(enqueuedAt));
        return id;
    }

    private boolean exists(UUID id) {
        List<String> result = jdbcTemplate.queryForList("SELECT status FROM job_meta_data WHERE id = ?", String.class, id);
        return !result.isEmpty();
    }
}
