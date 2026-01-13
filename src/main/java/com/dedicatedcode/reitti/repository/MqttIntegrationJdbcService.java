package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MqttIntegrationJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public MqttIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MqttIntegration> findByUser(User user) {
        String sql = """
            SELECT id, user_id, host, port, use_tls, identifier, topic, username, password,
                   payload_type, enabled, created_at, updated_at, last_used, version
            FROM mqtt_integrations
            WHERE user_id = ?
            """;
        
        List<MqttIntegration> results = jdbcTemplate.query(sql, new MqttIntegrationRowMapper(), user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public MqttIntegration save(User user, MqttIntegration integration) {
        if (integration.getId() == null) {
            return create(user, integration);
        } else {
            return update(integration);
        }
    }

    private MqttIntegration create(User user, MqttIntegration integration) {
        String sql = """
            INSERT INTO mqtt_integrations (user_id, host, port, use_tls, identifier, topic, username, password,
                                         payload_type, enabled, created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Instant now = Instant.now();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, user.getId());
            ps.setString(2, integration.getHost());
            ps.setInt(3, integration.getPort());
            ps.setBoolean(4, integration.isUseTLS());
            ps.setString(5, integration.getIdentifier());
            ps.setString(6, integration.getTopic());
            ps.setString(7, integration.getUsername());
            ps.setString(8, integration.getPassword());
            ps.setString(9, integration.getPayloadType().name());
            ps.setBoolean(10, integration.isEnabled());
            ps.setTimestamp(11, Timestamp.from(now));
            ps.setLong(12, 1L);
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return new MqttIntegration(
                id,
                integration.getHost(),
                integration.getPort(),
                integration.isUseTLS(),
                integration.getIdentifier(),
                integration.getTopic(),
                integration.getUsername(),
                integration.getPassword(),
                integration.getPayloadType(),
                integration.isEnabled(),
                now,
                null,
                null,
                1L
        );
    }

    private MqttIntegration update(MqttIntegration integration) {
        String sql = """
            UPDATE mqtt_integrations
            SET host = ?, port = ?, use_tls = ?, identifier = ?, topic = ?, username = ?, password = ?,
                payload_type = ?, enabled = ?, updated_at = ?, version = version + 1
            WHERE id = ? AND version = ?
            """;
        
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(sql,
            integration.getHost(),
            integration.getPort(),
            integration.isUseTLS(),
            integration.getIdentifier(),
            integration.getTopic(),
            integration.getUsername(),
            integration.getPassword(),
            integration.getPayloadType().name(),
            integration.isEnabled(),
            Timestamp.from(now),
            integration.getId(),
            integration.getVersion()
        );
        
        if (updated == 0) {
            throw new OptimisticLockException("MQTT integration was modified by another process");
        }
        
        return new MqttIntegration(
                integration.getId(),
                integration.getHost(),
                integration.getPort(),
                integration.isUseTLS(),
                integration.getIdentifier(),
                integration.getTopic(),
                integration.getUsername(),
                integration.getPassword(),
                integration.getPayloadType(),
                integration.isEnabled(),
                integration.getCreatedAt(),
                now,
                integration.getLastUsed(),
                integration.getVersion() + 1
        );
    }

    public void deleteForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM mqtt_integrations WHERE user_id = ?", user.getId());
    }

    private static class MqttIntegrationRowMapper implements RowMapper<MqttIntegration> {
        @Override
        public MqttIntegration mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            Timestamp lastUsed = rs.getTimestamp("last_used");
            
            return new MqttIntegration(
                    rs.getLong("id"),
                    rs.getString("host"),
                    rs.getInt("port"),
                    rs.getBoolean("use_tls"),
                    rs.getString("identifier"),
                    rs.getString("topic"),
                    rs.getString("username"),
                    rs.getString("password"),
                    PayloadType.valueOf(rs.getString("payload_type")),
                    rs.getBoolean("enabled"),
                    rs.getTimestamp("created_at").toInstant(),
                    updatedAt != null ? updatedAt.toInstant() : null,
                    lastUsed != null ? lastUsed.toInstant() : null,
                    rs.getLong("version")
            );
        }
    }
}
