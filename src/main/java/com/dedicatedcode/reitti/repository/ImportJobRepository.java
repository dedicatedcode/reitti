package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImportJobRepository {
    private final JdbcTemplate jdbcTemplate;

    public ImportJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(UUID jobId, User user, JobType jobType, JobState jobState, String source) {
        this.jdbcTemplate.update("INSERT INTO import_jobs(id, user_id, type, status, file_name) VALUES(?, ?, ?, ?, ?)",
                                 jobId,
                                 user.getId(),
                                 jobType.name(),
                                 jobState.name(),
                                 source);
    }

    public void updateState(UUID jobId, JobState jobState) {
        this.jdbcTemplate.update("UPDATE import_jobs SET status = ? WHERE id = ?", jobState.name(), jobId);
    }
}
