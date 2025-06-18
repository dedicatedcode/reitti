package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProcessedVisitJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ProcessedVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    private static final RowMapper<ProcessedVisit> PROCESSED_VISIT_ROW_MAPPER = new RowMapper<ProcessedVisit>() {
        @Override
        public ProcessedVisit mapRow(ResultSet rs, int rowNum) throws SQLException {
            SignificantPlace  place = null;
            // Note: This is simplified - actual ProcessedVisit would need proper SignificantPlace handling
            return new ProcessedVisit(
                rs.getLong("id"),
                place,
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                rs.getString("original_visit_ids"),
                rs.getLong("duration_seconds"),
                rs.getInt("merge_cound"),
                rs.getLong("version")
            );
        }
    };
    
    public List<ProcessedVisit> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ?";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
            java.sql.Timestamp.from(endTime), java.sql.Timestamp.from(startTime));
    }
    
    public List<ProcessedVisit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.user_id = ? AND pv.start_time BETWEEN ? AND ? " +
                    "ORDER BY pv.start_time ASC";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
            java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }
    
    public List<ProcessedVisit> findByUserAndEndTimeBetweenOrderByStartTimeAsc(User user, Instant endTimeAfter, Instant endTimeBefore) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.user_id = ? AND pv.end_time BETWEEN ? AND ? " +
                    "ORDER BY pv.start_time ASC";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
            java.sql.Timestamp.from(endTimeAfter), java.sql.Timestamp.from(endTimeBefore));
    }
    
    public Optional<ProcessedVisit> findByUserAndId(User user, long id) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.user_id = ? AND pv.id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<ProcessedVisit> findByUserAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(User user, Instant startTimeIsGreaterThan, Instant endTimeIsLessThan) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.user_id = ? AND pv.start_time >= ? AND pv.end_time <= ?";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
            java.sql.Timestamp.from(startTimeIsGreaterThan), java.sql.Timestamp.from(endTimeIsLessThan));
    }
    
    public List<Object[]> findTopPlacesByStayTimeWithLimit(User user, long limit) {
        String sql = "SELECT sp.name, SUM(pv.duration_seconds), COUNT(pv), sp.latitude_centroid, sp.longitude_centroid " +
                    "FROM processed_visits pv " +
                    "JOIN significant_places sp ON pv.place_id = sp.id " +
                    "WHERE pv.user_id = ? " +
                    "GROUP BY sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid " +
                    "ORDER BY SUM(pv.duration_seconds) DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString(1),
            rs.getLong(2),
            rs.getLong(3),
            rs.getDouble(4),
            rs.getDouble(5)
        }, user.getId(), limit);
    }
    
    public List<Object[]> findTopPlacesByStayTimeWithLimit(User user, Instant startTime, Instant endTime, long limit) {
        String sql = "SELECT sp.name, SUM(pv.duration_seconds), COUNT(pv), sp.latitude_centroid, sp.longitude_centroid " +
                    "FROM processed_visits pv " +
                    "JOIN significant_places sp ON pv.place_id = sp.id " +
                    "WHERE pv.user_id = ? AND pv.start_time >= ? AND pv.end_time <= ? " +
                    "GROUP BY sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid " +
                    "ORDER BY SUM(pv.duration_seconds) DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString(1),
            rs.getLong(2),
            rs.getLong(3),
            rs.getDouble(4),
            rs.getDouble(5)
        }, user.getId(), java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime), limit);
    }
    
    public ProcessedVisit create(User user, ProcessedVisit visit) {
        String sql = "INSERT INTO processed_visits (user_id, start_time, end_time, duration_seconds, place_id, version) " +
                    "VALUES (?, ?, ?, ?, ?, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
            user.getId(),
            java.sql.Timestamp.from(visit.getStartTime()),
            java.sql.Timestamp.from(visit.getEndTime()),
            visit.getDurationSeconds(),
            visit.getPlace() != null ? visit.getPlace().getId() : null
        );
        return visit.withId(id).withVersion(1);
    }
    
    public ProcessedVisit update(ProcessedVisit visit) {
        String sql = "UPDATE processed_visits SET start_time = ?, end_time = ?, duration_seconds = ?, place_id = ? WHERE id = ?";
        jdbcTemplate.update(sql,
            java.sql.Timestamp.from(visit.getStartTime()),
            java.sql.Timestamp.from(visit.getEndTime()),
            visit.getDurationSeconds(),
            visit.getPlace().getId(),
            visit.getId()
        );
        return visit;
    }
    
    public Optional<ProcessedVisit> findById(Long id) {
        String sql = "SELECT pv.* " +
                    "FROM processed_visits pv " +
                    "WHERE pv.id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public void deleteById(Long id) {
        String sql = "DELETE FROM processed_visits WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteAll(List<ProcessedVisit> processedVisits) {
        if (processedVisits == null || processedVisits.isEmpty()) {
            return;
        }
        
        List<Long> ids = processedVisits.stream()
                .map(ProcessedVisit::getId)
                .toList();
        
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM processed_visits WHERE id IN (" + placeholders + ")";
        
        jdbcTemplate.update(sql, ids.toArray());
    }
}
