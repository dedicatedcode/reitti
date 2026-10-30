package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.notification.NotificationChannelConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationChannelJdbcService {

    private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationChannelJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<NotificationChannelConfig> rowMapper = (rs, _) -> {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new NotificationChannelConfig(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("channel_key"),
                rs.getBoolean("enabled"),
                readConfig(rs.getString("config")),
                createdAt != null ? createdAt.toInstant() : null,
                updatedAt != null ? updatedAt.toInstant() : null,
                rs.getLong("version"));
    };

    private Map<String, String> readConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(json, CONFIG_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    public List<NotificationChannelConfig> findAllForUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM notification_channels WHERE user_id = ?",
                rowMapper, userId);
    }

    public List<NotificationChannelConfig> findEnabledForUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM notification_channels WHERE user_id = ? AND enabled = TRUE",
                rowMapper, userId);
    }

    public Optional<NotificationChannelConfig> findByUserAndKey(Long userId, String channelKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM notification_channels WHERE user_id = ? AND channel_key = ?",
                    rowMapper, userId, channelKey));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public NotificationChannelConfig save(NotificationChannelConfig config) {
        Map<String, String> values = new LinkedHashMap<>();
        config.config().forEach((key, value) -> {
            if (value != null) {
                values.put(key, value);
            }
        });
        String json = objectMapper.writeValueAsString(values);
        Timestamp now = Timestamp.from(Instant.now());

        Optional<NotificationChannelConfig> existing = findByUserAndKey(config.userId(), config.channelKey());
        if (existing.isEmpty()) {
            jdbcTemplate.update("""
                            INSERT INTO notification_channels (user_id, channel_key, enabled, config, created_at, updated_at, version)
                            VALUES (?, ?, ?, ?::jsonb, ?, ?, 1)
                            """,
                    config.userId(), config.channelKey(), config.enabled(), json, now, now);
        } else {
            jdbcTemplate.update("""
                            UPDATE notification_channels
                            SET enabled = ?, config = ?::jsonb, updated_at = ?, version = version + 1
                            WHERE user_id = ? AND channel_key = ?
                            """,
                    config.enabled(), json, now, config.userId(), config.channelKey());
        }
        return findByUserAndKey(config.userId(), config.channelKey()).orElse(config);
    }

    public void delete(Long userId, String channelKey) {
        jdbcTemplate.update(
                "DELETE FROM notification_channels WHERE user_id = ? AND channel_key = ?",
                userId, channelKey);
    }
}
