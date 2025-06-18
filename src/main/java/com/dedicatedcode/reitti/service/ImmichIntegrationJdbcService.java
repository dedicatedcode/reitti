package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ImmichIntegrationJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ImmichIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    private static final RowMapper<ImmichIntegration> IMMICH_INTEGRATION_ROW_MAPPER = new RowMapper<ImmichIntegration>() {
        @Override
        public ImmichIntegration mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("display_name"),
                rs.getLong("user_version")
            );
            
            return new ImmichIntegration(
                rs.getLong("id"),
                user,
                rs.getString("server_url"),
                rs.getString("api_token"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    };
    
    public Optional<ImmichIntegration> findByUser(User user) {
        String sql = "SELECT ii.id, ii.server_url, ii.api_token, ii.enabled, ii.created_at, ii.updated_at, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM immich_integrations ii " +
                    "JOIN users u ON ii.user_id = u.id " +
                    "WHERE ii.user_id = ?";
        
        try {
            List<ImmichIntegration> results = jdbcTemplate.query(sql, IMMICH_INTEGRATION_ROW_MAPPER, user.getId());
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public ImmichIntegration save(ImmichIntegration immichIntegration) {
        if (immichIntegration.getId() == null) {
            // Insert new record
            String sql = "INSERT INTO immich_integrations (user_id, server_url, api_token, enabled, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
            Instant now = Instant.now();
            Long id = jdbcTemplate.queryForObject(sql, Long.class,
                immichIntegration.getUser().getId(),
                immichIntegration.getServerUrl(),
                immichIntegration.getApiToken(),
                immichIntegration.isEnabled(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
            );
            return new ImmichIntegration(id, immichIntegration.getUser(), immichIntegration.getServerUrl(),
                immichIntegration.getApiToken(), immichIntegration.isEnabled(), now, now);
        } else {
            // Update existing record
            String sql = "UPDATE immich_integrations SET server_url = ?, api_token = ?, enabled = ?, updated_at = ? WHERE id = ?";
            Instant now = Instant.now();
            jdbcTemplate.update(sql,
                immichIntegration.getServerUrl(),
                immichIntegration.getApiToken(),
                immichIntegration.isEnabled(),
                java.sql.Timestamp.from(now),
                immichIntegration.getId()
            );
            return new ImmichIntegration(immichIntegration.getId(), immichIntegration.getUser(),
                immichIntegration.getServerUrl(), immichIntegration.getApiToken(), immichIntegration.isEnabled(),
                immichIntegration.getCreatedAt(), now);
        }
    }
    
    public Optional<ImmichIntegration> findById(Long id) {
        String sql = "SELECT ii.id, ii.server_url, ii.api_token, ii.enabled, ii.created_at, ii.updated_at, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM immich_integrations ii " +
                    "JOIN users u ON ii.user_id = u.id " +
                    "WHERE ii.id = ?";
        
        try {
            List<ImmichIntegration> results = jdbcTemplate.query(sql, IMMICH_INTEGRATION_ROW_MAPPER, id);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public void deleteById(Long id) {
        String sql = "DELETE FROM immich_integrations WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No ImmichIntegration found with id: " + id, 1);
        }
    }
    
    public List<ImmichIntegration> findAll() {
        String sql = "SELECT ii.id, ii.server_url, ii.api_token, ii.enabled, ii.created_at, ii.updated_at, " +
                    "u.id as user_id, u.username, u.password, u.display_name, u.version as user_version " +
                    "FROM immich_integrations ii " +
                    "JOIN users u ON ii.user_id = u.id";
        return jdbcTemplate.query(sql, IMMICH_INTEGRATION_ROW_MAPPER);
    }
}
