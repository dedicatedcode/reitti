package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
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
public class VisitJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public VisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Visit> VISIT_ROW_MAPPER = (rs, rowNum) -> new Visit(
            rs.getLong("id"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds"),
            rs.getBoolean("processed"),
            rs.getLong("version")
    );

    public List<Visit> findByUser(User user) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? ORDER BY start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId());
    }

    public List<Visit> findByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? AND v.start_time = ? AND v.end_time = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public Visit create(User user, Visit visit) {
        String sql = "INSERT INTO visits (user_id, longitude, latitude, start_time, end_time, duration_seconds, processed, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?,?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                visit.getLongitude(),
                visit.getLatitude(),
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.isProcessed(),
                visit.getVersion()
        );
        return visit.withId(id);
    }

    public Visit update(Visit visit) {
        String sql = "UPDATE visits SET longitude = ?, latitude = ?, start_time = ?, end_time = ?, duration_seconds = ?, processed = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                visit.getLongitude(),
                visit.getLatitude(),
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.isProcessed(),
                visit.getId()
        );
        return visit;
    }

    public Optional<Visit> findById(Long id) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.id = ?";
        List<Visit> results = jdbcTemplate.query(sql, VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Visit> findAllByIds(List<Long> visitIds) {
        if (visitIds == null || visitIds.isEmpty()) {
            return List.of();
        }
        
        String placeholders = String.join(",", visitIds.stream().map(id -> "?").toList());
        String sql = "SELECT v.* FROM visits v WHERE v.id IN (" + placeholders + ")";
        
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, visitIds.toArray());
    }

    public void deleteAll() {
        String sql = "DELETE FROM visits";
        jdbcTemplate.update(sql);
    }

    public List<Visit> findByUserAndTimeAfterAndStartTimeBefore(User user, Instant windowStart, Instant windowEnd) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? AND v.end_time > ? AND v.start_time < ? " +
                "ORDER BY v.start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(windowStart), Timestamp.from(windowEnd));
    }

    public void delete(List<Visit> affectedVisits) {
        if (affectedVisits == null || affectedVisits.isEmpty()) {
            return;
        }
        
        String placeholders = String.join(",", affectedVisits.stream().map(visit -> "?").toList());
        String sql = "DELETE FROM visits WHERE id IN (" + placeholders + ")";
        
        Object[] ids = affectedVisits.stream().map(Visit::getId).toArray();
        jdbcTemplate.update(sql, ids);
    }
}
