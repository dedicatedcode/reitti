package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ClusteredPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PreviewRawLocationPointJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RawLocationPoint> rawLocationPointRowMapper;
    private final PointReaderWriter pointReaderWriter;

    public PreviewRawLocationPointJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.rawLocationPointRowMapper = (rs, _) -> new RawLocationPoint(
                rs.getLong("id"),
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getBoolean("processed"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND preview_id = ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), previewId);
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestamp(User user, String previewId) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false AND preview_id = ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), previewId);
    }

    public List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            User user, String previewId, Instant startTime, Instant endTime, int minimumPoints, double distanceInMeters) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.version , " +
                "ST_ClusterDBSCAN(rlp.geom, ?, ?) over () AS cluster_id " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND preview_id = ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

                    RawLocationPoint point = new RawLocationPoint(
                            rs.getLong("id"),
                            rs.getTimestamp("timestamp").toInstant(),
                            this.pointReaderWriter.read(rs.getString("geom")),
                            rs.getDouble("accuracy_meters"),
                            rs.getBoolean("processed"),
                            rs.getLong("version")
                    );

                    Integer clusterId = rs.getObject("cluster_id", Integer.class);

                    return new ClusteredPoint(point, clusterId);
                }, distanceInMeters, minimumPoints, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime), previewId);
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
