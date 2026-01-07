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
import java.time.LocalDate;
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
        this.rawLocationPointRowMapper = (rs, _) -> new RawLocationPoint(
                rs.getLong("id"),
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getObject("elevation_meters", Double.class),
                rs.getBoolean("processed"),
                rs.getBoolean("synthetic"),
                rs.getBoolean("ignored"),
                rs.getBoolean("invalid"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.geometryFactory = geometryFactory;
    }


    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? AND rlp.timestamp < ? AND rlp.invalid = false " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(User user, Instant startTime, Instant endTime, boolean includeSynthetic, boolean includeIgnored, boolean includeInvalid) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeSynthetic) {
            sql.append("AND rlp.synthetic = false ");
        }
        if (!includeIgnored) {
            sql.append("AND rlp.ignored = false ");
        }
        if (!includeInvalid) {
            sql.append("AND rlp.invalid = false ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp");
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime, boolean includeSynthetic, boolean includeIgnored, boolean includeInvalid, int page, int pageSize) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeSynthetic) {
            sql.append("AND rlp.synthetic = false ");
        }
        if (!includeIgnored) {
            sql.append("AND rlp.ignored = false ");
        }
        if (!includeInvalid) {
            sql.append("AND rlp.invalid = false ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp")
                .append(" OFFSET ").append(page * pageSize).append(" LIMIT ").append(pageSize);
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @SuppressWarnings("DataFlowIssue")
    public long countByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime, boolean includeSynthetic, boolean includeIgnored, boolean includeInvalid) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT COUNT(*)")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeSynthetic) {
            sql.append("AND rlp.synthetic = false ");
        }
        if (!includeIgnored) {
            sql.append("AND rlp.ignored = false ");
        }
        if (!includeInvalid) {
            sql.append("AND rlp.invalid = false ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ");
        return jdbcTemplate.queryForObject(sql.toString(), Long.class,
                                  user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestampWithLimit(User user, int limit, int offset) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.invalid, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false AND rlp.invalid = false " +
                "ORDER BY rlp.timestamp " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), limit, offset);
    }

    public List<Integer> findDistinctYearsByUser(User user) {
        String sql = "SELECT DISTINCT EXTRACT(YEAR FROM timestamp) " +
                "FROM raw_location_points " +
                "WHERE user_id = ? AND invalid = false " +
                "ORDER BY EXTRACT(YEAR FROM timestamp) DESC";
        return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
    }

    public RawLocationPoint create(User user, RawLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, invalid, ignored) " +
                "VALUES (?, ?, ?, ?, ST_GeomFromText(?, '4326'), ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed(),
                rawLocationPoint.isSynthetic(),
                rawLocationPoint.isInvalid(),
                rawLocationPoint.isIgnored()
        );
        return rawLocationPoint.withId(id);
    }

    public RawLocationPoint update(RawLocationPoint rawLocationPoint) {
        String sql = "UPDATE raw_location_points SET timestamp = ?, accuracy_meters = ?, elevation_meters = ?, geom = ST_GeomFromText(?, '4326'), processed = ?, synthetic = ?, invalid = ?, ignored = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed(),
                rawLocationPoint.isSynthetic(),
                rawLocationPoint.isInvalid(),
                rawLocationPoint.isIgnored(),
                rawLocationPoint.getId()
        );
        return rawLocationPoint;
    }

    public Optional<RawLocationPoint> findById(Long id) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.id = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findLatest(User user, Instant since) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? AND rlp.invalid = false " +
                "ORDER BY rlp.timestamp LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), Timestamp.from(since));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findLatest(User user) {
       return findLatest(user, Instant.EPOCH);
    }

    public List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            User user, Instant startTime, Instant endTime, int minimumPoints, double distanceInMeters) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.invalid, rlp.ignored, rlp.version , " +
                "ST_ClusterDBSCAN(rlp.geom, ?, ?) over () AS cluster_id " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? AND rlp.timestamp < ?";

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
                            rs.getBoolean("invalid"),
                            rs.getLong("version")
                    );

                    Integer clusterId = rs.getObject("cluster_id", Integer.class);

                    return new ClusteredPoint(point, clusterId);
                }, distanceInMeters, minimumPoints, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @SuppressWarnings("DataFlowIssue")
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points", Long.class);
    }

    @SuppressWarnings("DataFlowIssue")
    public List<RawLocationPoint> findPointsInBoxWithNeighbors(
            User user,
            Instant startTime,
            Instant endTime,
            double minLat,
            double maxLat,
            double minLon,
            double maxLon,
            int maxPoints) {
        String countSql = """
            SELECT COUNT(*)
            FROM raw_location_points
            WHERE user_id = ?
              AND ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
              AND timestamp >= ?::timestamp AND timestamp < ?::timestamp
              AND ignored = false AND invalid = false
        """;

        Long relevantPointCount = jdbcTemplate.queryForObject(countSql, Long.class,
                                                              minLon, minLat, maxLon, maxLat,
                                                              user.getId(),
                                                              Timestamp.from(startTime),
                                                              Timestamp.from(endTime)
        );

        // If we have fewer points than the budget, return all without sampling
        if (relevantPointCount <= maxPoints) {
            String sql = """
            WITH box_filtered_points AS (
                SELECT
                    id,
                    user_id,
                    timestamp,
                    geom,
                    accuracy_meters,
                    elevation_meters,
                    processed,
                    ignored,
                    invalid,
                    synthetic,
                    version,
                    ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)) as in_box,
                    LAG(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                        OVER (ORDER BY timestamp) as prev_in_box,
                    LEAD(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                        OVER (ORDER BY timestamp) as next_in_box
                FROM raw_location_points
                WHERE user_id = ?
                  AND timestamp >= ?::timestamp AND timestamp < ?::timestamp
                  AND ignored = false AND invalid = false
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
                invalid,
                version
            FROM box_filtered_points
            WHERE in_box = true
               OR prev_in_box = true
               OR next_in_box = true
            ORDER BY timestamp
            """;

            return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                                      minLon, minLat, maxLon, maxLat,
                                      minLon, minLat, maxLon, maxLat,
                                      minLon, minLat, maxLon, maxLat,
                                      user.getId(),
                                      Timestamp.from(startTime),
                                      Timestamp.from(endTime)
            );
        }

        // Otherwise, apply sampling
        Duration period = Duration.between(startTime, endTime);
        long intervalMinutes = Math.max(1, period.toMinutes() / maxPoints);

        String sql = """
        WITH box_filtered_points AS (
            SELECT
                id,
                user_id,
                timestamp,
                geom,
                accuracy_meters,
                elevation_meters,
                processed,
                ignored,
                invalid,
                synthetic,
                version,
                ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)) as in_box,
                LAG(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                    OVER (ORDER BY timestamp) as prev_in_box,
                LEAD(ST_Within(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326)))
                    OVER (ORDER BY timestamp) as next_in_box
            FROM raw_location_points
            WHERE user_id = ?
              AND timestamp >= ?::timestamp AND timestamp < ?::timestamp
              AND ignored = false AND invalid = false
        ),
        relevant_points AS (
            SELECT *
            FROM box_filtered_points
            WHERE in_box = true
               OR prev_in_box = true
               OR next_in_box = true
        ),
        sampled_points AS (
            SELECT DISTINCT ON (
                date_trunc('hour', timestamp) + 
                (EXTRACT(minute FROM timestamp)::int / %d) * interval '%d minutes'
            )
            id,
            user_id,
            timestamp,
            geom,
            accuracy_meters,
            elevation_meters,
            processed,
            invalid,
            ignored,
            synthetic,
            version
            FROM relevant_points
            ORDER BY 
                date_trunc('hour', timestamp) + 
                (EXTRACT(minute FROM timestamp)::int / %d) * interval '%d minutes',
                timestamp
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
            invalid,
            version
        FROM sampled_points
        ORDER BY timestamp
        """.formatted(intervalMinutes, intervalMinutes, intervalMinutes, intervalMinutes);

        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                                  minLon, minLat, maxLon, maxLat,
                                  minLon, minLat, maxLon, maxLat,
                                  minLon, minLat, maxLon, maxLat,
                                  user.getId(),
                                  Timestamp.from(startTime),
                                  Timestamp.from(endTime)
        );
    }

    public List<RawLocationPoint> findSimplifiedRouteForPeriod(
            User user,
            Instant startTime,
            Instant endTime,
            int maxPoints) {

        // Calculate sampling interval based on time range and desired point count
        Duration period = Duration.between(startTime, endTime);
        long intervalMinutes = Math.max(1, period.toMinutes() / maxPoints);

        String sql = """
        WITH sampled_points AS (
            SELECT DISTINCT ON (
                date_trunc('hour', timestamp) +
                (EXTRACT(minute FROM timestamp)::int / %d) * interval '%d minutes'
            )
            id,
            timestamp,
            geom,
            accuracy_meters,
            elevation_meters,
            processed,
            synthetic,
            invalid,
            ignored,
            version
            FROM raw_location_points
            WHERE user_id = ?
              AND timestamp >= ? AND timestamp < ?
              AND ignored = false AND invalid = false
            ORDER BY
                date_trunc('hour', timestamp) +
                (EXTRACT(minute FROM timestamp)::int / %d) * interval '%d minutes',
                timestamp
        )
        SELECT
            id,
            accuracy_meters,
            elevation_meters,
            timestamp,
            ST_AsText(geom) as geom,
            processed,
            synthetic,
            ignored,
            invalid,
            version
        FROM sampled_points
        ORDER BY timestamp
        """.formatted(intervalMinutes, intervalMinutes, intervalMinutes, intervalMinutes);

        return jdbcTemplate.query(sql,
                                  rawLocationPointRowMapper,
                                  user.getId(),
                                  Timestamp.from(startTime), Timestamp.from(endTime));
    }

    @SuppressWarnings("DataFlowIssue")
    public long countByUser(User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE user_id = ?", Long.class, user.getId());
    }

    public int bulkInsert(User user, List<LocationPoint> points) {
        if (points.isEmpty()) {
            return -1;
        }
        
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, invalid, ignored) " +
                "VALUES (?, ?, ?, ?, CAST(? AS geometry), false, false, false, false) ON CONFLICT DO NOTHING;";

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

    public void resetInvalidStatus(User user, Instant startTime, Instant endTime) {
        String sql = "UPDATE raw_location_points SET invalid = false WHERE user_id = ? AND timestamp >= ? AND timestamp < ?";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public void bulkUpdateInvalidStatus(List<RawLocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }

        String sql = "UPDATE raw_location_points SET invalid = true, processed = true WHERE id = ?";

        List<Object[]> batchArgs = points.stream()
                .map(point -> new Object[]{point.getId()})
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void bulkUpdateIgnoredStatus(List<Long> pointIds, boolean ignored) {
        if (pointIds.isEmpty()) {
            return;
        }

        String sql = "UPDATE raw_location_points SET ignored = ?, processed = true WHERE id = ?";

        List<Object[]> batchArgs = pointIds.stream()
                .map(pointId -> new Object[]{ignored, pointId})
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

    public void markAllAsUnprocessedForUser(User user, List<LocalDate> affectedDays) {
        this.jdbcTemplate.update("UPDATE raw_location_points SET processed = false WHERE user_id = ? AND date_trunc('day', timestamp) = ANY(?)",
                                 user.getId(),
                                 affectedDays.stream().map(d -> Timestamp.valueOf(d.atStartOfDay())).toList().toArray(new Timestamp[0]));
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
        Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ? AND timestamp >= ? AND timestamp < ? LIMIT 1",
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

    public int bulkInsertSynthetic(User user, List<LocationPoint> syntheticPoints) {
        if (syntheticPoints.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed, synthetic, invalid, ignored) " +
                "VALUES (?, ?, ?, ?, CAST(? AS geometry), false, true, false, false) ON CONFLICT DO NOTHING;";

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
        String sql = "DELETE FROM raw_location_points WHERE user_id = ? AND timestamp >= ? AND timestamp < ? AND synthetic = true";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(start), Timestamp.from(end));
    }
}
