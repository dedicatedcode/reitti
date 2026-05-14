package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.DeviceTimeRange;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SourceLocationPointJdbcService {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SourceLocationPoint> rawLocationPointRowMapper;
    private final PointReaderWriter pointReaderWriter;
    private final GeometryFactory geometryFactory;

    public SourceLocationPointJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter, GeometryFactory geometryFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.rawLocationPointRowMapper = (rs, _) -> new SourceLocationPoint(
                rs.getLong("id"),
                rs.getTimestamp("timestamp").toInstant(),
                pointReaderWriter.read(rs.getString("geom")),
                rs.getDouble("accuracy_meters"),
                rs.getObject("elevation_meters", Double.class),
                SourceLocationPoint.Status.fromDbValue(rs.getLong("status")),
                rs.getBoolean("invalid")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.geometryFactory = geometryFactory;
    }

    public List<SourceLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(User user, Device device, Instant startTime, Instant endTime, boolean includeIgnored, boolean includeInvalid) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.invalid, rlp.status ")
                .append("FROM raw_source_points rlp ")
                .append("WHERE rlp.user_id = ? AND rlp.device_id IS NOT DISTINCT FROM ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.status = 0 ");
        }
        if (!includeInvalid) {
            sql.append("AND rlp.invalid = false ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp");
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), device != null ? device.id() : null, Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public SourceLocationPoint create(User user, Device device, SourceLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_source_points (user_id, device_id, timestamp, accuracy_meters, elevation_meters, geom, invalid, status) " +
                "VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, '4326'), ?, ?) ON CONFLICT DO NOTHING RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                device != null ? device.id() : null,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isInvalid(),
                rawLocationPoint.getStatus().getDbValue()
        );
        return rawLocationPoint.withId(id);
    }

    public int bulkInsert(User user, Device device, List<LocationPoint> points) {
        if (points.isEmpty()) {
            return -1;
        }
        
        String sql = "INSERT INTO raw_source_points (user_id, device_id, timestamp, accuracy_meters, elevation_meters, geom, invalid, status) " +
                "VALUES (?, ?, ?, ?, ?, CAST(? AS geometry), false, 0) ON CONFLICT DO NOTHING;";

        List<Object[]> batchArgs = new ArrayList<>();
        for (LocationPoint point : points) {
            batchArgs.add(new Object[]{
                    user.getId(),
                    device != null ? device.id() : null,
                    Timestamp.from(point.getTimestamp()),
                    point.getAccuracyMeters(),
                    point.getElevationMeters(),
                    geometryFactory.createPoint(new Coordinate(point.getLongitude(), point.getLatitude())).toString()
            });
        }
        int[] ints = jdbcTemplate.batchUpdate(sql, batchArgs);
        return Arrays.stream(ints).sum();
    }

    public void resetInvalidStatus(User user, Instant startTime, Instant endTime) {
        String sql = "UPDATE raw_source_points SET invalid = false WHERE user_id = ? AND timestamp >= ? AND timestamp < ?";
        jdbcTemplate.update(sql, user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public void bulkUpdateInvalidStatus(List<SourceLocationPoint> points) {
        if (points.isEmpty()) {
            return;
        }

        String sql = "UPDATE raw_source_points SET invalid = true WHERE id = ?";

        List<Object[]> batchArgs = points.stream()
                .map(point -> new Object[]{point.getId()})
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void bulkUpdateIgnoredStatus(User user, List<Long> pointIds) {

        SourceLocationPoint.Status ignoredStatus = SourceLocationPoint.Status.IGNORED_BY_SYSTEM;
        updateBulkStatus(user, pointIds, ignoredStatus);
    }

    public void bulkUpdateManuallyIgnoredStatus(User user, List<Long> pointIds) {
        updateBulkStatus(user, pointIds, SourceLocationPoint.Status.IGNORED_BY_USER);
    }

    public List<DeviceTimeRange> findAffectedTimeRange(User user, List<Long> pointIds) {
        String sql = "SELECT device_id, MIN(timestamp), MAX(timestamp) FROM raw_source_points WHERE user_id = ? AND id = ANY(?) GROUP BY device_id";

        return jdbcTemplate.query(sql, ps -> {
            Long[] idArray = pointIds.toArray(new Long[0]);
            Array sqlArray = ps.getConnection().createArrayOf("bigint", idArray);
            ps.setLong(1, user.getId());
            ps.setArray(2, sqlArray);
        }, ((rs, rowNum) -> new DeviceTimeRange((Long)rs.getObject("device_id"), TimeRange.of(rs.getTimestamp("min").toInstant(), rs.getTimestamp("max").toInstant()))));
    }

    private void updateBulkStatus(User user, List<Long> pointIds, SourceLocationPoint.Status ignoredStatus) {
        if (pointIds.isEmpty()) {
            return;
        }
        String sql = "UPDATE raw_source_points SET status = ? WHERE id = ? AND user_id = ?";

        List<Object[]> batchArgs = pointIds.stream()
                .map(pointId -> new Object[]{ignoredStatus.getDbValue(), pointId, user.getId()})
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }


    public int updateLocation(User user, Long id, double lat, double lng) {
        return this.jdbcTemplate.update("UPDATE raw_source_points SET geom = CAST(? AS geometry) WHERE id = ? AND user_id = ?",
                geometryFactory.createPoint(new Coordinate(lng, lat)).toString()
                , id, user.getId());
    }
}
