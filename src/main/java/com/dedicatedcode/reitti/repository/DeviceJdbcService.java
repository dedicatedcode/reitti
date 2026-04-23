package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceJdbcService {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Device> deviceRowMapper = (rs, rowNum) -> new Device(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getBoolean("enabled"),
            rs.getBoolean("show_on_map"),
            rs.getString("color"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    public DeviceJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @CacheEvict(value = "devices", allEntries = true)
    public Device save(Device device, User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO devices (user_id, name, color, enabled, show_on_map, created_at, updated_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, user.getId());
            ps.setString(2, device.name());
            ps.setString(3, device.color());
            ps.setBoolean(4, device.enabled());
            ps.setBoolean(5, device.showOnMap());
            ps.setTimestamp(6, Timestamp.from(device.createdAt()));
            ps.setTimestamp(7, Timestamp.from(device.updatedAt()));
            ps.setLong(8, 1L);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return new Device(
                key.longValue(),
                device.name(),
                device.enabled(),
                device.showOnMap(),
                device.color(),
                device.createdAt(),
                device.updatedAt(),
                1L
        );
    }

    @CacheEvict(value = "devices", allEntries = true)
    public Device update(Device device, User user) {
        int updated = jdbcTemplate.update(
                "UPDATE devices SET name = ?, color = ?, enabled = ?, show_on_map = ?, updated_at = ?, version = version + 1 " +
                        "WHERE id = ? AND user_id = ?",
                device.name(),
                device.color(),
                device.enabled(),
                device.showOnMap(),
                Timestamp.from(Instant.now()),
                device.id(),
                user.getId()
        );

        if (updated == 0) {
            throw new IllegalArgumentException("Device not found or not owned by user");
        }

        return new Device(
                device.id(),
                device.name(),
                device.enabled(),
                device.showOnMap(),
                device.color(),
                device.createdAt(),
                Instant.now(),
                device.version() + 1
        );
    }

    @CacheEvict(value = "devices", allEntries = true)
    public void delete(Device device, User user) {
        jdbcTemplate.update(
                "DELETE FROM devices WHERE id = ? AND user_id = ?",
                device.id(),
                user.getId()
        );
    }

    public List<Device> getAll(User user) {
        return jdbcTemplate.query(
                "SELECT * FROM devices WHERE user_id = ? ORDER BY created_at DESC",
                deviceRowMapper,
                user.getId()
        );
    }

    public List<Device> getAllEnabled(User user) {
        return jdbcTemplate.query(
                "SELECT * FROM devices WHERE user_id = ? AND enabled = TRUE ORDER BY created_at DESC",
                deviceRowMapper,
                user.getId()
        );
    }

    @Cacheable(value = "devices", key = "#token")
    public Optional<Device> findByApiToken(String token) {
        List<Device> results = jdbcTemplate.query(
                "SELECT d.* FROM devices d " +
                        "JOIN api_tokens t ON t.device_id = d.id " +
                        "WHERE t.token = ?",
                deviceRowMapper,
                token
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
