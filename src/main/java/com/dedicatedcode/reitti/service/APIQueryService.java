package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.controller.api.TripDTO;
import com.dedicatedcode.reitti.dto.TripResponseV2;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class APIQueryService {
    private static final Logger logger = LoggerFactory.getLogger(APIQueryService.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final ObjectMapper objectMapper;
    public APIQueryService(NamedParameterJdbcTemplate jdbcTemplate, UserSettingsJdbcService userSettingsJdbcService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.objectMapper = objectMapper;
    }

    public TripResponseV2 getTrips(User user, Instant start, Instant end, double zoom) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", user.getId())
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));

        String sql = """
                WITH trip_bounds AS (
                    SELECT
                        COALESCE(MIN(EXTRACT(EPOCH FROM start_time)), 0)::bigint as min_t,
                        COALESCE(MAX(EXTRACT(EPOCH FROM end_time)), 0)::bigint as max_t
                    FROM trips
                    WHERE user_id = :userId
                      AND ((start_time <= :end AND end_time >= :start) OR
                           (start_time >= :start AND start_time <= :end) OR
                           (end_time >= :start AND end_time <= :end))
                ),
                dominant_modes AS (
                    SELECT trip_id, transportation_mode
                    FROM (
                        SELECT trip_id, transportation_mode,
                               ROW_NUMBER() OVER (PARTITION BY trip_id ORDER BY duration_in_seconds DESC) as rn
                        FROM trip_transport_modes
                    ) x
                    WHERE rn = 1
                ),
                trip_segments AS (
                    SELECT
                        tm.trip_id,
                        json_agg(json_build_object(
                            'offsetSeconds', tm.offset_seconds,
                            'durationSeconds', tm.duration_in_seconds,
                            'mode', tm.transportation_mode,
                            'color', c.color,
                            'icon', c.icon
                        ) ORDER BY tm.offset_seconds) as segments
                    FROM trip_transport_modes tm
                    LEFT JOIN transport_mode_detection_configs c ON
                        c.user_id = :userId AND c.transport_mode = tm.transportation_mode
                    GROUP BY tm.trip_id
                )
                SELECT
                    (SELECT min_t FROM trip_bounds) as min_timestamp,
                    (SELECT max_t FROM trip_bounds) as max_timestamp,
                    COALESCE(
                        json_agg(json_build_object(
                            'id', t.id,
                            'startTime', EXTRACT(EPOCH FROM t.start_time)::bigint,
                            'endTime', EXTRACT(EPOCH FROM t.end_time)::bigint,
                            'mode', dm.transportation_mode,
                            'segments', COALESCE(ts.segments, '[]'::json)
                        ) ORDER BY t.start_time),
                        '[]'::json
                    ) as trips
                FROM trips t
                LEFT JOIN dominant_modes dm ON dm.trip_id = t.id
                LEFT JOIN trip_segments ts ON ts.trip_id = t.id
                WHERE t.user_id = :userId
                  AND ((t.start_time <= :end AND t.end_time >= :start) OR
                       (t.start_time >= :start AND t.start_time <= :end) OR
                       (t.end_time >= :start AND t.end_time <= :end));
                """;

        UserSettings userSettings = this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            return new TripResponseV2(
                    rs.getLong("min_timestamp"),
                    rs.getLong("max_timestamp"),
                    userSettings.getColor(),
                    parseTrips(rs.getString("trips"))
            );
        });
    }

    private List<TripDTO> parseTrips(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // We tell Jackson to parse the string into a List of TripDTO
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            // Log the error and return empty or throw a custom exception
            logger.error("Failed to parse trips JSON from database", e);
            throw new RuntimeException("Data mapping error", e);
        }
    }
}
