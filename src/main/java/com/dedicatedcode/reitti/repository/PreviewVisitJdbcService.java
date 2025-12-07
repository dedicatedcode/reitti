package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.Visit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PreviewVisitJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public PreviewVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Visit> VISIT_ROW_MAPPER = (rs, _) -> new Visit(
            rs.getLong("id"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds"),
            rs.getBoolean("processed"),
            rs.getLong("version")
    );

    public List<Visit> findByUserAndTimeAfterAndStartTimeBefore(User user, String previewId, Instant windowStart, Instant windowEnd) {
        String sql = "SELECT v.* " +
                "FROM preview_visits v " +
                "WHERE v.user_id = ? AND v.end_time >= ? AND v.start_time <= ? AND preview_id = ? " +
                "ORDER BY v.start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(windowStart), Timestamp.from(windowEnd), previewId);
    }

    public void delete(List<Visit> affectedVisits) throws OptimisticLockException {
        if (affectedVisits == null || affectedVisits.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", affectedVisits.stream().map(_ -> "?").toList());
        String sql = "DELETE FROM preview_visits WHERE id IN (" + placeholders + ")";
        
        Object[] ids = affectedVisits.stream().map(Visit::getId).toArray();
        jdbcTemplate.update(sql, ids);
    }
}
