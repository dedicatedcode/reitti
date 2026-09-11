package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.SuppressedVisitInfo;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
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
    private final RowMapper<SuppressedVisitInfo> infoRowMapper;

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
        this.infoRowMapper = (rs, _) -> new SuppressedVisitInfo(
                rs.getLong("id"),
                rs.getObject("place_id") != null ? rs.getLong("place_id") : null,
                rs.getString("place_name"),
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant());
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

    private static final String INFO_SELECT = """
            SELECT sv.id, sv.place_id, sp.name AS place_name, sv.latitude_centroid, sv.longitude_centroid, sv.start_time, sv.end_time
            FROM suppressed_visits sv
            LEFT JOIN significant_places sp ON sv.place_id = sp.id
            WHERE sv.user_id = ?
            """;

    public List<SuppressedVisitInfo> findAllInfosByUser(User user) {
        return jdbcTemplate.query(INFO_SELECT + " ORDER BY sv.start_time DESC", infoRowMapper, user.getId());
    }

    public Page<SuppressedVisitInfo> findInfosByUser(User user, PageRequest pageable) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suppressed_visits WHERE user_id = ?", Integer.class, user.getId());
        List<SuppressedVisitInfo> content = jdbcTemplate.query(
                INFO_SELECT + " ORDER BY sv.start_time DESC LIMIT ? OFFSET ?",
                infoRowMapper, user.getId(), pageable.getPageSize(), pageable.getOffset());
        return new Page<>(content, pageable, total != null ? total : 0);
    }

    public void delete(User user, Long id) {
        jdbcTemplate.update("DELETE FROM suppressed_visits WHERE user_id = ? AND id = ?", user.getId(), id);
    }
}
