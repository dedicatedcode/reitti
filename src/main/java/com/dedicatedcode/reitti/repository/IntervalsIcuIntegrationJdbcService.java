package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class IntervalsIcuIntegrationJdbcService {

    private static final String SELECT_COLUMNS =
            "id, api_key, reitti_device_id, athlete_id, athlete_name, enabled, last_successful_fetch, version";

    private final JdbcTemplate jdbcTemplate;

    public IntervalsIcuIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<IntervalsIcuIntegration> rowMapper = (rs, _) -> {
        Timestamp lastSuccessfulFetch = rs.getTimestamp("last_successful_fetch");
        return new IntervalsIcuIntegration(
                rs.getLong("id"),
                rs.getString("api_key"),
                rs.getLong("reitti_device_id"),
                rs.getString("athlete_id"),
                rs.getString("athlete_name"),
                rs.getBoolean("enabled"),
                lastSuccessfulFetch != null ? lastSuccessfulFetch.toInstant() : null,
                rs.getLong("version"));
    };

    public Optional<IntervalsIcuIntegration> findByUser(User user) {
        try {
            String sql = "SELECT " + SELECT_COLUMNS + " FROM intervals_icu_integrations WHERE user_id = ?";
            IntervalsIcuIntegration integration = jdbcTemplate.queryForObject(sql, rowMapper, user.getId());
            return Optional.ofNullable(integration);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public IntervalsIcuIntegration save(User user, IntervalsIcuIntegration integration) {
        String sql = "INSERT INTO intervals_icu_integrations (api_key, reitti_device_id, athlete_id, athlete_name, enabled, last_successful_fetch, user_id, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, integration.getApiKey());
            ps.setLong(2, integration.getReittiDeviceId());
            ps.setString(3, integration.getAthleteId());
            ps.setString(4, integration.getAthleteName());
            ps.setBoolean(5, integration.isEnabled());
            ps.setTimestamp(6, integration.getLastSuccessfulFetch() != null
                    ? Timestamp.from(integration.getLastSuccessfulFetch()) : null);
            ps.setLong(7, user.getId());
            ps.setLong(8, 1L);
            return ps;
        }, keyHolder);

        Long id = (Long) keyHolder.getKeys().get("id");
        return integration.withId(id).withVersion(1L);
    }

    public IntervalsIcuIntegration update(User user, IntervalsIcuIntegration integration) {
        String sql = "UPDATE intervals_icu_integrations SET api_key = ?, reitti_device_id = ?, athlete_id = ?, athlete_name = ?, enabled = ?, last_successful_fetch = ?, updated_at = now(), version = version + 1 WHERE id = ? AND user_id = ? AND version = ?";

        int rowsAffected = jdbcTemplate.update(sql,
                integration.getApiKey(),
                integration.getReittiDeviceId(),
                integration.getAthleteId(),
                integration.getAthleteName(),
                integration.isEnabled(),
                integration.getLastSuccessfulFetch() != null ? Timestamp.from(integration.getLastSuccessfulFetch()) : null,
                integration.getId(),
                user.getId(),
                integration.getVersion());

        if (rowsAffected == 0) {
            throw new OptimisticLockException("Optimistic locking failure or record not found");
        }

        return integration.withVersion(integration.getVersion() + 1);
    }
}
