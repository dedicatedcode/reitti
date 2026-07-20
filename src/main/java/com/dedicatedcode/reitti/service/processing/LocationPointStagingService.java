package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationPointStagingService {
    private static final Logger log = LoggerFactory.getLogger(LocationPointStagingService.class);
    private final Set<String> initializedPartitions = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbcTemplate;
    private final SpatialCoverageService spatialCoverageService;
    private final int batchSize;

    public LocationPointStagingService(JdbcTemplate jdbcTemplate,
                                       SpatialCoverageService spatialCoverageService,
                                       @Value("${reitti.import.batch-size:1000}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.spatialCoverageService = spatialCoverageService;
        this.batchSize = batchSize;
    }

    public void ensurePartitionExists(String partitionKey) {
        if (!initializedPartitions.contains(partitionKey)) {
            String tableName = getTableName(partitionKey);
            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF staging_location_points FOR VALUES IN ('%s')",
                    tableName, partitionKey
            );
            this.jdbcTemplate.execute(sql);
            this.jdbcTemplate.update("INSERT INTO partition_registry(partition_name) VALUES(?) ON CONFLICT DO NOTHING", partitionKey);
            log.debug("Ensured partition [{}] exists", tableName);
            initializedPartitions.add(partitionKey);
        }
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public void dropPartition(String partitionKey) {
        String tableName = getTableName(partitionKey);
        this.jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        this.jdbcTemplate.update("DELETE FROM partition_registry WHERE partition_name = ?", partitionKey);
        this.initializedPartitions.remove(partitionKey);

        log.debug("Dropped partition [{}]", tableName);
    }

    public int getBatchSize() {
        return batchSize;
    }

    private String getTableName(String partitionKey) {
        return "staged_" + partitionKey.toLowerCase().replace("-", "_").replace(".", "_");

    }

    public void insertBatch(String partitionKey, User user, Device device, List<LocationPoint> batch) {
        String sql = """
            INSERT INTO staging_location_points (
                partition_key,
                timestamp,
                user_id,
                device_id,
                geom,
                elevation_meters,
                accuracy_meters,
                h3_cell
            ) VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?)
        """;

        List<LocationPoint> filtered = batch.stream().filter(LocationPoint::isValid).toList();

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LocationPoint point = filtered.get(i);
                ps.setObject(1, partitionKey);
                ps.setTimestamp(2, Timestamp.from(point.getTimestamp()));
                ps.setLong(3, user.getId());
                ps.setObject(4, device.id());
                ps.setDouble(5, point.getLongitude());
                ps.setDouble(6, point.getLatitude());

                if (point.getElevationMeters() != null) {
                    ps.setDouble(7, point.getElevationMeters());
                } else {
                    ps.setNull(7, Types.DOUBLE);
                }
                ps.setDouble(8, point.getAccuracyMeters());
                ps.setLong(9, spatialCoverageService.getLevelCellForPoint(point.getLatitude(), point.getLongitude(), 12));
            }

            @Override
            public int getBatchSize() {
                return filtered.size();
            }
        });
    }

    @Transactional
    public int promote(String partitionKey) {
        String sql = """
            INSERT INTO raw_source_points (
                user_id, device_id, timestamp, accuracy_meters, elevation_meters,
                geom, invalid, status
            )
            SELECT
                user_id, device_id, timestamp, accuracy_meters, elevation_meters,
                geom, false, 0
            FROM staging_location_points
                    WHERE partition_key = ? AND promoted = FALSE
            ON CONFLICT (user_id, device_id, timestamp) DO NOTHING;
        """;
        int update = jdbcTemplate.update(sql, partitionKey);
        this.jdbcTemplate.update("UPDATE staging_location_points SET promoted = TRUE WHERE partition_key = ? AND promoted = FALSE", partitionKey);
        return update;
    }

    public TimeRange getTimeRange(String partitionKey) {
        String sql = "SELECT MIN(timestamp) as start_time, MAX(timestamp) as end_time FROM staging_location_points WHERE partition_key = ? AND promoted = FALSE";
        return this.jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Timestamp start = rs.getTimestamp("start_time");
            Timestamp end = rs.getTimestamp("end_time");

            if (start == null || end == null) {
                return null;
            }

            return new TimeRange(start.toInstant(), end.toInstant());
        }, partitionKey);
    }

    @Scheduled(cron = "${reitti.import.staging.cleanup.cron}")
    public void nightlyCleanup() {
        String sql = """
                 SELECT partition_name
                 FROM partition_registry
                 WHERE created_at < (now() - interval '7 days')
                 """;

        List<String> stalePartitions = jdbcTemplate.queryForList(sql, String.class);

        for (String part : stalePartitions) {
            // 2. Perform the drop
            try {
                log.info("Janitor: Dropping stale partition [{}]", part);
                dropPartitionSafely(getTableName(part));

                // 3. Remove from registry
                this.jdbcTemplate.update("DELETE FROM partition_registry WHERE partition_name = ?", part);
                this.initializedPartitions.remove(part);
            } catch (Exception e) {
                log.error("Janitor: Failed to drop partition [{}]", part, e);
            }
        }
    }

    private void dropPartitionSafely(String tableName) {
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL lock_timeout = '5s'");
                stmt.execute("ALTER TABLE staging_location_points DETACH PARTITION " + tableName + " CONCURRENTLY");
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            }
            return null;
        });
    }

}
