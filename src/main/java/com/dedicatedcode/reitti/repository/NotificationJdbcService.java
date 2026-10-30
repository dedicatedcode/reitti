package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.notification.Notification;
import com.dedicatedcode.reitti.model.notification.NotificationMessage;
import com.dedicatedcode.reitti.model.notification.NotificationSeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationJdbcService {

    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<Notification> rowMapper = (rs, _) -> {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new Notification(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("type_key"),
                NotificationSeverity.valueOf(rs.getString("severity")),
                rs.getString("title"),
                rs.getString("message"),
                readParams(rs.getString("params")),
                rs.getString("action_url"),
                rs.getString("dedupe_key"),
                rs.getTimestamp("created_at").toInstant(),
                readAt != null ? readAt.toInstant() : null);
    };

    private Map<String, Object> readParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, PARAMS_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Inserts the notification. When an unread notification with the same user/type/dedupe key
     * already exists the insert is skipped and an empty Optional is returned.
     */
    public Optional<Long> insert(NotificationMessage message) {
        String params = objectMapper.writeValueAsString(message.params() == null ? Map.of() : message.params());
        List<Long> ids = jdbcTemplate.query("""
                        INSERT INTO notifications (user_id, type_key, severity, title, message, params, action_url, dedupe_key)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                        ON CONFLICT DO NOTHING
                        RETURNING id
                        """,
                (rs, _) -> rs.getLong("id"),
                message.userId(),
                message.typeKey(),
                message.severity().name(),
                message.title(),
                message.message(),
                params,
                message.actionUrl(),
                message.dedupeKey());
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }

    public boolean existsUnread(Long userId, String typeKey, String dedupeKey) {
        if (dedupeKey == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM notifications
                              WHERE user_id = ? AND type_key = ? AND dedupe_key = ? AND read_at IS NULL)
                """, Boolean.class, userId, typeKey, dedupeKey);
        return Boolean.TRUE.equals(exists);
    }

    public List<Notification> findForUser(Long userId, boolean unreadOnly, int limit, int offset) {
        String sql = unreadOnly
                ? "SELECT * FROM notifications WHERE user_id = ? AND read_at IS NULL ORDER BY created_at DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, userId, limit, offset);
    }

    /**
     * Returns the newest unread notification of one of the given types, or empty when none exists.
     * Used to find the next dialog/toast to present to the user.
     */
    public Optional<Notification> findLatestUnreadByTypeKeys(Long userId, Collection<String> typeKeys) {
        if (userId == null || typeKeys == null || typeKeys.isEmpty()) {
            return Optional.empty();
        }
        String placeholders = String.join(",", Collections.nCopies(typeKeys.size(), "?"));
        String sql = "SELECT * FROM notifications WHERE user_id = ? AND read_at IS NULL"
                + " AND type_key IN (" + placeholders + ")"
                + " ORDER BY created_at DESC, id DESC LIMIT 1";
        List<Object> args = new ArrayList<>(typeKeys.size() + 1);
        args.add(userId);
        args.addAll(typeKeys);
        List<Notification> results = jdbcTemplate.query(sql, rowMapper, args.toArray());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public int countUnread(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    public Optional<Notification> findForUser(Long userId, Long notificationId) {
        List<Notification> results = jdbcTemplate.query(
                "SELECT * FROM notifications WHERE user_id = ? AND id = ?",
                rowMapper, userId, notificationId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public boolean markRead(Long userId, Long notificationId) {
        return jdbcTemplate.update(
                "UPDATE notifications SET read_at = ? WHERE user_id = ? AND id = ? AND read_at IS NULL",
                Timestamp.from(Instant.now()), userId, notificationId) > 0;
    }

    public int markAllRead(Long userId) {
        return jdbcTemplate.update(
                "UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                Timestamp.from(Instant.now()), userId);
    }

    public boolean delete(Long userId, Long notificationId) {
        return jdbcTemplate.update(
                "DELETE FROM notifications WHERE user_id = ? AND id = ?",
                userId, notificationId) > 0;
    }

    public int clearAll(Long userId) {
        return jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM notifications WHERE created_at < ?",
                Timestamp.from(cutoff));
    }
}
