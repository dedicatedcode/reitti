package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.ClusteredPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
                rs.getObject("elevation_meters", Double.class),
                rs.getBoolean("processed"),
                rs.getBoolean("synthetic"),
                rs.getBoolean("ignored"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.geometryFactory = geometryFactory;
    }


    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndDateRange(User user, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.valueOf(startTime), Timestamp.valueOf(endTime));
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestamp(User user) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId());
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestampWithLimit(User user, int limit, int offset) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false " +
                "ORDER BY rlp.timestamp " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), limit, offset);
    }

    public List<Integer> findDistinctYearsByUser(User user) {
        String sql = "SELECT DISTINCT EXTRACT(YEAR FROM timestamp) " +
                "FROM raw_location_points " +
                "WHERE user_id = ? " +
                "ORDER BY EXTRACT(YEAR FROM timestamp) DESC";
        return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
    }

    public RawLocationPoint create(User user, RawLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, ignored) " +
                "VALUES (?, ?, ?, ?, ST_GeomFromText(?, '4326'), ?, ?, ?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed(),
                rawLocationPoint.isSynthetic(),
                rawLocationPoint.isIgnored()
        );
        return rawLocationPoint.withId(id);
    }

    public RawLocationPoint update(RawLocationPoint rawLocationPoint) {
        String sql = "UPDATE raw_location_points SET timestamp = ?, accuracy_meters = ?, elevation_meters = ?, geom = ST_GeomFromText(?, '4326'), processed = ?, synthetic = ?, ignored = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed(),
                rawLocationPoint.isSynthetic(),
                rawLocationPoint.isIgnored(),
                rawLocationPoint.getId()
        );
        return rawLocationPoint;
    }

    public Optional<RawLocationPoint> findById(Long id) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.id = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<RawLocationPoint> findLatest(User user, Instant since) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? " +
                "ORDER BY rlp.timestamp LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), Timestamp.from(since));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<RawLocationPoint> findLatest(User user) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? " +
                "ORDER BY rlp.timestamp DESC LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM raw_location_points WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            User user, Instant startTime, Instant endTime, int minimumPoints, double distanceInMeters) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version , " +
                "ST_ClusterDBSCAN(rlp.geom, ?, ?) over () AS cluster_id " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

                    RawLocationPoint point = new RawLocationPoint(
                            rs.getLong("id"),
                            rs.getTimestamp("timestamp").toInstant(),
                            this.pointReaderWriter.read(rs.getString("geom")),
                            rs.getDouble("accuracy_meters"),
                            rs.getObject("elevation_meters", Double.class),
                            rs.getBoolean("processed"),
                            rs.getBoolean("synthetic"),
                            rs.getBoolean("ignored"),
                            rs.getLong("version")
                    );

                    Integer clusterId = rs.getObject("cluster_id", Integer.class);

                    return new ClusteredPoint(point, clusterId);
                }, distanceInMeters, minimumPoints, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points", Long.class);
    }

    public List<RawLocationPoint> findPointsInBoxWithNeighbors(
            User user,
            Instant startTime,
            Instant endTime,
            double minLat,
            double maxLat,
            double minLon,
            double maxLon) {

        String sql = """
            WITH all_points AS (
                SELECT
                    id,
                    user_id,
                    timestamp,
                    geom,
                    accuracy_meters,
                    elevation_meters,
                    processed,
                    version,
                    ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)) as in_box,
                    LAG(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                        OVER (ORDER BY timestamp) as prev_in_box,
                    LEAD(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                        OVER (ORDER BY timestamp) as next_in_box
                FROM raw_location_points
                WHERE user_id = ?
                  AND timestamp BETWEEN ? AND ?
            )
            SELECT
                id,
                user_id,
                timestamp,
                ST_AsText(geom) as geom,
                accuracy_meters,
                elevation_meters,
                processed,
                synthetic,
                ignored,
                version
            FROM all_points
            WHERE in_box = true
               OR prev_in_box = true
               OR next_in_box = true
            ORDER BY timestamp
            """;

        return jdbcTemplate.query(
                sql,
                rawLocationPointRowMapper,
                // ST_MakeEnvelope params for in_box
                minLon, minLat, maxLon, maxLat,
                // ST_MakeEnvelope params for prev_in_box
                minLon, minLat, maxLon, maxLat,
                // ST_MakeEnvelope params for next_in_box
                minLon, minLat, maxLon, maxLat,
                // WHERE clause params
                user.getId(),
                Timestamp.from(startTime),
                Timestamp.from(endTime)
        );
    }

    public long countByUser(User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE user_id = ?", Long.class, user.getId());
    }

    public int bulkInsert(User user, List<LocationPoint> points) {
        if (points.isEmpty()) {
            return -1;
        }
        
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, ignored) " +
                "VALUES (?, ?, ?, ?, CAST(? AS geometry), false, false, false) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationPoint point : points) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[]{
                    user.getId(),
                    timestamp,
                    point.getAccuracyMeters(),
                    point.getElevationMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        int[] ints = jdbcTemplate.batchUpdate(sql, batchArgs);
        return Arrays.stream(ints).sum();
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

    public void markAllAsUnprocessedForUserAfter(User user, Instant start) {
        String sql = "UPDATE raw_location_points SET processed = false WHERE user_id = ? AND timestamp > ?";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(start));
    }

    public void markAllAsUnprocessedForUserBetween(User user, Instant start, Instant end) {
        String sql = "UPDATE raw_location_points SET processed = false WHERE user_id = ? AND timestamp BETWEEN ? AND ?";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(start), Timestamp.from(end));
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM raw_location_points WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    public Optional<RawLocationPoint> findProximatePoint(User user, Instant when, int maxOffsetInSeconds) {
        List<RawLocationPoint> result = findByUserAndTimestampBetweenOrderByTimestampAsc(user, when.minusSeconds(maxOffsetInSeconds / 2), when.plusSeconds(maxOffsetInSeconds / 2));
        return result.stream().findFirst();
    }

    public boolean containsData(User user, Instant start, Instant end) {
        Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ? AND timestamp > ? AND timestamp < ? LIMIT 1",
                Integer.class,
                user.getId(),
                start != null ? Timestamp.from(start) : Timestamp.valueOf("1970-01-01 00:00:00"),
                Timestamp.from(end));
        return count != null && count > 0;
    }

    public boolean containsDataAfter(User user, Instant start) {
        Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ? AND timestamp > ? LIMIT 1",
                Integer.class,
                user.getId(),
                Timestamp.from(start));
        return count != null && count > 0;
    }

    // New methods for density normalization
    
    public List<RawLocationPoint> findSurroundingPoints(User user, Instant timestamp, Duration window) {
        Instant start = timestamp.minus(window);
        Instant end = timestamp.plus(window);
        
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(start), Timestamp.from(end));
    }
    
    public List<RawLocationPoint> findSyntheticPointsInRange(User user, Instant start, Instant end) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND rlp.synthetic = true " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(start), Timestamp.from(end));
    }
    
    public int bulkInsertSynthetic(User user, List<LocationPoint> syntheticPoints) {
        if (syntheticPoints.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, ignored) " +
                "VALUES (?, ?, ?, ?, CAST(? AS geometry), false, true, false) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationPoint point : syntheticPoints) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[]{
                    user.getId(),
                    timestamp,
                    point.getAccuracyMeters(),
                    point.getElevationMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        int[] ints = jdbcTemplate.batchUpdate(sql, batchArgs);
        return Arrays.stream(ints).sum();
    }
    
    public void deleteSyntheticPointsInRange(User user, Instant start, Instant end) {
        String sql = "DELETE FROM raw_location_points WHERE user_id = ? AND timestamp BETWEEN ? AND ? AND synthetic = true";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(start), Timestamp.from(end));
    }
    
    public void bulkUpdateIgnoredStatus(List<Long> pointIds, boolean ignored) {
        if (pointIds.isEmpty()) {
            return;
        }
        
        String sql = "UPDATE raw_location_points SET ignored = ? WHERE id = ?";
        
        List<Object[]> batchArgs = pointIds.stream()
                .map(pointId -> new Object[]{ignored, pointId})
                .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
    
    public List<RawLocationPoint> findByUserAndTimeRangeWithFlags(User user, Instant start, Instant end, Boolean synthetic, Boolean ignored) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version ");
        sqlBuilder.append("FROM raw_location_points rlp ");
        sqlBuilder.append("WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? ");
        
        List<Object> params = new ArrayList<>();
        params.add(user.getId());
        params.add(Timestamp.from(start));
        params.add(Timestamp.from(end));
        
        if (synthetic != null) {
            sqlBuilder.append("AND rlp.synthetic = ? ");
            params.add(synthetic);
        }
        
        if (ignored != null) {
            sqlBuilder.append("AND rlp.ignored = ? ");
            params.add(ignored);
        }
        
        sqlBuilder.append("ORDER BY rlp.timestamp");
        
        return jdbcTemplate.query(sqlBuilder.toString(), rawLocationPointRowMapper, params.toArray());
    }

    public int bulkUpsertSynthetic(User user, List<LocationPoint> toInsert) {
        if (toInsert.isEmpty()) {
            return 0;
        }
        
        String sql = """
            INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, ignored)
            VALUES (?, ?, ?, ?, CAST(? AS geometry), false, true, false)
            ON CONFLICT (user_id, timestamp) DO UPDATE SET
                accuracy_meters = EXCLUDED.accuracy_meters,
                elevation_meters = EXCLUDED.elevation_meters,
                geom = EXCLUDED.geom,
                processed = false,
                synthetic = true,
                ignored = false;
            """;

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationPoint point : toInsert) {
            ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
            Timestamp timestamp = Timestamp.from(parse.toInstant());
            batchArgs.add(new Object[]{
                    user.getId(),
                    timestamp,
                    point.getAccuracyMeters(),
                    point.getElevationMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        int[] ints = jdbcTemplate.batchUpdate(sql, batchArgs);
        return Arrays.stream(ints).sum();
    }

    public void deleteSyntheticByIds(List<Long> toDelete) {
        if (toDelete == null || toDelete.isEmpty()) {
            return;
        }
        
        String placeholders = String.join(",", toDelete.stream().map(id -> "?").toList());
        String sql = "DELETE FROM raw_location_points WHERE id IN (" + placeholders + ") AND synthetic = true";
        
        jdbcTemplate.update(sql, toDelete.toArray());
    }
}
