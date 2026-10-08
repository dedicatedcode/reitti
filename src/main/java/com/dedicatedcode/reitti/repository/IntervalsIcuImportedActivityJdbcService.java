package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class IntervalsIcuImportedActivityJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public IntervalsIcuImportedActivityJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> findImportedIds(User user, Collection<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Set.of();
        }
        List<String> ids = List.copyOf(activityIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT activity_id FROM intervals_icu_imported_activities WHERE user_id = ? AND activity_id IN (" + placeholders + ")";

        Object[] args = new Object[ids.size() + 1];
        args[0] = user.getId();
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }

        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, args));
    }

    public void markImported(User user, String activityId, LocalDateTime startDateLocal, long pointsReceived) {
        String sql = """
                INSERT INTO intervals_icu_imported_activities (user_id, activity_id, start_date_local, points_received, imported_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_id, activity_id) DO NOTHING
                """;
        jdbcTemplate.update(sql,
                user.getId(),
                activityId,
                startDateLocal != null ? Timestamp.valueOf(startDateLocal) : null,
                pointsReceived);
    }
}
