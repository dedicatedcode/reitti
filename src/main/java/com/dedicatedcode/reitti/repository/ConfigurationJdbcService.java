package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.processing.Configuration;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
            
            return new Configuration(visitDetection, visitMerging, validSince);
        }
    };

    @Transactional(readOnly = true)
    public Optional<Configuration> findCurrentConfigurationForUser(User user) {
        String sql = """
            SELECT * FROM visit_detection_parameters 
            WHERE user_id = ? AND (valid_since IS NULL OR valid_since <= NOW()) 
            ORDER BY valid_since DESC NULLS LAST 
            LIMIT 1
            """;
        
        List<Configuration> results = jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Transactional(readOnly = true)
    public List<Configuration> findAllConfigurationsForUser(User user) {
        String sql = """
            SELECT * FROM visit_detection_parameters 
            WHERE user_id = ? 
            ORDER BY valid_since DESC NULLS LAST
            """;
        
        return jdbcTemplate.query(sql, CONFIGURATION_ROW_MAPPER, user.getId());
    }

    public void saveConfiguration(User user, Configuration configuration) {
        String sql = """
            INSERT INTO visit_detection_parameters (
                user_id, valid_since, detection_search_distance_meters, 
                detection_minimum_adjacent_points, detection_minimum_stay_time_seconds, 
                detection_max_merge_time_between_same_stay_points, merging_search_duration_in_hours, 
                merging_max_merge_time_between_same_visits, merging_min_distance_between_visits
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        Timestamp validSinceTimestamp = configuration.validSince() != null ? 
            Timestamp.from(configuration.validSince()) : null;
        
        jdbcTemplate.update(sql,
            user.getId(),
            validSinceTimestamp,
            configuration.visitDetection().searchDistanceInMeters(),
            configuration.visitDetection().minimumAdjacentPoints(),
            configuration.visitDetection().minimumStayTimeInSeconds(),
            configuration.visitDetection().maxMergeTimeBetweenSameStayPoints(),
            configuration.visitMerging().searchDurationInHours(),
            configuration.visitMerging().maxMergeTimeBetweenSameVisits(),
            configuration.visitMerging().minDistanceBetweenVisits()
        );
    }

    public void updateConfiguration(User user, Configuration oldConfiguration, Configuration newConfiguration) {
        // Delete the old configuration
        String deleteSql = """
            DELETE FROM visit_detection_parameters 
            WHERE user_id = ? AND valid_since = ?
            """;
        
        Timestamp oldValidSinceTimestamp = oldConfiguration.validSince() != null ? 
            Timestamp.from(oldConfiguration.validSince()) : null;
        
        jdbcTemplate.update(deleteSql, user.getId(), oldValidSinceTimestamp);
        
        // Insert the new configuration
        saveConfiguration(user, newConfiguration);
    }

    public void deleteConfiguration(User user, Configuration configuration) {
        String sql = """
            DELETE FROM visit_detection_parameters 
            WHERE user_id = ? AND valid_since = ?
            """;
        
        Timestamp validSinceTimestamp = configuration.validSince() != null ? 
            Timestamp.from(configuration.validSince()) : null;
        
        jdbcTemplate.update(sql, user.getId(), validSinceTimestamp);
    }
}
