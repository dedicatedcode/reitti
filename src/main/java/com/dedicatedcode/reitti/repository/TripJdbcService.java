package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.Trip;
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
public class TripJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TripJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Trip> TRIP_ROW_MAPPER = new RowMapper<Trip>() {
        @Override
        public Trip mapRow(ResultSet rs, int rowNum) throws SQLException {
            // Note: This is a simplified mapping - actual Trip entity would need
            // proper handling of start/end places and visits
            return new Trip(
                    rs.getLong("id"),
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time").toInstant(),
                    rs.getLong("duration_seconds"),
                    rs.getDouble("estimated_distance_meters"),
                    rs.getDouble("travelled_distance_meters"),
                    rs.getString("transport_mode_inferred"),
                    null, // endPlace - would need join
                    null, // endVisit - would need join
                    rs.getLong("version")
            );
        }
    };

    public List<Trip> findByUser(User user) {
        String sql = "SELECT t.*" +
                "FROM trips t " +
                "WHERE t.user_id = ?";
        return jdbcTemplate.query(sql, TRIP_ROW_MAPPER, user.getId());
    }

    public List<Trip> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT t.* " +
                "FROM trips t " +
                "WHERE t.user_id = ? AND t.start_time BETWEEN ? AND ? " +
                "ORDER BY t.start_time ASC";
        return jdbcTemplate.query(sql, TRIP_ROW_MAPPER, user.getId(),
                java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }

    public List<Trip> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT t.* " +
                "FROM trips t " +
                "WHERE t.user_id = ? " +
                "AND ((t.start_time <= ? AND t.end_time >= ?) OR " +
                "(t.start_time >= ? AND t.start_time <= ?) OR " +
                "(t.end_time >= ? AND t.end_time <= ?))";
        return jdbcTemplate.query(sql, TRIP_ROW_MAPPER, user.getId(),
                java.sql.Timestamp.from(endTime), java.sql.Timestamp.from(startTime),
                java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime),
                java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }

    public boolean existsByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(*) FROM trips WHERE user_id = ? AND start_time = ? AND end_time = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, user.getId(),
                java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
        return count != null && count > 0;
    }

    public List<Object[]> findTransportStatisticsByUser(User user) {
        String sql = "SELECT transport_mode_inferred, SUM(travelled_distance_meters), SUM(duration_seconds), COUNT(*) " +
                "FROM trips " +
                "WHERE user_id = ? " +
                "GROUP BY transport_mode_inferred " +
                "ORDER BY SUM(travelled_distance_meters) DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString(1),
                rs.getDouble(2),
                rs.getLong(3),
                rs.getLong(4)
        }, user.getId());
    }

    public List<Object[]> findTransportStatisticsByUserAndTimeRange(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT transport_mode_inferred, SUM(travelled_distance_meters), SUM(duration_seconds), COUNT(*) " +
                "FROM trips " +
                "WHERE user_id = ? AND start_time >= ? AND end_time <= ? " +
                "GROUP BY transport_mode_inferred " +
                "ORDER BY SUM(travelled_distance_meters) DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString(1),
                rs.getDouble(2),
                rs.getLong(3),
                rs.getLong(4)
        }, user.getId(), java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }

    public Trip create(User user, Trip trip) {
        String sql = "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                java.sql.Timestamp.from(trip.getStartTime()),
                java.sql.Timestamp.from(trip.getEndTime()),
                trip.getDurationSeconds(),
                trip.getTravelledDistanceMeters(),
                trip.getTransportModeInferred(),
                trip.getStartVisit() != null ? trip.getStartVisit().getId() : null,
                trip.getEndVisit() != null ? trip.getEndVisit().getId() : null
        );
        return trip.withId(id);
    }

    public Trip update(Trip trip) {
        String sql = "UPDATE trips SET start_time = ?, end_time = ?, duration_seconds = ?, travelled_distance_meters = ?, transport_mode_inferred = ?, start_place_id = ?, end_place_id = ?, start_visit_id = ?, end_visit_id = ?, version = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                java.sql.Timestamp.from(trip.getStartTime()),
                java.sql.Timestamp.from(trip.getEndTime()),
                trip.getDurationSeconds(),
                trip.getTravelledDistanceMeters(),
                trip.getTransportModeInferred(),
                trip.getStartVisit() != null ? trip.getStartVisit().getId() : null,
                trip.getEndVisit() != null ? trip.getEndVisit().getId() : null,
                trip.getId()
        );
        return trip;
    }

    public Optional<Trip> findById(Long id) {
        String sql = "SELECT t.* " +
                "FROM trips t " +
                "WHERE t.id = ?";
        List<Trip> results = jdbcTemplate.query(sql, TRIP_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM trips WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteAll() {
        String sql = "DELETE FROM trips";
        jdbcTemplate.update(sql);
    }
}
