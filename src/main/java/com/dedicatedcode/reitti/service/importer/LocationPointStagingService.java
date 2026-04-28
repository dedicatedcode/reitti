package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Service
public class LocationPointStagingService {
    private static final Logger log = LoggerFactory.getLogger(LocationPointStagingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;

    public LocationPointStagingService(JdbcTemplate jdbcTemplate,
                                       @Value("${reitti.import.batch-size:1000}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
    }

    public void ensurePartitionExists(String partitionKey) {
        String tableName = getTableName(partitionKey);
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF staging_location_points FOR VALUES IN ('%s')",
                tableName, partitionKey
        );
        this.jdbcTemplate.execute(sql);
        log.debug("Ensured partition [{}] exists", tableName);
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public void dropPartition(String partitionKey) {
        String tableName = getTableName(partitionKey);
        this.jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
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
                accuracy_meters
            ) VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?)
        """;

        List<LocationPoint> filtered = batch.stream().filter(LocationPoint::isValid).toList();

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LocationPoint point = filtered.get(i);
                ps.setObject(1, partitionKey);
                ps.setTimestamp(2, Timestamp.from(point.getTimestamp()));
                ps.setLong(3, user.getId());
                ps.setObject(4, device != null ? device.id() : null);
                ps.setDouble(5, point.getLongitude());
                ps.setDouble(6, point.getLatitude());

                if (point.getElevationMeters() != null) {
                    ps.setDouble(7, point.getElevationMeters());
                } else {
                    ps.setNull(7, Types.DOUBLE);
                }
                ps.setDouble(8, point.getAccuracyMeters());
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
            INSERT INTO raw_location_points (
                user_id, timestamp, accuracy_meters, elevation_meters,
                geom, processed, synthetic, invalid, ignored
            )
            SELECT
                user_id, timestamp, accuracy_meters, elevation_meters,
                geom, false, false, false, false
            FROM staging_location_points
                    WHERE partition_key = ? AND promoted = FALSE
            ON CONFLICT (user_id, timestamp) DO NOTHING;
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
        List<String> partitions = jdbcTemplate.queryForList(
                "SELECT relid::regclass::text FROM pg_partition_tree('staging_location_points') WHERE isleaf = true",
                String.class
        );

        for (String part : partitions) {
            try {
                String tableNameOnly = part.contains(".") ? part.substring(part.lastIndexOf('.') + 1) : part;

                Boolean isInactive = jdbcTemplate.queryForObject("""
                                                                         SELECT (now() - GREATEST(last_vacuum, last_analyze, last_autoanalyze, now() - interval '1 year')) > interval '12 hours'
                                                                         FROM pg_stat_user_tables WHERE relname = ?
                                                                         """, Boolean.class, tableNameOnly);

                // 3. Check Promotion Status: Any data left behind?
                Integer unpromotedCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + part + " WHERE promoted = FALSE", Integer.class);

                // 4. Execution: Only drop if it's quiet AND finished
                if (Boolean.TRUE.equals(isInactive) && unpromotedCount != null && unpromotedCount == 0) {
                    log.info("Janitor: Dropping fully promoted and inactive partition [{}]", part);
                    jdbcTemplate.execute("DROP TABLE " + part);
                } else {
                    log.debug("Janitor: Skipping partition [{}]. Inactive: {}, Unpromoted: {}",
                              part, isInactive, unpromotedCount);
                }
            } catch (Exception e) {
                log.error("Janitor: Failed to process cleanup for partition [{}]", part, e);
            }
        }
    }
}
