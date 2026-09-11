package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PreviewRawLocationPointJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RawLocationPoint> rawLocationPointRowMapper;
    private final int pointChunkSize;

    public PreviewRawLocationPointJdbcService(JdbcTemplate jdbcTemplate,
                                              PointReaderWriter pointReaderWriter,
                                              @Value("${reitti.processing.point-chunk-size:5000}") int pointChunkSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.rawLocationPointRowMapper = (rs, _) -> new RawLocationPoint(
                rs.getLong("id"),
                null,
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getObject("elevation_meters", Double.class),
                rs.getBoolean("processed"),
                rs.getBoolean("synthetic"),
                rs.getLong("version")
        );
        this.pointChunkSize = pointChunkSize;
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND preview_id = ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), previewId);
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestampWithLimit(User user, String previewId, int limit, int offset) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false AND preview_id = ? " +
                "ORDER BY rlp.timestamp " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), previewId, limit, offset);
    }

    /**
     * Streams all preview points of the user inside [startTime, endTime] in
     * bounded chunks ordered by (timestamp, id), without materializing the
     * whole range.
     */
    public Iterator<RawLocationPoint> iterateByUserAndTimestampBetween(User user, String previewId, Instant startTime, Instant endTime) {
        return new KeysetPointIterator(
                (afterTimestamp, afterId, limit) -> fetchPointChunk(user, previewId, startTime, endTime, afterTimestamp, afterId, limit),
                pointChunkSize);
    }

    /**
     * Streams all preview points of the user inside [startTime, endTime]
     * together with their count and time bounds.
     */
    public RawLocationPointStream streamByUserAndTimestampBetween(User user, String previewId, Instant startTime, Instant endTime) {
        RawLocationPointStream.Stats stats = this.jdbcTemplate.query(
                "SELECT count(*) AS point_count, min(timestamp) AS min_ts, max(timestamp) AS max_ts " +
                        "FROM preview_raw_location_points WHERE user_id = ? AND preview_id = ? AND timestamp BETWEEN ? AND ?",
                rs -> {
                    rs.next();
                    Timestamp minTs = rs.getTimestamp("min_ts");
                    Timestamp maxTs = rs.getTimestamp("max_ts");
                    return new RawLocationPointStream.Stats(rs.getLong("point_count"),
                            minTs != null ? minTs.toInstant() : null,
                            maxTs != null ? maxTs.toInstant() : null);
                }, user.getId(), previewId, Timestamp.from(startTime), Timestamp.from(endTime));
        return new RawLocationPointStream(stats,
                (afterTimestamp, afterId, limit) -> fetchPointChunk(user, previewId, startTime, endTime, afterTimestamp, afterId, limit),
                pointChunkSize);
    }

    private List<RawLocationPoint> fetchPointChunk(User user, String previewId, Instant startTime, Instant endTime,
                                                   Instant afterTimestamp, Long afterId, int limit) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.version ")
                .append("FROM preview_raw_location_points rlp ")
                .append("WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND preview_id = ? ");
        if (afterTimestamp != null) {
            sql.append("AND (rlp.timestamp > ? OR (rlp.timestamp = ? AND rlp.id > ?)) ");
        }
        sql.append("ORDER BY rlp.timestamp, rlp.id LIMIT ?");
        if (afterTimestamp != null) {
            return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                    user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), previewId,
                    Timestamp.from(afterTimestamp), Timestamp.from(afterTimestamp), afterId, limit);
        }
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), previewId, limit);
    }

    public void bulkUpdateProcessedStatus(List<RawLocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        
        String sql = "UPDATE preview_raw_location_points SET processed = true WHERE id = ?";
        
        List<Object[]> batchArgs = points.stream()
                .map(point -> new Object[]{point.getId()})
                .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

}
