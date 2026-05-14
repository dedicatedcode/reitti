package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class TestJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public TestJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findPointsForVisit(User user, ProcessedVisit visit) {
        return this.jdbcTemplate.queryForList("SELECT raw_location_points.source_point_id FROM raw_location_points WHERE user_id = ? AND timestamp >= ? AND timestamp < ? AND source_point_id IS NOT NULL;", Long.class,
                                       user.getId(),
                                       Timestamp.from(visit.getStartTime()),
                                       Timestamp.from(visit.getEndTime()));
    }
}
