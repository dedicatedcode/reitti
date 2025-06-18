package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.RawLocationPoint;
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
public class RawLocationPointJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public RawLocationPointJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RawLocationPoint> RAW_LOCATION_POINT_ROW_MAPPER = new RowMapper<RawLocationPoint>() {
        @Override
        public RawLocationPoint mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RawLocationPoint(
                    rs.getLong("id"),
                    rs.getTimestamp("timestamp").toInstant(),
                    null,
                    rs.getDouble("accuracy_meters"),
                    rs.getString("activity_provided"),
                    rs.getBoolean("processed"),
                    rs.getLong("version")
            );
        }
    };

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.timestamp, rlp.accuracy_meters, rlp.activity_provided, rlp.processed, " +
                "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                "FROM raw_location_points rlp " +
                "JOIN users u ON rlp.user_id = u.id " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp ASC";
        return jdbcTemplate.query(sql, RAW_LOCATION_POINT_ROW_MAPPER,
                user.getId(), java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }

    public Optional<RawLocationPoint> findByUserAndTimestamp(User user, Instant timestamp) {
        String sql = "SELECT rlp.id, rlp.timestamp, rlp.accuracy_meters, rlp.activity_provided, rlp.processed, " +
                "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                "FROM raw_location_points rlp " +
                "JOIN users u ON rlp.user_id = u.id " +
                "WHERE rlp.user_id = ? AND rlp.timestamp = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, RAW_LOCATION_POINT_ROW_MAPPER,
                user.getId(), java.sql.Timestamp.from(timestamp));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestamp(User user) {
        String sql = "SELECT rlp.id, rlp.timestamp, rlp.accuracy_meters, rlp.activity_provided, rlp.processed, " +
                "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                "FROM raw_location_points rlp " +
                "JOIN users u ON rlp.user_id = u.id " +
                "WHERE rlp.user_id = ? AND rlp.processed = false " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, RAW_LOCATION_POINT_ROW_MAPPER, user.getId());
    }

    public List<Integer> findDistinctYearsByUser(User user) {
        String sql = "SELECT DISTINCT EXTRACT(YEAR FROM timestamp)::integer " +
                "FROM raw_location_points " +
                "WHERE user_id = ? " +
                "ORDER BY EXTRACT(YEAR FROM timestamp) DESC";
        return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
    }

    public RawLocationPoint create(User user, RawLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, activity_provided, geom, processed) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                java.sql.Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getActivityProvided(),
                rawLocationPoint.getGeom(), // Would need PostGIS handling
                rawLocationPoint.isProcessed()
        );
        return rawLocationPoint.withId(id);
    }

    public RawLocationPoint update(RawLocationPoint rawLocationPoint) {
        String sql = "UPDATE raw_location_points SET timestamp = ?, accuracy_meters = ?, activity_provided = ?, geom = ?, processed = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                java.sql.Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getActivityProvided(),
                rawLocationPoint.getGeom(), // Would need PostGIS handling
                rawLocationPoint.isProcessed(),
                rawLocationPoint.getId()
        );
        return rawLocationPoint;
    }

    public Optional<RawLocationPoint> findById(Long id) {
        String sql = "SELECT rlp.id, rlp.timestamp, rlp.accuracy_meters, rlp.activity_provided, rlp.processed, " +
                "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                "FROM raw_location_points rlp " +
                "JOIN users u ON rlp.user_id = u.id " +
                "WHERE rlp.id = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, RAW_LOCATION_POINT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM raw_location_points WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            User user, Instant startTime, Instant endTime, int minimumPoints, double distanceInMeters) {
        String sql = "SELECT rlp.*, " +
                "ST_ClusterDBSCAN(rlp.geom, ?, ?) over () AS cluster_id " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

                    RawLocationPoint point = new RawLocationPoint(
                            rs.getLong("id"),
                            rs.getTimestamp("timestamp").toInstant(),
                            null,
                            rs.getDouble("accuracy_meters"),
                            rs.getString("activity_provided"),
                            rs.getBoolean("processed"),
                            rs.getLong("version")
                    );

                    Integer clusterId = rs.getObject("cluster_id", Integer.class);

                    return new ClusteredPoint(point, clusterId);
                }, distanceInMeters, minimumPoints, user.getId(),
                java.sql.Timestamp.from(startTime), java.sql.Timestamp.from(endTime));
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM raw_location_points";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    public void deleteAll() {
        String sql = "DELETE FROM raw_location_points";
        jdbcTemplate.update(sql);
    }

    public static class ClusteredPoint {
        private final RawLocationPoint point;
        private final Integer clusterId;

        public ClusteredPoint(RawLocationPoint point, Integer clusterId) {
            this.point = point;
            this.clusterId = clusterId;
        }

        public RawLocationPoint getPoint() {
            return point;
        }

        public Integer getClusterId() {
            return clusterId;
        }
    }
}
