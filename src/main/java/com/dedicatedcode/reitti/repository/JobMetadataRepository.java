package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobMetadataRepository {
    private final JdbcTemplate jdbcTemplate;

    public JobMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID jobId, User user, JobType jobType, String friendlyName, JobState initialState, Instant enqueuedAt, Instant scheduledAt) {
        jdbcTemplate.update(
            "INSERT INTO import_jobs (id, user_id, type, friendly_name, status, enqueued_at, scheduled_at, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            jobId,
            user.getId(),
            jobType.name(),
            friendlyName,
            initialState.name(),
            toTimestamp(enqueuedAt),
            toTimestamp(scheduledAt)
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public void updateProgress(UUID jobId, long current, long max, String message) {
        this.jdbcTemplate.update("UPDATE import_jobs SET current_progress = ?, max_progress = ?, progress_message = ? WHERE id = ?", current, max, message, jobId);
    }

    public void updateState(UUID jobId, JobState newState, Instant stateTimestamp) {
        String column = switch (newState) {
            case RUNNING -> "processing_at";
            case FAILED, COMPLETED -> "finished_at";
            default -> null;
        };

        if (column != null) {
            jdbcTemplate.update(
                "UPDATE import_jobs SET status = ?, " + column + " = ?, updated_at = NOW() WHERE id = ?",
                newState.name(),
                toTimestamp(stateTimestamp),
                jobId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE import_jobs SET status = ?, updated_at = NOW() WHERE id = ?",
                newState.name(),
                jobId
            );
        }
    }

    public Optional<JobState> getState(UUID jobId) {
        String state = jdbcTemplate.queryForObject(
            "SELECT status FROM import_jobs WHERE id = ?",
            String.class,
            jobId
        );
        if (state != null) {
            return Optional.of(JobState.valueOf(state));
        } else {
            return Optional.empty();
        }
    }

    public List<JobMetadata> findByStates(List<JobState> states) {
        if (states.isEmpty()) {
            return List.of();
        }
        String inClause = String.join(",", Collections.nCopies(states.size(), "?"));
        String sql = "SELECT id, user_id, type, friendly_name, status, enqueued_at, scheduled_at, processing_at, finished_at " +
                     "FROM import_jobs WHERE status IN (" + inClause + ") ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            JobMetadata metadata = new JobMetadata();
            metadata.setId(UUID.fromString(rs.getString("id")));
            metadata.setUserId(rs.getLong("user_id"));
            metadata.setJobType(rs.getString("type"));
            metadata.setFriendlyName(rs.getString("friendly_name"));
            metadata.setState(JobState.valueOf(rs.getString("status")));
            metadata.setEnqueuedAt(toInstant(rs.getTimestamp("enqueued_at")));
            metadata.setScheduledAt(toInstant(rs.getTimestamp("scheduled_at")));
            metadata.setProcessingAt(toInstant(rs.getTimestamp("processing_at")));
            metadata.setFinishedAt(toInstant(rs.getTimestamp("finished_at")));
            return metadata;
        }, states.stream().map(Enum::name).toArray());
    }

    public static class JobMetadata {
        private UUID id;
        private Long userId;
        private String jobType;
        private String friendlyName;
        private JobState state;
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

        public JobState getState() {
            return state;
        }

        public void setState(JobState state) {
            this.state = state;
        }
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
