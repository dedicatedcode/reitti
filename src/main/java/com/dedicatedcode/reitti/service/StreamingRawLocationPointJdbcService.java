package com.dedicatedcode.reitti.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
public class StreamingRawLocationPointJdbcService {
    private static final Logger log = LoggerFactory.getLogger(StreamingRawLocationPointJdbcService.class);

    private final JdbcTemplate jdbcTemplate;
    private final GeoLocationTimezoneService timezoneService;

    public StreamingRawLocationPointJdbcService(JdbcTemplate jdbcTemplate, GeoLocationTimezoneService timezoneService) {
        this.jdbcTemplate = jdbcTemplate;
        this.timezoneService = timezoneService;
    }

    @Transactional(readOnly = true)
    public void streamPoints(Long userId, Instant start, Instant end, ResponseBodyEmitter emitter) {
        String sql = """
            SELECT
                ST_X(geom) as lng,
                ST_Y(geom) as lat,
                elevation_meters as alt,
                EXTRACT(EPOCH FROM timestamp) as ts
            FROM raw_location_points
            WHERE user_id = ?
              AND timestamp >= ? AND timestamp < ?
              AND invalid = false
            ORDER BY timestamp
        """;
        int pointsPerBatch = 8192;
        ByteBuffer buffer = ByteBuffer.allocate(pointsPerBatch * 20);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // Essential for JS Float32Array

        Map<String, ZoneId> timezoneCache = new HashMap<>();
        jdbcTemplate.query(sql, ps -> {
            // Important for streaming
            ps.setLong(1, userId);
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            ps.setFetchSize(pointsPerBatch);
        }, rs -> {
            float lat = rs.getFloat("lat");
            float lng = rs.getFloat("lng");
            float ts = rs.getFloat("ts");
            int latRounded = Math.round((float) (lat * 10.0));
            int lngRounded = Math.round((float) (lng * 10.0));
            String cacheKey = latRounded + "," + lngRounded;
            ZoneId zoneId = timezoneCache.computeIfAbsent(cacheKey, _ ->
                    timezoneService.getTimezone(latRounded / 10.0, lngRounded / 10.0).orElse(null));
            int timezoneOffsetSeconds = (zoneId != null) ? zoneId.getRules().getOffset(Instant.ofEpochSecond((long) ts, 0)).getTotalSeconds() : 0;

            buffer.putFloat(lat);
            buffer.putFloat(lng);
            buffer.putFloat(rs.getFloat("alt"));
            buffer.putFloat(ts);
            buffer.putFloat(timezoneOffsetSeconds);
            if (buffer.remaining() < 20) {
                try {
                    // Send the full raw byte array
                    emitter.send(buffer.array(), MediaType.APPLICATION_OCTET_STREAM);
                    buffer.clear();
                } catch (IOException e) {
                    throw new SQLException("Client disconnected");
                }
            }
        });

        try {
            if (buffer.position() > 0) {
                byte[] remainder = new byte[buffer.position()];
                buffer.flip();
                buffer.get(remainder);
                emitter.send(remainder, MediaType.APPLICATION_OCTET_STREAM);
            }
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to complete streaming: {}", e.getMessage());
        }
    }
}
