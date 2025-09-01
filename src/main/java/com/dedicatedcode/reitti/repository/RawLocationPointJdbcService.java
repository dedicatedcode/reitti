package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class RawLocationPointJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RawLocationPoint> rawLocationPointRowMapper;
    private final PointReaderWriter pointReaderWriter;
    private final GeometryFactory geometryFactory;

    public RawLocationPointJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter, GeometryFactory geometryFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.rawLocationPointRowMapper = (rs, rowNum) -> new RawLocationPoint(
                rs.getLong("id"),
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getString("activity_provided"),
                rs.getBoolean("processed"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.geometryFactory = geometryFactory;
    }


    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.activity_provided, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp ASC";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndDateRange(User user, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.activity_provided, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp ASC";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.valueOf(startTime), Timestamp.valueOf(endTime));
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestamp(User user) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.activity_provided, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId());
    }

    public List<Integer> findDistinctYearsByUser(User user) {
        String sql = "SELECT DISTINCT EXTRACT(YEAR FROM timestamp) " +
                "FROM raw_location_points " +
                "WHERE user_id = ? " +
                "ORDER BY EXTRACT(YEAR FROM timestamp) DESC";
        return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
    }

    public RawLocationPoint create(User user, RawLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, activity_provided, geom, processed) " +
                "VALUES (?, ?, ?, ?, CAST(? AS geometry), ?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getActivityProvided(),
                rawLocationPoint.getGeom().toString(), // Would need PostGIS handling
                rawLocationPoint.isProcessed()
        );
        return rawLocationPoint.withId(id);
    }

    public RawLocationPoint update(RawLocationPoint rawLocationPoint) {
        String sql = "UPDATE raw_location_points SET timestamp = ?, accuracy_meters = ?, activity_provided = ?, geom = CAST(? AS geometry), processed = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getActivityProvided(),
                rawLocationPoint.getGeom().toString(), // Would need PostGIS handling
                rawLocationPoint.isProcessed(),
                rawLocationPoint.getId()
        );
        return rawLocationPoint;
    }

    public Optional<RawLocationPoint> findById(Long id) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.activity_provided, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.id = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM raw_location_points WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            User user, Instant startTime, Instant endTime, int minimumPoints, double distanceInMeters) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.activity_provided, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version , " +
                "ST_ClusterDBSCAN(rlp.geom, ?, ?) over () AS cluster_id " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

                    RawLocationPoint point = new RawLocationPoint(
                            rs.getLong("id"),
                            rs.getTimestamp("timestamp").toInstant(),
                            this.pointReaderWriter.read(rs.getString("geom")),
                            rs.getDouble("accuracy_meters"),
                            rs.getString("activity_provided"),
                            rs.getBoolean("processed"),
                            rs.getLong("version")
                    );

                    Integer clusterId = rs.getObject("cluster_id", Integer.class);

                    return new ClusteredPoint(point, clusterId);
                }, distanceInMeters, minimumPoints, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM raw_location_points";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    public void bulkInsert(User user, List<LocationDataRequest.LocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO raw_location_points (user_id , activity_provided, timestamp, accuracy_meters, geom, processed) " +
                "VALUES (?, ?,  ?,  ?, CAST(? AS geometry), false) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationDataRequest.LocationPoint point : points) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[]{
                    user.getId(),
                    point.getActivity(),
                    timestamp,
                    point.getAccuracyMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void bulkUpdateProcessedStatus(List<RawLocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        
        String sql = "UPDATE raw_location_points SET processed = true WHERE id = ?";
        
        List<Object[]> batchArgs = points.stream()
                .map(point -> new Object[]{point.getId()})
                .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void deleteAll() {
        String sql = "DELETE FROM raw_location_points";
        jdbcTemplate.update(sql);
    }

    public void markAllAsUnprocessedForUser(User user) {
        String sql = "UPDATE raw_location_points SET processed = false WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM raw_location_points WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
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
