package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final RowMapper<ImmichIntegration> IMMICH_INTEGRATION_ROW_MAPPER = (rs, rowNum) -> new ImmichIntegration(
            rs.getLong("id"),
            rs.getString("server_url"),
            rs.getString("api_token"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    public Optional<ImmichIntegration> findByUser(User user) {
        String sql = "SELECT ii.* " +
                "FROM immich_integrations ii " +
                "WHERE ii.user_id = ?";

        try {
            List<ImmichIntegration> results = jdbcTemplate.query(sql, IMMICH_INTEGRATION_ROW_MAPPER, user.getId());
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public ImmichIntegration save(User user, ImmichIntegration immichIntegration) {
        if (immichIntegration.getId() == null) {
            // Insert new record
            String sql = "INSERT INTO immich_integrations (user_id, server_url, api_token, enabled, created_at, updated_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
            Instant now = Instant.now();
            Long id = jdbcTemplate.queryForObject(sql, Long.class,
                    user.getId(),
                    immichIntegration.getServerUrl(),
                    immichIntegration.getApiToken(),
                    immichIntegration.isEnabled(),
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now),
                    immichIntegration.getVersion()
            );
            return immichIntegration.withId(id);
        } else {
            // Update existing record
            String sql = "UPDATE immich_integrations SET server_url = ?, api_token = ?, enabled = ?, updated_at = ?, version = version + 1 WHERE id = ? AND version = ?";
            Instant now = Instant.now();
            jdbcTemplate.update(sql,
                    immichIntegration.getServerUrl(),
                    immichIntegration.getApiToken(),
                    immichIntegration.isEnabled(),
                    java.sql.Timestamp.from(now),
                    immichIntegration.getId(),
                    immichIntegration.getVersion()
            );
            return findById(immichIntegration.getId()).orElseThrow();
        }
    }

    public Optional<ImmichIntegration> findById(Long id) {
        String sql = "SELECT ii.* " +
                "FROM immich_integrations ii " +
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
        String sql = "SELECT ii.*" +
                "FROM immich_integrations ii ";
        return jdbcTemplate.query(sql, IMMICH_INTEGRATION_ROW_MAPPER);
    }
}
