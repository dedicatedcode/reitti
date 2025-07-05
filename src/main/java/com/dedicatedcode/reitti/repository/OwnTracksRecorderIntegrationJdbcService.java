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
    private final UserJdbcService userJdbcService;

    public OwnTracksRecorderIntegrationJdbcService(JdbcTemplate jdbcTemplate, UserJdbcService userJdbcService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJdbcService = userJdbcService;
    }

    private final RowMapper<OwnTracksRecorderIntegration> rowMapper = (rs, rowNum) -> {
        Long userId = rs.getLong("user_id");
        User user = userJdbcService.findById(userId).orElse(null);
        
        return new OwnTracksRecorderIntegration(
                rs.getLong("id"),
                rs.getString("base_url"),
                rs.getString("username"),
                rs.getString("device_id"),
                rs.getBoolean("enabled"),
                user,
                rs.getLong("version")
        );
    };

    public Optional<OwnTracksRecorderIntegration> findByUser(User user) {
        try {
            String sql = "SELECT id, base_url, username, device_id, enabled, user_id, version FROM owntracks_recorder_integration WHERE user_id = ?";
            OwnTracksRecorderIntegration integration = jdbcTemplate.queryForObject(sql, rowMapper, user.getId());
            return Optional.of(integration);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public OwnTracksRecorderIntegration save(OwnTracksRecorderIntegration integration) {
        if (integration.getId() == null) {
            return insert(integration);
        } else {
            return update(integration);
        }
    }

    private OwnTracksRecorderIntegration insert(OwnTracksRecorderIntegration integration) {
        String sql = "INSERT INTO owntracks_recorder_integration (base_url, username, device_id, enabled, user_id, version) VALUES (?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, integration.getBaseUrl());
            ps.setString(2, integration.getUsername());
            ps.setString(3, integration.getDeviceId());
            ps.setBoolean(4, integration.isEnabled());
            ps.setLong(5, integration.getUser().getId());
            ps.setLong(6, 1L); // Initial version
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return integration.withId(id).withVersion(1L);
    }

    private OwnTracksRecorderIntegration update(OwnTracksRecorderIntegration integration) {
        String sql = "UPDATE owntracks_recorder_integration SET base_url = ?, username = ?, device_id = ?, enabled = ?, version = version + 1 WHERE id = ? AND version = ?";
        
        int rowsAffected = jdbcTemplate.update(sql,
                integration.getBaseUrl(),
                integration.getUsername(),
                integration.getDeviceId(),
                integration.isEnabled(),
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
