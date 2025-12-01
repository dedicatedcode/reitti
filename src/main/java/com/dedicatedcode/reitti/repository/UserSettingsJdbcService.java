package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class UserSettingsJdbcService {
    
    private final JdbcTemplate jdbcTemplate;

    public UserSettingsJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserSettings> userSettingsRowMapper = (rs, _) -> {
        Long userId = rs.getLong("user_id");
        Timestamp newestData = rs.getTimestamp("latest_data");
        return new UserSettings(
                userId,
                rs.getBoolean("prefer_colored_map"),
                Language.valueOf(rs.getString("selected_language")),
                UnitSystem.valueOf(rs.getString("unit_system")),
                rs.getDouble("home_lat"),
                rs.getDouble("home_lng"),
                rs.getString("time_zone_override") != null ? ZoneId.of(rs.getString("time_zone_override")) : null,
                TimeDisplayMode.valueOf(rs.getString("time_display_mode")),
                rs.getString("custom_css"),
                newestData != null ? newestData.toInstant() : null,
                rs.getString("color"),
                rs.getLong("version"));
    };
    
    public Optional<UserSettings> findByUserId(Long userId) {
        try {
            UserSettings settings = jdbcTemplate.queryForObject(
                    "SELECT * FROM user_settings WHERE user_id = ?",
                    userSettingsRowMapper,
                    userId
            );
            return Optional.ofNullable(settings);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public UserSettings save(UserSettings userSettings) {
        if (userSettings.getVersion() == null) {
            // Insert new settings
            this.jdbcTemplate.update("INSERT INTO user_settings (user_id, prefer_colored_map, selected_language, unit_system, home_lat, home_lng, time_zone_override, time_display_mode, custom_css, latest_data, color, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                                     userSettings.getUserId(),
                                     userSettings.isPreferColoredMap(),
                                     userSettings.getSelectedLanguage().name(),
                                     userSettings.getUnitSystem().name(),
                                     userSettings.getHomeLatitude(),
                                     userSettings.getHomeLongitude(),
                                     userSettings.getTimeZoneOverride() != null ? userSettings.getTimeZoneOverride().getId() : null,
                                     userSettings.getTimeDisplayMode().name(),
                                     userSettings.getCustomCss(),
                                     userSettings.getLatestData() != null ? Timestamp.from(userSettings.getLatestData()) : null,
                                     userSettings.getColor());

            return userSettings.withVersion(1L);
        } else {
            // Update existing settings
            jdbcTemplate.update(
                    "UPDATE user_settings SET prefer_colored_map = ?, selected_language = ?, unit_system = ?, home_lat = ?, home_lng = ?, time_zone_override = ?, time_display_mode = ?, custom_css = ?, latest_data = GREATEST(latest_data, ?), color = ?, version = version + 1 WHERE user_id = ?",
                    userSettings.isPreferColoredMap(),
                    userSettings.getSelectedLanguage().name(),
                    userSettings.getUnitSystem().name(),
                    userSettings.getHomeLatitude(),
                    userSettings.getHomeLongitude(),
                    userSettings.getTimeZoneOverride() != null ? userSettings.getTimeZoneOverride().getId() : null,
                    userSettings.getTimeDisplayMode().name(),
                    userSettings.getCustomCss(),
                    userSettings.getLatestData() != null ? Timestamp.from(userSettings.getLatestData()) : null,
                    userSettings.getColor(),
                    userSettings.getUserId()
            );
            
            return findByUserId(userSettings.getUserId()).orElse(userSettings);
        }
    }
    
    public UserSettings getOrCreateDefaultSettings(Long userId) {
        return findByUserId(userId).orElseGet(() -> save(UserSettings.defaultSettings(userId)));
    }
    
    public void updateNewestData(User user, List<LocationPoint> filtered) {
        filtered.stream().map(LocationPoint::getTimestamp).max(Comparator.naturalOrder()).ifPresent(timestamp -> {
            Instant instant = ZonedDateTime.parse(timestamp).toInstant();
            this.jdbcTemplate.update("UPDATE user_settings SET latest_data = GREATEST(latest_data, ?) WHERE user_id = ?", Timestamp.from(instant), user.getId());
        });
    }

    public void deleteFor(User user) {
        this.jdbcTemplate.update("DELETE FROM user_settings WHERE user_id = ?", user.getId());
    }
}
