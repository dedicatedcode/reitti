package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransportModeJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TransportModeJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(value = "transport-mode-configs", key = "#user.id")
    public List<TransportModeConfig> getTransportModeConfigs(User user) {
        String sql = """
            SELECT transport_mode, min_kmh, max_kmh, preference_weight, enabled, color, icon
            FROM transport_mode_detection_configs
            WHERE user_id = ?
            ORDER BY min_kmh, max_kmh
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TransportModeConfig(
                TransportMode.valueOf(rs.getString("transport_mode")),
                rs.getDouble("min_kmh"),
                rs.getDouble("max_kmh"),
                rs.getDouble("preference_weight"),
                rs.getBoolean("enabled"),
                rs.getString("color"),
                rs.getString("icon")
        ), user.getId());
    }

    @Transactional
    @CacheEvict(value = "transport-mode-configs", key = "#user.id")
    public void setTransportModeConfigs(User user, List<TransportModeConfig> configs) {
        String deleteSql = "DELETE FROM transport_mode_detection_configs WHERE user_id = ?";
        jdbcTemplate.update(deleteSql, user.getId());

        String insertSql = """
            INSERT INTO transport_mode_detection_configs (user_id, transport_mode, min_kmh, max_kmh, preference_weight, enabled, color, icon)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        for (TransportModeConfig config : configs) {
            jdbcTemplate.update(insertSql,
                    user.getId(),
                    config.mode().name(),
                    config.minKmh(),
                    config.maxKmh(),
                    config.preferenceWeight(),
                    config.enabled(),
                    config.color(),
                    config.icon());
        }
    }

    @Cacheable(value = "transport-mode-settings", key = "#user.id")
    public double getModeChangeSensitivity(User user) {
        List<Double> values = jdbcTemplate.queryForList(
                "SELECT mode_change_sensitivity FROM transport_mode_detection_settings WHERE user_id = ?",
                Double.class, user.getId());
        return values.isEmpty() ? 2.0 : values.getFirst();
    }

    @CacheEvict(value = "transport-mode-settings", key = "#user.id")
    public void setModeChangeSensitivity(User user, double modeChangeSensitivity) {
        jdbcTemplate.update("""
                INSERT INTO transport_mode_detection_settings (user_id, mode_change_sensitivity)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET mode_change_sensitivity = EXCLUDED.mode_change_sensitivity
                """, user.getId(), modeChangeSensitivity);
    }

    @CacheEvict(value = {"transport-mode-configs", "transport-mode-settings"}, key = "#user.id")
    public void deleteAllForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM transport_mode_detection_configs WHERE user_id = ?", user.getId());
        this.jdbcTemplate.update("DELETE FROM transport_mode_detection_settings WHERE user_id = ?", user.getId());
    }
}
