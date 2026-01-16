package com.dedicatedcode.reitti.service;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class StreamingRawLocationPointJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public StreamingRawLocationPointJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void streamPoints(Long userId, Instant start, Instant end, ResponseBodyEmitter emitter) {
        String sql = """
            SELECT
                ST_X(geom) as lng,
                ST_Y(geom) as lat,
                EXTRACT(EPOCH FROM timestamp) as ts
            FROM raw_location_points
            WHERE user_id = ?
              AND timestamp >= ? AND timestamp < ?
            ORDER BY timestamp
        """;

        jdbcTemplate.query(sql, ps -> {
            // Important for streaming
            ps.setLong(1, userId);
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            ps.setFetchSize(1000);
        }, rs -> {
            try {
                Map<String, Object> point = new HashMap<>();
                point.put("lng", rs.getDouble("lng"));
                point.put("lat", rs.getDouble("lat"));
                point.put("ts", rs.getLong("ts"));
                emitter.send(point, MediaType.APPLICATION_JSON);
                emitter.send("\n");
            } catch (IOException e) {
                throw new SQLException("Emitter closed", e);
            }
        });
    }
}
