package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class TransportModeJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TransportModeJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(value = "transport-mode-configs", key = "#user.id")
    public List<TransportModeConfig> getTransportModeConfigs(User user) {
        String sql = """
            SELECT transport_mode, max_kmh, color, icon
            FROM transport_mode_detection_configs
            WHERE user_id = ? 
            ORDER BY max_kmh NULLS LAST
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            TransportMode mode = TransportMode.valueOf(rs.getString("transport_mode"));
            Double maxKmh = Optional.ofNullable(rs.getObject("max_kmh")).map(BigDecimal.class::cast).map(BigDecimal::doubleValue).orElse(null);
            String color = rs.getString("color");
            String icon = rs.getString("icon");
            return new TransportModeConfig(mode, maxKmh, color, icon);
        }, user.getId());
    }

    @Transactional
    @CacheEvict(value = "transport-mode-configs", key = "#user.id")
    public void setTransportModeConfigs(User user, List<TransportModeConfig> configs) {
        String deleteSql = "DELETE FROM transport_mode_detection_configs WHERE user_id = ?";
        jdbcTemplate.update(deleteSql, user.getId());

        String insertSql = """
            INSERT INTO transport_mode_detection_configs (user_id, transport_mode, max_kmh, color, icon)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        for (TransportModeConfig config : configs) {
            Double maxKmh = config.maxKmh();
            jdbcTemplate.update(insertSql, user.getId(), config.mode().name(), maxKmh, config.color(), config.icon());
        }
    }

    @CacheEvict(value = "transport-mode-configs", key = "#user.id")
    public void deleteAllForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM transport_mode_detection_configs WHERE user_id = ?", user.getId());
    }
}
