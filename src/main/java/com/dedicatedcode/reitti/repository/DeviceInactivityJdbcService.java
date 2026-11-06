package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.notification.DeviceInactivitySetting;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DeviceInactivityJdbcService {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DeviceInactivitySetting> rowMapper = (rs, _) -> new DeviceInactivitySetting(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("device_id"),
            rs.getInt("hours"),
            rs.getTimestamp("last_notified_at") != null ? rs.getTimestamp("last_notified_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version"));

    public DeviceInactivityJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeviceInactivitySetting> findAll() {
        return jdbcTemplate.query("SELECT * FROM device_inactivity_notifications ORDER BY user_id, device_id", rowMapper);
    }

    public List<DeviceInactivitySetting> findAllForUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM device_inactivity_notifications WHERE user_id = ? ORDER BY device_id",
                rowMapper, userId);
    }

    public Optional<DeviceInactivitySetting> findByUserAndDevice(Long userId, Long deviceId) {
        List<DeviceInactivitySetting> results = jdbcTemplate.query(
                "SELECT * FROM device_inactivity_notifications WHERE user_id = ? AND device_id = ?",
                rowMapper, userId, deviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public DeviceInactivitySetting save(Long userId, Long deviceId, int hours) {
        jdbcTemplate.update("""
                        INSERT INTO device_inactivity_notifications (user_id, device_id, hours, created_at, updated_at)
                        VALUES (?, ?, ?, now(), now())
                        ON CONFLICT (user_id, device_id) DO UPDATE
                            SET hours = EXCLUDED.hours, updated_at = now(),
                                version = device_inactivity_notifications.version + 1
                        """,
                userId, deviceId, hours);
        return findByUserAndDevice(userId, deviceId).orElseThrow();
    }

    public void delete(Long userId, Long deviceId) {
        jdbcTemplate.update(
                "DELETE FROM device_inactivity_notifications WHERE user_id = ? AND device_id = ?",
                userId, deviceId);
    }

    public void markNotified(Long id, Instant notifiedAt) {
        jdbcTemplate.update(
                "UPDATE device_inactivity_notifications SET last_notified_at = ? WHERE id = ?",
                Timestamp.from(notifiedAt), id);
    }

    public void resetNotification(Long id) {
        jdbcTemplate.update(
                "UPDATE device_inactivity_notifications SET last_notified_at = NULL WHERE id = ?",
                id);
    }

    public void deleteAllForUser(Long userId) {
        jdbcTemplate.update("DELETE FROM device_inactivity_notifications WHERE user_id = ?", userId);
    }
}
