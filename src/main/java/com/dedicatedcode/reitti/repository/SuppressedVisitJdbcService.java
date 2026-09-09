package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.security.User;
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
public class SuppressedVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SuppressedVisit> rowMapper;

    public SuppressedVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = (rs, _) -> new SuppressedVisit(
                rs.getLong("id"),
                rs.getObject("place_id") != null ? rs.getLong("place_id") : null,
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    public SuppressedVisit create(User user, SuppressedVisit suppressedVisit) {
        String sql = """
                INSERT INTO suppressed_visits (user_id, place_id, latitude_centroid, longitude_centroid, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """;
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                suppressedVisit.placeId(),
                suppressedVisit.latitudeCentroid(),
                suppressedVisit.longitudeCentroid(),
                Timestamp.from(suppressedVisit.startTime()),
                Timestamp.from(suppressedVisit.endTime()));
        return findById(user, id).orElseThrow();
    }

    public Optional<SuppressedVisit> findById(User user, Long id) {
        String sql = "SELECT id, place_id, latitude_centroid, longitude_centroid, start_time, end_time, created_at " +
                     "FROM suppressed_visits WHERE user_id = ? AND id = ?";
        List<SuppressedVisit> result = jdbcTemplate.query(sql, rowMapper, user.getId(), id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    public List<SuppressedVisit> findByUser(User user) {
        String sql = "SELECT id, place_id, latitude_centroid, longitude_centroid, start_time, end_time, created_at " +
                     "FROM suppressed_visits WHERE user_id = ? ORDER BY start_time DESC";
        return jdbcTemplate.query(sql, rowMapper, user.getId());
    }

    public List<SuppressedVisit> findByUserAndTimeOverlap(User user, Instant start, Instant end) {
        String sql = "SELECT id, place_id, latitude_centroid, longitude_centroid, start_time, end_time, created_at " +
                     "FROM suppressed_visits WHERE user_id = ? AND start_time < ? AND end_time > ?";
        return jdbcTemplate.query(sql, rowMapper, user.getId(), Timestamp.from(end), Timestamp.from(start));
    }

    public void delete(User user, Long id) {
        jdbcTemplate.update("DELETE FROM suppressed_visits WHERE user_id = ? AND id = ?", user.getId(), id);
    }
}
