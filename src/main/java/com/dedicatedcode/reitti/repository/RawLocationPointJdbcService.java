package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.controller.api.v2.CoverageController;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class RawLocationPointJdbcService {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RawLocationPoint> rawLocationPointRowMapper;
    private final PointReaderWriter pointReaderWriter;
    private final int pointChunkSize;

    public RawLocationPointJdbcService(JdbcTemplate jdbcTemplate,
                                       PointReaderWriter pointReaderWriter,
                                       @Value("${reitti.processing.point-chunk-size:5000}") int pointChunkSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.rawLocationPointRowMapper = (rs, _) -> new RawLocationPoint(
                rs.getLong("id"),
                (Long) rs.getObject("source_point_id"),
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getObject("elevation_meters", Double.class),
                rs.getBoolean("processed"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.pointChunkSize = pointChunkSize;
    }


    public List<Instant> findManuallyIgnoredTimestampsIn(User user, Instant start, Instant end) {
        return jdbcTemplate.queryForList(
                "SELECT rsp.timestamp FROM raw_source_points rsp " +
                        "WHERE rsp.user_id = ? AND rsp.status = 1 AND rsp.timestamp >= ? AND rsp.timestamp <= ? " +
                        "ORDER BY rsp.timestamp",
                Instant.class,
                user.getId(),
                Timestamp.from(start),
                Timestamp.from(end));
    }

    public List<Instant> findIgnoredTimestampsIn(User user, Instant start, Instant end) {
        return jdbcTemplate.queryForList(
                "SELECT timestamp FROM raw_location_points " +
                        "WHERE user_id = ? AND status != 0 AND timestamp >= ? AND timestamp < ? " +
                        "ORDER BY timestamp",
                Instant.class,
                user.getId(),
                Timestamp.from(start),
                Timestamp.from(end));
    }

    public Optional<RawLocationPoint> findLastRealBefore(User user, Instant instant) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp < ? " +
                "ORDER BY rlp.timestamp DESC LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), Timestamp.from(instant));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findFirstRealAtOrAfter(User user, Instant instant) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? " +
                "ORDER BY rlp.timestamp ASC LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), Timestamp.from(instant));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? AND rlp.timestamp < ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime, boolean includeIgnored) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.status = 0 ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp");
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime, boolean includeIgnored, int page, int pageSize) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.status = 0 ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp")
                .append(" OFFSET ").append(page * pageSize).append(" LIMIT ").append(pageSize);
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public long countByUserAndTimestampBetween(User user, Instant startTime, Instant endTime, boolean includeIgnored) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT count(*) FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.status = 0 ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ?");
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
        return count != null ? count : 0;
    }

    /**
     * Streams all points of the user inside [startTime, endTime) in bounded
     * chunks ordered by (timestamp, id), without materializing the whole range.
     */
    public RawLocationPointStream streamByUserAndTimestampBetween(User user, Instant startTime, Instant endTime,
                                                                  boolean includeIgnored) {
        StringBuilder statsSql = new StringBuilder()
                .append("SELECT count(*) AS point_count, min(timestamp) AS min_ts, max(timestamp) AS max_ts ")
                .append("FROM raw_location_points WHERE user_id = ? ");
        if (!includeIgnored) {
            statsSql.append("AND status = 0 ");
        }
        statsSql.append("AND timestamp >= ? AND timestamp < ?");
        RawLocationPointStream.Stats stats = this.jdbcTemplate.query(statsSql.toString(), PointStreamSupport.STATS_EXTRACTOR,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
        return new RawLocationPointStream(stats,
                (afterTimestamp, afterId, limit) -> fetchPointChunk(user, startTime, endTime, includeIgnored, afterTimestamp, afterId, limit),
                pointChunkSize);
    }

    /**
     * Streams all points (ignored included) of the user inside [startTime, endTime).
     */
    public RawLocationPointStream streamByUserAndTimestampBetween(User user, Instant startTime, Instant endTime) {
        return streamByUserAndTimestampBetween(user, startTime, endTime, true);
    }

    private List<RawLocationPoint> fetchPointChunk(User user, Instant startTime, Instant endTime,
                                                   boolean includeIgnored,
                                                   Instant afterTimestamp, Long afterId, int limit) {
        Timestamp after = afterTimestamp != null ? Timestamp.from(afterTimestamp) : null;
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.status = 0 ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ");
        PointStreamSupport.appendKeysetPredicate(sql, after);
        sql.append("ORDER BY rlp.timestamp, rlp.id LIMIT ?");
        if (after != null) {
            return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                    user.getId(), Timestamp.from(startTime), Timestamp.from(endTime),
                    after, afterId, limit);
        }
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), limit);
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestampWithLimit(User user, int limit, int offset) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false " +
                "ORDER BY rlp.timestamp " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), limit, offset);
    }

    public List<RawLocationPoint> findByUserOrderByTimestampWithKeyset(User user, Instant afterTimestamp, int limit) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version ")
                .append("FROM raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? ");
        Timestamp after = afterTimestamp != null ? Timestamp.from(afterTimestamp) : null;
        if (after != null) {
            sql.append("AND rlp.timestamp > ? ");
        }
        sql.append("ORDER BY rlp.timestamp LIMIT ?");

        if (after != null) {
            return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper, user.getId(), after, limit);
        }
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper, user.getId(), limit);
    }

    public List<Integer> findDistinctYearsByUser(User user) {
        String sql = """
                SELECT DISTINCT EXTRACT(YEAR FROM day)
                  FROM location_daily_summary
                WHERE user_id = ?
                ORDER BY EXTRACT(YEAR FROM day) DESC
                """;
        return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
    }

    public RawLocationPoint create(User user, RawLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed) " +
                "VALUES (?, ?, ?, ?, ST_GeomFromText(?, '4326'), ?) ON CONFLICT DO NOTHING RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed()
        );
        return rawLocationPoint.withId(id);
    }

    public RawLocationPoint update(User user, RawLocationPoint rawLocationPoint) {
        String sql = "UPDATE raw_location_points SET timestamp = ?, accuracy_meters = ?, elevation_meters = ?, geom = ST_GeomFromText(?, '4326'), processed = ? WHERE id = ? AND user_id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isProcessed(),
                rawLocationPoint.getId(),
                user.getId()
        );
        return rawLocationPoint;
    }

    public Optional<RawLocationPoint> findById(User user, Long id) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.id = ? AND rlp.user_id = ?";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, id, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findLatest(User user, Instant since) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp >= ? " +
                "ORDER BY rlp.timestamp LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), Timestamp.from(since));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findLatest(User user) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? " +
                "ORDER BY rlp.timestamp DESC LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<RawLocationPoint> findEarliest(User user) {
        String sql = "SELECT rlp.id, rlp.source_point_id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM raw_location_points rlp " +
                "WHERE rlp.user_id = ? " +
                "ORDER BY rlp.timestamp ASC LIMIT 1";
        List<RawLocationPoint> results = jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @SuppressWarnings("DataFlowIssue")
    public long countByUser(User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE user_id = ?", Long.class, user.getId());
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

    public void markAllAsUnprocessedForUser(User user, List<LocalDate> affectedDays) {
        this.jdbcTemplate.update("UPDATE raw_location_points SET processed = false WHERE user_id = ? AND date_trunc('day', timestamp) = ANY(?)",
                                 user.getId(),
                                 affectedDays.stream().map(d -> Timestamp.valueOf(d.atStartOfDay())).toList().toArray(new Timestamp[0]));
    }

    public void markUnprocessedForUserAndTimeRange(User user, Instant start, Instant end) {
        this.jdbcTemplate.update("UPDATE raw_location_points SET processed = false WHERE user_id = ? AND timestamp >= ? AND timestamp <= ?",
                                 user.getId(), Timestamp.from(start), Timestamp.from(end));
    }

    public void markUnprocessedForUserAndBoundingBox(User user, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        this.jdbcTemplate.update("UPDATE raw_location_points SET processed = false WHERE user_id = ? AND ST_Intersects(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))",
                                 user.getId(), minLongitude, minLatitude, maxLongitude, maxLatitude);
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM raw_location_points WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    public void replaceLatestForUser(User user, LocationPoint locationPoint) {
        this.jdbcTemplate.update(
                """
                INSERT INTO raw_location_points (user_id, timestamp, accuracy_meters, elevation_meters, geom, processed)
                VALUES (?, ?, ?, ?, ST_GeomFromText(?, '4326'), false)
                ON CONFLICT (user_id, timestamp) DO UPDATE SET
                    accuracy_meters = EXCLUDED.accuracy_meters,
                    elevation_meters = EXCLUDED.elevation_meters,
                    geom = EXCLUDED.geom
                """,
                user.getId(),
                Timestamp.from(locationPoint.getTimestamp()),
                locationPoint.getAccuracyMeters(),
                locationPoint.getElevationMeters(),
                pointReaderWriter.write(new GeoPoint(locationPoint.getLatitude(), locationPoint.getLongitude()))
        );
        this.jdbcTemplate.update(
                "DELETE FROM raw_location_points WHERE user_id = ? AND timestamp < ?",
                user.getId(), Timestamp.from(locationPoint.getTimestamp())
        );
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

    public MapMetadata getMetadata(User user, Instant start, Instant end) {

        boolean useRawTable = Duration.between(start, end).toDays() <= 31;

        String sql = useRawTable ? """
                SELECT
                  EXTRACT(EPOCH FROM MIN(timestamp)) as min_ts,
                  EXTRACT(EPOCH FROM MAX(timestamp)) as max_ts,
                  COUNT(*) as total_count,
                  ST_YMin(ST_Extent(geom)) as min_lat,
                  ST_YMax(ST_Extent(geom)) as max_lat,
                  ST_XMin(ST_Extent(geom)) as min_lng,
                  ST_XMax(ST_Extent(geom)) as max_lng
                FROM raw_location_points
                WHERE user_id = ?
                  AND timestamp >= ? AND timestamp < ?
                """ : """
                SELECT
                  EXTRACT(EPOCH FROM MIN(min_ts)) as min_ts,
                  EXTRACT(EPOCH FROM MAX(max_ts)) as max_ts,
                  SUM(point_count) as total_count,
                  MIN(ST_YMin(bbox::geometry)) as min_lat,
                  MAX(ST_YMax(bbox::geometry)) as max_lat,
                  MIN(ST_XMin(bbox::geometry)) as min_lng,
                  MAX(ST_XMax(bbox::geometry)) as max_lng
                FROM location_daily_summary
                WHERE user_id = ?
                  AND day >= ?::date AND day < ?::date
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new MapMetadata(
                rs.getLong("min_ts"),
                rs.getLong("max_ts"),
                rs.getLong("total_count"),
                rs.getDouble("min_lat"),
                rs.getDouble("max_lat"),
                rs.getDouble("min_lng"),
                rs.getDouble("max_lng"),
                this.findLatest(user).map(rawLocationPoint -> {
                    LocationPoint locationPoint = new LocationPoint();
                    locationPoint.setTimestamp(rawLocationPoint.getTimestamp());
                    locationPoint.setLatitude(rawLocationPoint.getLatitude());
                    locationPoint.setLongitude(rawLocationPoint.getLongitude());
                    locationPoint.setAccuracyMeters(rawLocationPoint.getAccuracyMeters());
                    locationPoint.setElevationMeters(rawLocationPoint.getElevationMeters());
                    return locationPoint;
                })
        ), user.getId(), Timestamp.from(start), Timestamp.from(end));
    }

    @SuppressWarnings("DataFlowIssue")
    public long countUnprocessedByUser(User user) {
        return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE user_id = ? AND processed = false", Long.class, user.getId());
    }

    public void dropForReSeeding(User user, TimeRange timeRange) {
        this.jdbcTemplate.update("DELETE FROM raw_location_points WHERE user_id = ? AND timestamp >= ? AND timestamp < ?",
                                 user.getId(),
                                 Timestamp.from(timeRange.start()),
                                 Timestamp.from(timeRange.end()));
    }

    public int updateFromDevices(User user, TimeRange timeRange) {
       return this.jdbcTemplate.update("""
                INSERT INTO raw_location_points
                (accuracy_meters, timestamp, user_id, geom, elevation_meters, source_point_id, processed, status, h3_cell)
                SELECT DISTINCT
                  accuracy_meters, timestamp, user_id, geom, elevation_meters, source_point_id, FALSE, status, h3_cell
                FROM v_source_stream
                WHERE user_id = ? AND timestamp  >= ? AND timestamp < ?
                """
               ,user.getId(), Timestamp.from(timeRange.start()), Timestamp.from(timeRange.end()));
    }

    public List<CoverageController.H3CellCount> findVisitedH3CellsCounts(User user, Instant startOfRange, Instant endOfRange) {
        return this.jdbcTemplate.query("""
                                           SELECT h3_cell, COUNT(*), date_bin('5 minutes', timestamp, TIMESTAMP '2001-01-01') AS time_bucket
                                           FROM v_source_stream
                                           WHERE user_id = ? AND timestamp >= ? AND timestamp < ? AND h3_cell IS NOT NULL
                                           GROUP BY h3_cell, time_bucket;
                                           """, (rs, _) -> new CoverageController.H3CellCount(rs.getString("h3_cell"), rs.getTimestamp("time_bucket").toInstant(), rs.getLong("count")),
                                user.getId(),
                                Timestamp.from(startOfRange),
                                Timestamp.from(endOfRange));
    }
}
