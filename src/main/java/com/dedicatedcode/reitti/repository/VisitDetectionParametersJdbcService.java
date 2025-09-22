package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.processing.DetectionParameter;
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
public class VisitDetectionParametersJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public VisitDetectionParametersJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<DetectionParameter> CONFIGURATION_ROW_MAPPER = (rs, _) -> {
        Long id = rs.getLong("id");
        boolean needsRecalculation = rs.getBoolean("needs_recalculation");
        Timestamp validSinceTimestamp = rs.getTimestamp("valid_since");
        Instant validSince = validSinceTimestamp != null ? validSinceTimestamp.toInstant() : null;

        DetectionParameter.VisitDetection visitDetection = new DetectionParameter.VisitDetection(
                rs.getLong("detection_search_distance_meters"),
                rs.getInt("detection_minimum_adjacent_points"),
                rs.getLong("detection_minimum_stay_time_seconds"),
                rs.getLong("detection_max_merge_time_between_same_stay_points")
        );

        DetectionParameter.VisitMerging visitMerging = new DetectionParameter.VisitMerging(
                rs.getLong("merging_search_duration_in_hours"),
                rs.getLong("merging_max_merge_time_between_same_visits"),
                rs.getLong("merging_min_distance_between_visits")
        );

        return new DetectionParameter(id, visitDetection, visitMerging, validSince, needsRecalculation);
    };


    @Transactional(readOnly = true)
    @Cacheable(value = "configurations", key = "#user.id")
    public List<DetectionParameter> findAllConfigurationsForUser(User user) {
        String sql = """
            SELECT * FROM visit_detection_parameters
            WHERE user_id = ?
            ORDER BY valid_since DESC NULLS LAST
            """;
        
        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "configurations", key = "#user.id + '_' + #id")
    public Optional<DetectionParameter> findById(Long id, User user) {
        String sql = """
            SELECT * FROM visit_detection_parameters
            WHERE id = ? AND user_id = ?
            """;
        
        List<DetectionParameter> results = jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, id, user.getId());
        return results.stream().findFirst();
    }

    @CacheEvict(value = "configurations", key = "#user.id")
    public void saveConfiguration(User user, DetectionParameter detectionParameter) {
        String sql = """
            INSERT INTO visit_detection_parameters (
                user_id, valid_since, detection_search_distance_meters,
                detection_minimum_adjacent_points, detection_minimum_stay_time_seconds, 
                detection_max_merge_time_between_same_stay_points, merging_search_duration_in_hours, 
                            merging_max_merge_time_between_same_visits, merging_min_distance_between_visits, needs_recalculation
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        Timestamp validSinceTimestamp = detectionParameter.getValidSince() != null ?
            Timestamp.from(detectionParameter.getValidSince()) : null;
        
        jdbcTemplate.update(sql,
            user.getId(),
            validSinceTimestamp,
            detectionParameter.getVisitDetection().getSearchDistanceInMeters(),
            detectionParameter.getVisitDetection().getMinimumAdjacentPoints(),
            detectionParameter.getVisitDetection().getMinimumStayTimeInSeconds(),
            detectionParameter.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
            detectionParameter.getVisitMerging().getSearchDurationInHours(),
            detectionParameter.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
            detectionParameter.getVisitMerging().getMinDistanceBetweenVisits(),
            detectionParameter.needsRecalculation()
        );
    }

    @CacheEvict(value = "configurations", allEntries = true)
    public void updateConfiguration(DetectionParameter detectionParameter) {
        String sql = """
            UPDATE visit_detection_parameters SET
                valid_since = ?,
                detection_search_distance_meters = ?,
                detection_minimum_adjacent_points = ?,
                detection_minimum_stay_time_seconds = ?,
                detection_max_merge_time_between_same_stay_points = ?,
                merging_search_duration_in_hours = ?,
                merging_max_merge_time_between_same_visits = ?,
                merging_min_distance_between_visits = ?,
                needs_recalculation = ?
            WHERE id = ?
            """;
        
        Timestamp validSinceTimestamp = detectionParameter.getValidSince() != null ?
            Timestamp.from(detectionParameter.getValidSince()) : null;
        
        jdbcTemplate.update(sql,
            validSinceTimestamp,
            detectionParameter.getVisitDetection().getSearchDistanceInMeters(),
            detectionParameter.getVisitDetection().getMinimumAdjacentPoints(),
            detectionParameter.getVisitDetection().getMinimumStayTimeInSeconds(),
            detectionParameter.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
            detectionParameter.getVisitMerging().getSearchDurationInHours(),
            detectionParameter.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
            detectionParameter.getVisitMerging().getMinDistanceBetweenVisits(),
            detectionParameter.needsRecalculation(),
            detectionParameter.getId()
        );
    }

    @CacheEvict(value = "configurations", allEntries = true)
    public void delete(Long configurationId) {
        String sql = """
            DELETE FROM visit_detection_parameters
            WHERE id = ? AND valid_since IS NOT NULL
            """;
        
        jdbcTemplate.update(sql, configurationId);
    }

    public DetectionParameter findCurrent(User user, Instant instant) {
        String sql = """
            SELECT * FROM visit_detection_parameters
            WHERE user_id = ? AND (valid_since <= ? OR valid_since IS NULL)
            ORDER BY valid_since DESC NULLS LAST
            """;

        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId(), Timestamp.from(instant)).getFirst();
    }
}
