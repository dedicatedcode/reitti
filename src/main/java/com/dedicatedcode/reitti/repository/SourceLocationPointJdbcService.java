package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                rs.getBoolean("ignored"),
                rs.getBoolean("invalid")
        );

        this.pointReaderWriter = pointReaderWriter;
        this.geometryFactory = geometryFactory;
    }

    public List<SourceLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(User user, Device device, Instant startTime, Instant endTime, boolean includeIgnored, boolean includeInvalid) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT rlp.id, rlp.accuracy_meters, rlp.elevation_meters, rlp.timestamp, rlp.user_id, ST_AsText(rlp.geom) as geom, rlp.invalid, rlp.ignored ")
                .append("FROM raw_source_points rlp ")
                .append("WHERE rlp.user_id = ? AND rlp.device_id IS NOT DISTINCT FROM ? ");
        if (!includeIgnored) {
            sql.append("AND rlp.ignored = false ");
        }
        if (!includeInvalid) {
            sql.append("AND rlp.invalid = false ");
        }
        sql.append("AND rlp.timestamp >= ? AND rlp.timestamp < ? ORDER BY rlp.timestamp");
        return jdbcTemplate.query(sql.toString(), rawLocationPointRowMapper,
                                  user.getId(), device != null ? device.id() : null, Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public SourceLocationPoint create(User user, Device device, SourceLocationPoint rawLocationPoint) {
        String sql = "INSERT INTO raw_source_points (user_id, device_id, timestamp, accuracy_meters, elevation_meters, geom, invalid, ignored) " +
                "VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, '4326'), ?, ?) ON CONFLICT DO NOTHING RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                device != null ? device.id() : null,
                Timestamp.from(rawLocationPoint.getTimestamp()),
                rawLocationPoint.getAccuracyMeters(),
                rawLocationPoint.getElevationMeters(),
                pointReaderWriter.write(rawLocationPoint.getGeom()),
                rawLocationPoint.isInvalid(),
                rawLocationPoint.isIgnored()
        );
        return rawLocationPoint.withId(id);
    }

    public int bulkInsert(User user, Device device, List<LocationPoint> points) {
        if (points.isEmpty()) {
            return -1;
        }
        
        String sql = "INSERT INTO raw_source_points (user_id, device_id, timestamp, accuracy_meters, elevation_meters, geom, invalid, ignored) " +
                "VALUES (?, ?, ?, ?, ?, CAST(? AS geometry), false, false) ON CONFLICT DO NOTHING;";

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

    public void bulkUpdateIgnoredStatus(List<Long> pointIds, boolean ignored) {
        if (pointIds.isEmpty()) {
            return;
        }

        String sql = "UPDATE raw_source_points SET ignored = ? WHERE id = ?";

        List<Object[]> batchArgs = pointIds.stream()
                .map(pointId -> new Object[]{ignored, pointId})
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

}
