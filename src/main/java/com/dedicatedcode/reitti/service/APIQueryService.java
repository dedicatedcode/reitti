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
        // Calculate tolerance in degrees (4326)
        // Zoom 18 (Street) -> ~0.00001 (1m)
        // Zoom 2 (World) -> ~0.5 (50km)
        double tolerance = Math.pow(2, 12 - zoom) * 0.000001;

        // Ensure tolerance doesn't go too low (keep it simple) or too high
        tolerance = Math.max(0, Math.min(tolerance, 0.5));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", user.getId())
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end))
                .addValue("tolerance", tolerance);

        String sql = """
                
                WITH trip_bounds AS (
                                                        SELECT
                                                            MIN(EXTRACT(EPOCH FROM start_time)) as min_t,
                                                            MAX(EXTRACT(EPOCH FROM end_time)) as max_t
                                                        FROM trips
                                                        WHERE user_id = :userId
                                                          AND start_time >= :start
                                                          AND end_time <= :end
                                                    ),
                                                    points_in_trips AS (
                                                        -- Direct join using the geom column
                                                        SELECT
                                                            t.id as trip_id,
                                                            t.transport_mode_inferred,
                                                            p.geom,
                                                            p.timestamp
                                                        FROM trips t
                                                        INNER JOIN raw_location_points p ON
                                                            p.timestamp BETWEEN t.start_time AND t.end_time
                                                        WHERE t.user_id = :userId
                                                          AND t.start_time >= :start
                                                          AND t.end_time <= :end
                                                    ),
                                                    simplified_paths AS (
                                                        SELECT
                                                            trip_id,
                                                            transport_mode_inferred,
                                                            ST_SimplifyPreserveTopology(
                                                                ST_MakeLine(geom ORDER BY timestamp),
                                                                :tolerance
                                                            ) as line_geom
                                                        FROM points_in_trips
                                                        GROUP BY trip_id, transport_mode_inferred
                                                    ),
                                                    final_agg AS (
                                                        SELECT
                                                            sp.trip_id,
                                                            sp.transport_mode_inferred,
                                                            -- ST_X and ST_Y extract lng/lat from the simplified point
                                                            array_agg(ARRAY[ST_X(pts.geom), ST_Y(pts.geom)]) as coords,
                                                            array_agg(EXTRACT(EPOCH FROM p.timestamp) - b.min_t ORDER BY p.timestamp) as offsets
                                                        FROM simplified_paths sp
                                                        CROSS JOIN ST_DumpPoints(sp.line_geom) as pts
                                                        JOIN raw_location_points p ON
                                                            p.timestamp BETWEEN :start AND :end
                                                            AND ST_DWithin(p.geom, pts.geom, 0.00001)
                                                        CROSS JOIN trip_bounds b
                                                        GROUP BY sp.trip_id, sp.transport_mode_inferred, b.min_t
                                                    )
                                                    SELECT
                                                        (SELECT min_t FROM trip_bounds) as min_timestamp,
                                                        (SELECT max_t FROM trip_bounds) as max_timestamp,
                                                        json_agg(json_build_object(
                                                            'id', trip_id,
                                                            'mode', transport_mode_inferred,
                                                            'path', coords,
                                                            'timestamps', offsets
                                                        )) as trips
                                                    FROM final_agg;
                """;

        UserSettings userSettings = this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            // Mapping to your TripResponseV2 DTO
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
