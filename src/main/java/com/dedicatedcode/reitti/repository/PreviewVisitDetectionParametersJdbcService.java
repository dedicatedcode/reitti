package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PreviewVisitDetectionParametersJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public PreviewVisitDetectionParametersJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Configuration> CONFIGURATION_ROW_MAPPER = (rs, _) -> {
        Long id = rs.getLong("id");
        Timestamp validSinceTimestamp = rs.getTimestamp("valid_since");
        Instant validSince = validSinceTimestamp != null ? validSinceTimestamp.toInstant() : null;

        Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                rs.getLong("detection_search_distance_meters"),
                rs.getInt("detection_minimum_adjacent_points"),
                rs.getLong("detection_minimum_stay_time_seconds"),
                rs.getLong("detection_max_merge_time_between_same_stay_points")
        );

        Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                rs.getLong("merging_search_duration_in_hours"),
                rs.getLong("merging_max_merge_time_between_same_visits"),
                rs.getLong("merging_min_distance_between_visits")
        );

        return new Configuration(id, visitDetection, visitMerging, validSince);
    };


    @Transactional(readOnly = true)
    public List<Configuration> findAllConfigurationsForUser(User user, String previewId) {
        String sql = """
            SELECT * FROM preview_visit_detection_parameters
            WHERE user_id = ? and preview_id = ?
            ORDER BY valid_since DESC NULLS LAST
            """;
        
        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId(), previewId);
    }

    public Configuration findCurrent(User user, String previewId) {
        String sql = """
            SELECT * FROM preview_visit_detection_parameters
            WHERE user_id = ? and preview_id = ?
            ORDER BY valid_since DESC NULLS LAST
            """;

        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId(), previewId).getFirst();
    }
}
