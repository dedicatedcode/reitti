package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;

@Repository
public class JobMetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public JobMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID jobId, Long userId, JobType jobType, String friendlyName, String initialState, Instant enqueuedAt, Instant scheduledAt) {
        jdbcTemplate.update(
            "INSERT INTO import_jobs (id, user_id, job_type, friendly_name, state, enqueued_at, scheduled_at, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            jobId,
            userId,
            jobType.name(),
            friendlyName,
            initialState,
            enqueuedAt,
            scheduledAt
        );
    }

    public void updateState(UUID jobId, String newState, Instant stateTimestamp) {
        String column;
        switch (newState) {
            case "PROCESSING":
                column = "processing_at";
                break;
            case "SUCCEEDED":
            case "FAILED":
            case "COMPLETED":
                column = "finished_at";
                break;
            default:
                column = null;
        }

        if (column != null) {
            jdbcTemplate.update(
                "UPDATE import_jobs SET state = ?, " + column + " = ?, updated_at = NOW() WHERE id = ?",
                newState,
                stateTimestamp,
                jobId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE import_jobs SET state = ?, updated_at = NOW() WHERE id = ?",
                newState,
                jobId
            );
        }
    }

    public Optional<String> getState(UUID jobId) {
        try {
            String state = jdbcTemplate.queryForObject(
                "SELECT state FROM import_jobs WHERE id = ?",
                String.class,
                jobId
            );
            return Optional.of(state);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<JobMetadata> findByStates(List<String> states) {
        if (states.isEmpty()) {
            return List.of();
        }
        String inClause = String.join(",", Collections.nCopies(states.size(), "?"));
        String sql = "SELECT id, user_id, job_type, friendly_name, state, enqueued_at, scheduled_at, processing_at, finished_at " +
                     "FROM import_jobs WHERE state IN (" + inClause + ") ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            JobMetadata metadata = new JobMetadata();
            metadata.setId(UUID.fromString(rs.getString("id")));
            metadata.setUserId(rs.getLong("user_id"));
            metadata.setJobType(rs.getString("job_type"));
            metadata.setFriendlyName(rs.getString("friendly_name"));
            metadata.setState(rs.getString("state"));
            metadata.setEnqueuedAt(rs.getTimestamp("enqueued_at") != null ? rs.getTimestamp("enqueued_at").toInstant() : null);
            metadata.setScheduledAt(rs.getTimestamp("scheduled_at") != null ? rs.getTimestamp("scheduled_at").toInstant() : null);
            metadata.setProcessingAt(rs.getTimestamp("processing_at") != null ? rs.getTimestamp("processing_at").toInstant() : null);
            metadata.setFinishedAt(rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null);
            return metadata;
        }, states.toArray());
    }

    public static class JobMetadata {
        private UUID id;
        private Long userId;
        private String jobType;
        private String friendlyName;
        private String state;
        private Instant enqueuedAt;
        private Instant scheduledAt;
        private Instant processingAt;
        private Instant finishedAt;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getJobType() { return jobType; }
        public void setJobType(String jobType) { this.jobType = jobType; }
        public String getFriendlyName() { return friendlyName; }
        public void setFriendlyName(String friendlyName) { this.friendlyName = friendlyName; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public Instant getEnqueuedAt() { return enqueuedAt; }
        public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }
        public Instant getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
        public Instant getProcessingAt() { return processingAt; }
        public void setProcessingAt(Instant processingAt) { this.processingAt = processingAt; }
        public Instant getFinishedAt() { return finishedAt; }
        public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    }
}
