package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ConfigurationJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public ConfigurationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Configuration> CONFIGURATION_ROW_MAPPER = new RowMapper<Configuration>() {
        @Override
        public Configuration mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long id = rs.getLong("id");
            Timestamp validSinceTimestamp = rs.getTimestamp("valid_since");
            Instant validSince = validSinceTimestamp != null ? validSinceTimestamp.toInstant() : null;
            
            Configuration.VisitDetection visitDetection = new Configuration.VisitDetection(
                rs.getLong("detection_search_distance_meters"),
                rs.getLong("detection_minimum_adjacent_points"),
                rs.getLong("detection_minimum_stay_time_seconds"),
                rs.getLong("detection_max_merge_time_between_same_stay_points")
            );
            
            Configuration.VisitMerging visitMerging = new Configuration.VisitMerging(
                rs.getLong("merging_search_duration_in_hours"),
                rs.getLong("merging_max_merge_time_between_same_visits"),
                rs.getLong("merging_min_distance_between_visits")
            );
            
            return new Configuration(id, visitDetection, visitMerging, validSince);
        }
    };


    @Transactional(readOnly = true)
    @Cacheable(value = "configurations", key = "#user.id")
    public List<Configuration> findAllConfigurationsForUser(User user) {
        String sql = """
            SELECT * FROM visit_detection_parameters
            WHERE user_id = ?
            ORDER BY valid_since DESC NULLS LAST
            """;
        
        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId());
    }

    @CacheEvict(value = "configurations", key = "#user.id")
    public void saveConfiguration(User user, Configuration configuration) {
        String sql = """
            INSERT INTO visit_detection_parameters (
                user_id, valid_since, detection_search_distance_meters,
                detection_minimum_adjacent_points, detection_minimum_stay_time_seconds, 
                detection_max_merge_time_between_same_stay_points, merging_search_duration_in_hours, 
                merging_max_merge_time_between_same_visits, merging_min_distance_between_visits
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        Timestamp validSinceTimestamp = configuration.getValidSince() != null ?
            Timestamp.from(configuration.getValidSince()) : null;
        
        jdbcTemplate.update(sql,
            user.getId(),
            validSinceTimestamp,
            configuration.getVisitDetection().getSearchDistanceInMeters(),
            configuration.getVisitDetection().getMinimumAdjacentPoints(),
            configuration.getVisitDetection().getMinimumStayTimeInSeconds(),
            configuration.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
            configuration.getVisitMerging().getSearchDurationInHours(),
            configuration.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
            configuration.getVisitMerging().getMinDistanceBetweenVisits()
        );
    }

    @CacheEvict(value = "configurations", allEntries = true)
    public void updateConfiguration(Configuration configuration) {
        String sql = """
            UPDATE visit_detection_parameters SET
                valid_since = ?,
                detection_search_distance_meters = ?,
                detection_minimum_adjacent_points = ?,
                detection_minimum_stay_time_seconds = ?,
                detection_max_merge_time_between_same_stay_points = ?,
                merging_search_duration_in_hours = ?,
                merging_max_merge_time_between_same_visits = ?,
                merging_min_distance_between_visits = ?
            WHERE id = ?
            """;
        
        Timestamp validSinceTimestamp = configuration.getValidSince() != null ? 
            Timestamp.from(configuration.getValidSince()) : null;
        
        jdbcTemplate.update(sql,
            validSinceTimestamp,
            configuration.getVisitDetection().getSearchDistanceInMeters(),
            configuration.getVisitDetection().getMinimumAdjacentPoints(),
            configuration.getVisitDetection().getMinimumStayTimeInSeconds(),
            configuration.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints(),
            configuration.getVisitMerging().getSearchDurationInHours(),
            configuration.getVisitMerging().getMaxMergeTimeBetweenSameVisits(),
            configuration.getVisitMerging().getMinDistanceBetweenVisits(),
            configuration.getId()
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
}
