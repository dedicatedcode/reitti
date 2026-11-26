package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.extension.ExtensionRegistration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MapExtensionJdbcService {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ExtensionRegistration> rowMapper = (rs, _) -> new ExtensionRegistration(
            rs.getLong("id"),
            rs.getString("manifest_id"),
            rs.getString("name"),
            rs.getString("version"),
            rs.getString("manifest_url"),
            rs.getString("manifest"),
            rs.getBoolean("enabled"),
            rs.getString("encrypted_credentials"),
            rs.getLong("revision"),
            instant(rs.getTimestamp("last_refreshed_at")),
            rs.getObject("last_refresh_success", Boolean.class),
            rs.getString("last_error"));

    public MapExtensionJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExtensionRegistration> findAll() {
        return jdbcTemplate.query("SELECT * FROM map_extensions ORDER BY name, id", rowMapper);
    }

    public List<ExtensionRegistration> findEnabled() {
        return jdbcTemplate.query("SELECT * FROM map_extensions WHERE enabled = true ORDER BY name, id", rowMapper);
    }

    public Optional<ExtensionRegistration> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM map_extensions WHERE id = ?", rowMapper, id).stream().findFirst();
    }

    public Optional<ExtensionRegistration> findEnabledById(long id) {
        return jdbcTemplate.query("SELECT * FROM map_extensions WHERE id = ? AND enabled = true", rowMapper, id)
                .stream().findFirst();
    }

    public ExtensionRegistration save(ExtensionRegistration registration) {
        if (registration.id() == null) {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO map_extensions (manifest_id, name, version, manifest_url, manifest, enabled,
                        encrypted_credentials, revision, last_refreshed_at, last_refresh_success, last_error)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, registration.manifestId(), registration.name(), registration.version(),
                    registration.manifestUrl(), registration.manifestJson(), registration.enabled(),
                    registration.encryptedCredentials(), registration.revision(), timestamp(registration.lastRefreshedAt()),
                    registration.lastRefreshSuccess(), registration.lastError());
            return new ExtensionRegistration(id, registration.manifestId(), registration.name(), registration.version(),
                    registration.manifestUrl(), registration.manifestJson(), registration.enabled(),
                    registration.encryptedCredentials(), registration.revision(), registration.lastRefreshedAt(),
                    registration.lastRefreshSuccess(), registration.lastError());
        }
        int updated = jdbcTemplate.update("""
                UPDATE map_extensions SET manifest_id = ?, name = ?, version = ?, manifest_url = ?, manifest = ?::jsonb,
                    enabled = ?, encrypted_credentials = ?, revision = ?, last_refreshed_at = ?,
                    last_refresh_success = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND revision = ?
                """, registration.manifestId(), registration.name(), registration.version(), registration.manifestUrl(),
                registration.manifestJson(), registration.enabled(), registration.encryptedCredentials(), registration.revision(),
                timestamp(registration.lastRefreshedAt()), registration.lastRefreshSuccess(), registration.lastError(),
                registration.id(), registration.revision() - 1);
        if (updated == 0) {
            throw new OptimisticLockException("Map extension " + registration.id() + " was modified concurrently");
        }
        return registration;
    }

    public void recordRefreshFailure(long id, long revision, String sanitizedError) {
        jdbcTemplate.update("""
                UPDATE map_extensions SET last_refreshed_at = CURRENT_TIMESTAMP, last_refresh_success = false,
                    last_error = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND revision = ?
                """, sanitizedError, id, revision);
    }

    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM map_extensions WHERE id = ?", id);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM map_extensions", Long.class);
        return count == null ? 0 : count;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
