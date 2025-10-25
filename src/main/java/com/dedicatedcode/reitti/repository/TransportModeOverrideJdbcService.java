package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TransportModeOverrideJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TransportModeOverrideJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    @CacheEvict(value = "transport-mode-overrides", key = "#user.id")
    public void addTransportModeOverride(User user, TransportMode transportMode, Instant start, Instant end) {
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

    @Cacheable(value = "transport-mode-overrides", key = "#user.id")
    public List<TransportModeOverride> getTransportModeOverrides(User user) {
        String sql = """
            SELECT time, transport_mode 
            FROM transport_mode_overrides 
            WHERE user_id = ? 
            ORDER BY time
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant time = rs.getTimestamp("time").toInstant();
            TransportMode mode = TransportMode.valueOf(rs.getString("transport_mode"));
            return new TransportModeOverride(time, mode);
        }, user.getId());
    }

    @Transactional
    @CacheEvict(value = "transport-mode-overrides", key = "#user.id")
    public void deleteAllTransportModeOverrides(User user) {
        String deleteSql = "DELETE FROM transport_mode_overrides WHERE user_id = ?";
        jdbcTemplate.update(deleteSql, user.getId());
    }

    public static class TransportModeOverride {
        private final Instant time;
        private final TransportMode transportMode;

        public TransportModeOverride(Instant time, TransportMode transportMode) {
            this.time = time;
            this.transportMode = transportMode;
        }

        public Instant getTime() {
            return time;
        }

        public TransportMode getTransportMode() {
            return transportMode;
        }
    }
}
