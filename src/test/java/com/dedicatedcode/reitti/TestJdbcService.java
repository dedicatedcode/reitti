package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class TestJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public TestJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findPointsForVisit(User user, ProcessedVisit visit) {
        return this.jdbcTemplate.queryForList("SELECT raw_location_points.source_point_id FROM raw_location_points WHERE user_id = ? AND timestamp >= ? AND timestamp < ? AND source_point_id IS NOT NULL;", Long.class,
                                       user.getId(),
                                       Timestamp.from(visit.getStartTime()),
                                       Timestamp.from(visit.getEndTime()));
    }

    public List<Long> findSourcePointsAfter(User user, String time) {
        Instant timestamp = Instant.parse(time);
        return this.jdbcTemplate.queryForList("""
                                                      SELECT raw_location_points.source_point_id FROM raw_location_points
                                                      WHERE user_id = ? AND timestamp >= ? AND source_point_id IS NOT NULL;
                                                      """, Long.class,
                                              user.getId(),
                                              Timestamp.from(timestamp));
    }

    public List<Long> findSourcePointIdsForDevice(User user, Device device, Instant start, Instant end) {
        return this.jdbcTemplate.queryForList("""
                                                      SELECT rlp.source_point_id
                                                      FROM raw_location_points rlp
                                                      JOIN raw_source_points rsp ON rsp.id = rlp.source_point_id
                                                      WHERE rlp.user_id = ? AND rsp.device_id = ?
                                                        AND rlp.timestamp >= ? AND rlp.timestamp < ?;
                                                      """, Long.class,
                                              user.getId(),
                                              device.id(),
                                              Timestamp.from(start),
                                              Timestamp.from(end));
    }
}
