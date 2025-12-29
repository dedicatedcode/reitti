package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.Visit;
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
                rs.getObject("elevation_meters", Double.class),
                rs.getBoolean("processed"),
                rs.getBoolean("synthetic"),
                rs.getBoolean("ignored"),
                rs.getLong("version")
        );

        this.pointReaderWriter = pointReaderWriter;
    }

    public List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.timestamp BETWEEN ? AND ? AND preview_id = ? " +
                "ORDER BY rlp.timestamp";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper,
                user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), previewId);
    }

    public List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestampWithLimit(User user, String previewId, int limit, int offset) {
        String sql = "SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.processed, rlp.synthetic, rlp.ignored, rlp.version " +
                "FROM preview_raw_location_points rlp " +
                "WHERE rlp.user_id = ? AND rlp.processed = false AND preview_id = ? " +
                "ORDER BY rlp.timestamp " +
                "LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rawLocationPointRowMapper, user.getId(), previewId, limit, offset);
    }
    public List<Visit> findVisitsInTimerangeForUser(
            User user, String previewId, Instant startTime, Instant endTime, long minimumStayTime, double distanceInMeters) {
        String sql = """
                
                WITH smoothed_data AS (
                                       -- Step 1: Smooth the track using a rolling centroid of the last 4 points
                                       SELECT
                                           *,
                                           ST_Centroid(
                                               ST_Collect(geom) OVER (
                                                   ORDER BY "timestamp"
                                                   ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
                                               )
                                           ) AS smoothed_geom
                                       FROM preview_raw_location_points
                                       WHERE user_id = ? AND preview_id = ? AND "timestamp" BETWEEN ? AND ?
                                   ),
                                   lagged_data AS (
                                       -- Step 2: Add the lagged smoothed geometry and timestamp
                                       SELECT
                                           *,
                                           LAG(smoothed_geom, 4) OVER (ORDER BY "timestamp") AS prev_smoothed_geom,
                                           LAG("timestamp", 4) OVER (ORDER BY "timestamp") AS prev_ts
                                       FROM smoothed_data
                                   ),
                                   island_flags AS (
                                       -- Step 3: Identify breaks (islands) based on smoothed movement
                                       SELECT
                                           *,
                                           CASE
                                               WHEN prev_smoothed_geom IS NULL THEN 1
                                               -- Check if current smoothed center is > 50m from 1 minute ago center
                                               WHEN ST_Distance(smoothed_geom::geography, prev_smoothed_geom::geography) > ? THEN 1
                                               -- Check if time gap is > 10 minutes (600 seconds)
                                               WHEN EXTRACT(EPOCH FROM ("timestamp" - prev_ts)) > ? THEN 1
                                               ELSE 0
                                           END AS is_new_cluster
                                       FROM lagged_data
                                   ),
                                   clustered_points AS (
                                       -- Step 4: Assign a unique ID by summing the flags
                                       SELECT
                                           *,
                                           SUM(is_new_cluster) OVER (ORDER BY "timestamp") AS cluster_id
                                       FROM island_flags
                                   )
                                   -- Step 5: Final Output - Group into "Stay Events"
                                   SELECT
                                       cluster_id,
                                       MIN("timestamp") AS arrival,
                                       MAX("timestamp") AS departure,
                                       EXTRACT(EPOCH FROM (MAX("timestamp") - MIN("timestamp"))) AS duration,
                                       COUNT(*) AS point_count,
                                       ST_AsText(ST_Centroid(ST_Collect(geom))) AS cluster_center_geom
                                   FROM clustered_points
                                   GROUP BY cluster_id
                                   HAVING COUNT(*) >= 4 -- Must stay for at least 1 minute to be a cluster
                                   ORDER BY arrival;
                """;
        return jdbcTemplate.query(sql, (rs, _) -> {
            GeoPoint geom = this.pointReaderWriter.read(rs.getString("cluster_center_geom"));
            return new Visit(geom.longitude(),
                             geom.latitude(),
                             rs.getTimestamp("arrival").toInstant(),
                             rs.getTimestamp("departure").toInstant(),
                             rs.getLong("duration"),
                             false
            );
        }, user.getId(), previewId, Timestamp.from(startTime), Timestamp.from(endTime), distanceInMeters, minimumStayTime);
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
