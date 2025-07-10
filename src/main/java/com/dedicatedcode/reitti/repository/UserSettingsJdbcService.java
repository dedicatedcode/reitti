package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.UserSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Service
public class UserSettingsJdbcService {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    public UserSettingsJdbcService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    private final RowMapper<UserSettings> userSettingsRowMapper = (rs, rowNum) -> {
        return new UserSettings(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getBoolean("prefer_colored_map"),
                rs.getString("selected_language"),
                List.of(), // Connected accounts are stored in a separate table
                rs.getLong("version")
        );
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
        if (userSettings.getId() == null) {
            // Insert new settings
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO user_settings (user_id, prefer_colored_map, selected_language) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, userSettings.getUserId());
                ps.setBoolean(2, userSettings.isPreferColoredMap());
                ps.setString(3, userSettings.getSelectedLanguage());
                return ps;
            }, keyHolder);
            
            Long id = keyHolder.getKey().longValue();
            return new UserSettings(id, userSettings.getUserId(), userSettings.isPreferColoredMap(), 
                    userSettings.getSelectedLanguage(), List.of(), 1L);
        } else {
            // Update existing settings
            jdbcTemplate.update(
                    "UPDATE user_settings SET prefer_colored_map = ?, selected_language = ?, version = version + 1 WHERE id = ?",
                    userSettings.isPreferColoredMap(),
                    userSettings.getSelectedLanguage(),
                    userSettings.getId()
            );
            
            return findByUserId(userSettings.getUserId()).orElse(userSettings);
        }
    }
    
    public UserSettings getOrCreateDefaultSettings(Long userId) {
        return findByUserId(userId).orElseGet(() -> save(UserSettings.defaultSettings(userId)));
    }
    
    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM user_settings WHERE user_id = ?", userId);
    }
}
