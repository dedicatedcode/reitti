package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class OwnTracksRecorderIntegrationJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public OwnTracksRecorderIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<OwnTracksRecorderIntegration> rowMapper = (rs, rowNum) -> {
        java.sql.Timestamp timestamp = rs.getTimestamp("last_successful_fetch");
        java.time.Instant lastSuccessfulFetch = timestamp != null ? timestamp.toInstant() : null;

        return new OwnTracksRecorderIntegration(
                rs.getLong("id"),
                rs.getString("base_url"),
                rs.getString("username"),
                rs.getString("device_id"),
                rs.getBoolean("enabled"),
                lastSuccessfulFetch,
                rs.getLong("version")
        );
    };

    public Optional<OwnTracksRecorderIntegration> findByUser(User user) {
        try {
            String sql = "SELECT id, base_url, username, device_id, enabled, last_successful_fetch, user_id, version FROM owntracks_recorder_integration WHERE user_id = ?";
            OwnTracksRecorderIntegration integration = jdbcTemplate.queryForObject(sql, rowMapper, user.getId());
            return Optional.ofNullable(integration);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public OwnTracksRecorderIntegration save(User user, OwnTracksRecorderIntegration integration) {
        String sql = "INSERT INTO owntracks_recorder_integration (base_url, username, device_id, enabled, last_successful_fetch, user_id, version) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, integration.getBaseUrl());
            ps.setString(2, integration.getUsername());
            ps.setString(3, integration.getDeviceId());
            ps.setBoolean(4, integration.isEnabled());
            if (integration.getLastSuccessfulFetch() != null) {
                ps.setTimestamp(5, java.sql.Timestamp.from(integration.getLastSuccessfulFetch()));
            } else {
                ps.setTimestamp(5, null);
            }
            ps.setLong(6, user.getId());
            ps.setLong(7, 1L); // Initial version
            return ps;
        }, keyHolder);

        Long id = (Long) keyHolder.getKeys().get("id");
        return integration.withId(id).withVersion(1L);
    }

    public OwnTracksRecorderIntegration update(OwnTracksRecorderIntegration integration) {
        String sql = "UPDATE owntracks_recorder_integration SET base_url = ?, username = ?, device_id = ?, enabled = ?, last_successful_fetch = ?, version = version + 1 WHERE id = ? AND version = ?";
        
        int rowsAffected = jdbcTemplate.update(sql,
                integration.getBaseUrl(),
                integration.getUsername(),
                integration.getDeviceId(),
                integration.isEnabled(),
                integration.getLastSuccessfulFetch() != null ? java.sql.Timestamp.from(integration.getLastSuccessfulFetch()) : null,
                integration.getId(),
                integration.getVersion());

        if (rowsAffected == 0) {
            throw new RuntimeException("Optimistic locking failure or record not found");
        }

        return integration.withVersion(integration.getVersion() + 1);
    }

    public void delete(OwnTracksRecorderIntegration integration) {
        String sql = "DELETE FROM owntracks_recorder_integration WHERE id = ?";
        jdbcTemplate.update(sql, integration.getId());
    }
}
