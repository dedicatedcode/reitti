package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.Visit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public List<Visit> findAllByIds(List<Long> visitIds) {
        if (visitIds == null || visitIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", visitIds.stream().map(_ -> "?").toList());
        String sql = "SELECT v.* FROM preview_visits v WHERE v.id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, visitIds.toArray());
    }

    private List<Visit> findByUserAndStartTimeAndEndTime(User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT v.* " +
                "FROM preview_visits v " +
                "WHERE v.user_id = ? AND v.start_time = ? AND v.end_time = ? AND preview_id = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime), previewId);
    }

    public List<Visit> findByUserAndTimeAfterAndStartTimeBefore(User user, String previewId, Instant windowStart, Instant windowEnd) {
        String sql = "SELECT v.* " +
                "FROM preview_visits v " +
                "WHERE v.user_id = ? AND v.end_time >= ? AND v.start_time <= ? AND preview_id = ? " +
                "ORDER BY v.start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(windowStart), Timestamp.from(windowEnd), previewId);
    }

    public List<Visit> bulkInsert(User user, String previewId, List<Visit> visitsToInsert) {
        if (visitsToInsert.isEmpty()) {
            return new ArrayList<>();
        }

        List<Visit> createdVisits = new ArrayList<>();
        String sql = """
                INSERT INTO preview_visits (user_id, latitude, longitude, start_time, end_time, duration_seconds, processed, version, preview_id, preview_created_at)
                VALUES (?, ?, ?, ?, ?, ?, false, 1, ?, now());
                """;

        List<Object[]> batchArgs = visitsToInsert.stream()
                .map(visit -> new Object[]{
                        user.getId(),
                        visit.getLatitude(),
                        visit.getLongitude(),
                        Timestamp.from(visit.getStartTime()),
                        Timestamp.from(visit.getEndTime()),
                        visit.getDurationSeconds(),
                        previewId
                })
                .collect(Collectors.toList());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        for (int i = 0; i < updateCounts.length; i++) {
            int updateCount = updateCounts[i];
            if (updateCount > 0) {
                createdVisits.addAll(this.findByUserAndStartTimeAndEndTime(user, previewId, visitsToInsert.get(i).getStartTime(), visitsToInsert.get(i).getEndTime()));
            }
        }
        return createdVisits;
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
