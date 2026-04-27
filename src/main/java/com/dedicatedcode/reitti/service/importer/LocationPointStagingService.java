package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Service
public class LocationPointStagingService {
    private static final Logger log = LoggerFactory.getLogger(LocationPointStagingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ImportJobRepository importJobRepository;

    public LocationPointStagingService(JdbcTemplate jdbcTemplate, ImportJobRepository importJobRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.importJobRepository = importJobRepository;
    }

    public void start(UUID jobId, User user, String source) {
        String tableName = getTableName(jobId);
        String sql = String.format(
                "CREATE TABLE %s PARTITION OF staging_location_points FOR VALUES IN ('%s')",
                tableName, jobId
        );
        this.jdbcTemplate.execute(sql);
        this.importJobRepository.create(jobId, user, JobType.GPS_IMPORT, JobState.PREPARING, source);
        log.debug("Created partition [{}]", tableName);
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public void dropPartition(UUID jobId) {
        String tableName = getTableName(jobId);
        this.jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
        log.debug("Dropped partition [{}]", tableName);
    }

    private String getTableName(UUID jobId) {
        return "staging_location_points_" + jobId.toString().replace("-", "_");
    }

    public void insertBatch(UUID jobId, User user, Device device, List<LocationPoint> batch) {
        String sql = """
            INSERT INTO staging_location_points (
                job_id,
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
                ps.setObject(1, jobId);
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

    public int promote(UUID jobId) {
        String sql = """
            INSERT INTO raw_location_points (
                user_id, timestamp, accuracy_meters, elevation_meters,
                geom, processed, synthetic, invalid, ignored
            )
            SELECT
                user_id, timestamp, accuracy_meters, elevation_meters,
                geom, false, false, false, false
            FROM staging_location_points
            WHERE job_id = ?
            ON CONFLICT (user_id, timestamp) DO NOTHING;
        """;
        return jdbcTemplate.update(sql, jobId);
    }

    public TimeRange getTimeRange(UUID jobId) {
        String sql = "SELECT MIN(timestamp), MAX(timestamp) FROM staging_location_points WHERE job_id = ?";
        return this.jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new TimeRange(rs.getTimestamp("min").toInstant(), rs.getTimestamp("max").toInstant()), jobId);
    }
}
