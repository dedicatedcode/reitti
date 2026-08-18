package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TransportModeOverrideJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TransportModeOverrideJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    @CacheEvict(value = "transport-mode-overrides", allEntries = true)
    public void addTransportModeOverride(User user, TransportMode transportMode, Instant start, Instant end) {
        Instant middleTime = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2);
        
        // Delete any existing overrides for this user in the time range
        String deleteSql = """
            DELETE FROM transport_mode_overrides
            WHERE user_id = ?
            AND time BETWEEN ? AND ?
            """;
        jdbcTemplate.update(deleteSql, user.getId(), Timestamp.from(start), Timestamp.from(end));
        
        // Insert the new override
        String insertSql = """
            INSERT INTO transport_mode_overrides (user_id, time, transport_mode)
            VALUES (?, ?, ?)
            """;
        jdbcTemplate.update(insertSql, user.getId(), Timestamp.from(middleTime), transportMode.name());
    }

    @Cacheable(value = "transport-mode-overrides", key = "#user.id + '_' + #start.toEpochMilli() + '_' + #end.toEpochMilli()")
    public Optional<TransportMode> getTransportModeOverride(User user, Instant start, Instant end) {
        String sql = """
            SELECT transport_mode
            FROM transport_mode_overrides
            WHERE user_id = ?
            AND time BETWEEN ? AND ?
            LIMIT 1
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            return TransportMode.valueOf(rs.getString("transport_mode"));
        }, user.getId(), Timestamp.from(start), Timestamp.from(end))
        .stream()
        .findFirst();
    }

    @Transactional
    @CacheEvict(value = "transport-mode-overrides", allEntries = true)
    public void deleteAllTransportModeOverrides(User user) {
        String deleteSql = "DELETE FROM transport_mode_overrides WHERE user_id = ?";
        jdbcTemplate.update(deleteSql, user.getId());
    }

    /**
     * Returns all transport mode overrides in the given time range for the user.
     * Used during trip segmentation to apply per-segment overrides.
     */
    public List<TransportModeOverride> getTransportModeOverrides(User user, Instant start, Instant end) {
        String sql = """
            SELECT transport_mode, time
            FROM transport_mode_overrides
            WHERE user_id = ?
            AND time BETWEEN ? AND ?
            ORDER BY time ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TransportMode mode = TransportMode.valueOf(rs.getString("transport_mode"));
            Instant storedTime = rs.getTimestamp("time").toInstant();
            return new TransportModeOverride(mode, storedTime);
        }, user.getId(), Timestamp.from(start), Timestamp.from(end));
    }

    public record TransportModeOverride(TransportMode mode, Instant time) {
    }

}
