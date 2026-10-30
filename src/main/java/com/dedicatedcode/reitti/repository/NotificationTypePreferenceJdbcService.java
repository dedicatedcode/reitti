package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.notification.NotificationTypePreference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationTypePreferenceJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationTypePreferenceJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<NotificationTypePreference> rowMapper = (rs, _) -> new NotificationTypePreference(
            rs.getLong("user_id"),
            rs.getString("type_key"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);

    public List<NotificationTypePreference> findAllForUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM notification_type_preferences WHERE user_id = ?",
                rowMapper, userId);
    }

    public Map<String, Boolean> findPreferencesForUser(Long userId) {
        Map<String, Boolean> result = new HashMap<>();
        findAllForUser(userId).forEach(preference -> result.put(preference.typeKey(), preference.enabled()));
        return result;
    }

    /**
     * @param defaultEnabled the default of the notification type, used when the user has not
     *                       expressed a preference yet.
     */
    public boolean isEnabled(Long userId, String typeKey, boolean defaultEnabled) {
        Boolean enabled = jdbcTemplate.query(
                "SELECT enabled FROM notification_type_preferences WHERE user_id = ? AND type_key = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                userId, typeKey);
        return enabled == null ? defaultEnabled : enabled;
    }

    public void save(Long userId, String typeKey, boolean enabled) {
        jdbcTemplate.update("""
                        INSERT INTO notification_type_preferences (user_id, type_key, enabled, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (user_id, type_key) DO UPDATE
                            SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                        """,
                userId, typeKey, enabled, Timestamp.from(Instant.now()));
    }

    public void delete(Long userId, String typeKey) {
        jdbcTemplate.update(
                "DELETE FROM notification_type_preferences WHERE user_id = ? AND type_key = ?",
                userId, typeKey);
    }

    public void deleteAllForUser(Long userId) {
        jdbcTemplate.update("DELETE FROM notification_type_preferences WHERE user_id = ?", userId);
    }
}
