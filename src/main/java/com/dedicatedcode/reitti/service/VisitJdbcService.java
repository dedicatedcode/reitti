package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
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
public class VisitJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public VisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    private static final RowMapper<Visit> VISIT_ROW_MAPPER = new RowMapper<Visit>() {
        @Override
        public Visit mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("display_name"),
                rs.getLong("user_version")
            );
            
            return new Visit(
                rs.getLong("id"),
                user,
                rs.getDouble("longitude"),
                rs.getDouble("latitude"),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                rs.getLong("duration_seconds"),
                rs.getBoolean("processed")
            );
        }
    };
    
    public List<Visit> findByUserAndStartTime(User user, Instant startTime) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.user_id = ? AND v.start_time = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(), java.sql.Timestamp.from(startTime));
    }
    
    public List<Visit> findByUserAndEndTime(User user, Instant departureTime) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.user_id = ? AND v.end_time = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(), java.sql.Timestamp.from(departureTime));
    }
    
    public List<Visit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.user_id = ? AND v.start_time BETWEEN ? AND ? " +
                    "ORDER BY v.start_time ASC";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(), 
            java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }
    
    public List<Visit> findByUserAndStartTimeBeforeAndEndTimeAfter(User user, Instant startTimeBefore, Instant endTimeAfter) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.user_id = ? AND v.start_time < ? AND v.end_time > ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(), 
            java.sql.Timestamp.from(startTimeBefore), java.sql.Timestamp.from(endTimeAfter));
    }
    
    public List<Visit> findByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.user_id = ? AND v.start_time = ? AND v.end_time = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(), 
            java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }
    
    public Visit create(Visit visit) {
        String sql = "INSERT INTO visits (user_id, longitude, latitude, start_time, end_time, duration_seconds, processed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
            visit.getUser().getId(),
            visit.getLongitude(),
            visit.getLatitude(),
            java.sql.Timestamp.from(visit.getStartTime()),
            java.sql.Timestamp.from(visit.getEndTime()),
            visit.getDurationSeconds(),
            visit.isProcessed()
        );
        return new Visit(id, visit.getUser(), visit.getLongitude(), visit.getLatitude(),
            visit.getStartTime(), visit.getEndTime(), visit.getDurationSeconds(), visit.isProcessed());
    }
    
    public Visit update(Visit visit) {
        String sql = "UPDATE visits SET longitude = ?, latitude = ?, start_time = ?, end_time = ?, duration_seconds = ?, processed = ? WHERE id = ?";
        jdbcTemplate.update(sql,
            visit.getLongitude(),
            visit.getLatitude(),
            java.sql.Timestamp.from(visit.getStartTime()),
            java.sql.Timestamp.from(visit.getEndTime()),
            visit.getDurationSeconds(),
            visit.isProcessed(),
            visit.getId()
        );
        return visit;
    }
    
    public Optional<Visit> findById(Long id) {
        String sql = "SELECT v.id, v.longitude, v.latitude, v.start_time, v.end_time, v.duration_seconds, v.processed, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM visits v " +
                    "JOIN users u ON v.user_id = u.id " +
                    "WHERE v.id = ?";
        List<Visit> results = jdbcTemplate.query(sql, VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public void deleteById(Long id) {
        String sql = "DELETE FROM visits WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
