package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
    private final RowMapper<JobMetadata> jobMetadataRowMapper = (rs, ignored) -> {
        JobMetadata metadata = new JobMetadata();
        metadata.setId(UUID.fromString(rs.getString("id")));
        metadata.setUserId(rs.getLong("user_id"));
        metadata.setJobType(JobType.valueOf(rs.getString("type")));
        metadata.setFriendlyName(rs.getString("friendly_name"));
        metadata.setState(JobState.valueOf(rs.getString("status")));
        metadata.setEnqueuedAt(toInstant(rs.getTimestamp("enqueued_at")));
        metadata.setScheduledAt(toInstant(rs.getTimestamp("scheduled_at")));
        metadata.setProcessingAt(toInstant(rs.getTimestamp("processing_at")));
        metadata.setFinishedAt(toInstant(rs.getTimestamp("finished_at")));
        metadata.setProgressMessage(rs.getString("progress_message"));
        metadata.setCurrentProgress((Long) rs.getObject("current_progress"));
        metadata.setMaxProgress((Long) rs.getObject("max_progress"));
        String parentJobIdStr = rs.getString("parent_job_id");
        if (parentJobIdStr != null) {
            metadata.setParentJobId(UUID.fromString(parentJobIdStr));
        }

        return metadata;
    };

    public JobMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID jobId, User user, JobType jobType, String friendlyName, JobState initialState, Instant enqueuedAt, Instant scheduledAt, UUID parentId) {
        jdbcTemplate.update(
            "INSERT INTO import_jobs (id, user_id, type, friendly_name, status, enqueued_at, scheduled_at, parent_job_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            jobId,
            user.getId(),
            jobType.name(),
            friendlyName,
            initialState.name(),
            toTimestamp(enqueuedAt),
            toTimestamp(scheduledAt),
            parentId);
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
        String sql = "SELECT id, user_id, type, friendly_name, status, enqueued_at, scheduled_at, processing_at, finished_at, parent_job_id, current_progress, max_progress, progress_message " +
                "FROM import_jobs WHERE status IN (" + inClause + ") ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, jobMetadataRowMapper, states.stream().map(Enum::name).toArray());
    }

    public List<JobMetadata> findByParentJobId(UUID parentId) {
        String sql = "SELECT id, user_id, type, friendly_name, status, enqueued_at, scheduled_at, processing_at, finished_at, parent_job_id, current_progress, max_progress, progress_message " +
                "FROM import_jobs WHERE parent_job_id = ?";
        return jdbcTemplate.query(sql, jobMetadataRowMapper, parentId);
    }

    public Optional<JobMetadata> findById(UUID jobId) {
        return Optional.ofNullable(this.jdbcTemplate.queryForObject("SELECT * FROM import_jobs WHERE id = ?", jobMetadataRowMapper, jobId));
    }

    public void updateParentJobState(UUID parentJobId, JobState newState) {
        Optional<JobMetadata> parent = findById(parentJobId);
        if (parent.isEmpty()) return;

        JobState currentState = parent.get().getState();

        if (newState == JobState.RUNNING) {
            // Only update if currently awaiting
            if (currentState == JobState.AWAITING) {
                updateState(parentJobId, JobState.RUNNING, Instant.now());
            }
        } else if (newState == JobState.COMPLETED || newState == JobState.FAILED) {
            // Check all children before completing
            List<JobMetadata> childJobs = findByParentJobId(parentJobId);

            boolean allComplete = childJobs.stream()
                    .allMatch(j -> j.getState() == JobState.COMPLETED || j.getState() == JobState.FAILED);

            if (allComplete) {
                boolean anyFailed = childJobs.stream()
                        .anyMatch(j -> j.getState() == JobState.FAILED);

                JobState finalState = anyFailed ? JobState.FAILED : JobState.COMPLETED;
                updateState(parentJobId, finalState, Instant.now());
            }
        }
    }

    public void delete(UUID jobId) {
        this.jdbcTemplate.update("DELETE FROM import_jobs WHERE id = ?", jobId);
    }

    public static class JobMetadata {
        private UUID id;
        private UUID parentJobId;

        private Long userId;
        private JobType jobType;
        private String friendlyName;
        private JobState state;
        private Instant enqueuedAt;
        private Instant scheduledAt;
        private Instant processingAt;
        private Instant finishedAt;
        private String progressMessage;
        private Long currentProgress;
        private Long maxProgress;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public UUID getParentJobId() { return parentJobId; }
        public void setParentJobId(UUID parentJobId) { this.parentJobId = parentJobId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public JobType getJobType() { return jobType; }
        public void setJobType(JobType jobType) { this.jobType = jobType; }
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
        public String getProgressMessage() { return progressMessage; }
        public void setProgressMessage(String progressMessage) { this.progressMessage = progressMessage; }
        public Long getCurrentProgress() { return currentProgress; }
        public void setCurrentProgress(Long currentProgress) { this.currentProgress = currentProgress; }
        public Long getMaxProgress() { return maxProgress; }
        public void setMaxProgress(Long maxProgress) { this.maxProgress = maxProgress; }
    }
}
