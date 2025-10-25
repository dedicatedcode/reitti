package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransportModeOverrideJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TransportModeOverrideJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void addTransportModeOverride(User user, TransportMode transportMode, Instant start, Instant end) {
        // Calculate the middle time between start and end
        Instant middleTime = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2);
        
        // Delete any existing overrides for this user in the time range
        String deleteSql = """
            DELETE FROM transport_mode_overrides 
            WHERE user_id = ? 
            AND time BETWEEN ? AND ?
            """;
        jdbcTemplate.update(deleteSql, user.getId(), start, end);
        
        // Insert the new override
        String insertSql = """
            INSERT INTO transport_mode_overrides (user_id, time, transport_mode) 
            VALUES (?, ?, ?)
            """;
        jdbcTemplate.update(insertSql, user.getId(), middleTime, transportMode.name());
    }
}
