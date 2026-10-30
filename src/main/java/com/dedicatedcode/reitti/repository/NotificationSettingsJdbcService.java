package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.notification.NotificationSettings;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Service
public class NotificationSettingsJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationSettingsJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<NotificationSettings> rowMapper = (rs, _) -> new NotificationSettings(
            rs.getLong("user_id"),
            rs.getBoolean("enabled"),
            rs.getBoolean("show_badge"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);

    public Optional<NotificationSettings> findByUserId(Long userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM notification_settings WHERE user_id = ?",
                    rowMapper, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public NotificationSettings getOrCreate(Long userId) {
        return findByUserId(userId).orElseGet(() -> save(NotificationSettings.defaultSettings(userId)));
    }

    public NotificationSettings save(NotificationSettings settings) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                        INSERT INTO notification_settings (user_id, enabled, show_badge, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                            SET enabled = EXCLUDED.enabled, show_badge = EXCLUDED.show_badge, updated_at = EXCLUDED.updated_at
                        """,
                settings.userId(), settings.enabled(), settings.showBadge(), now, now);
        return findByUserId(settings.userId()).orElse(settings);
    }

    public boolean isEnabled(Long userId) {
        return findByUserId(userId).map(NotificationSettings::enabled).orElse(true);
    }

    public boolean isBadgeVisible(Long userId) {
        return findByUserId(userId).map(NotificationSettings::showBadge).orElse(true);
    }
}
