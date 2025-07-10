package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.UserSettings;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserSettingsJdbcService {
    
    private final JdbcTemplate jdbcTemplate;

    public UserSettingsJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserSettings> userSettingsRowMapper = (rs, rowNum) -> {
        Long userId = rs.getLong("user_id");
        List<Long> connectedAccounts = getConnectedUserAccounts(userId);
        
        return new UserSettings(
                userId,
                rs.getBoolean("prefer_colored_map"),
                rs.getString("selected_language"),
                connectedAccounts,
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
        if (userSettings.getVersion() == null) {
            // Insert new settings
            KeyHolder keyHolder = new GeneratedKeyHolder();
            this.jdbcTemplate.update("INSERT INTO user_settings (user_id, prefer_colored_map, selected_language, version) VALUES (?, ?, ?, 1)",
                    userSettings.getUserId(),
                    userSettings.isPreferColoredMap(),
                    userSettings.getSelectedLanguage());


            // Update user connections
            updateUserConnections(userSettings.getUserId(), userSettings.getConnectedUserAccounts());

            return new UserSettings(userSettings.getUserId(), userSettings.isPreferColoredMap(),
                    userSettings.getSelectedLanguage(), userSettings.getConnectedUserAccounts(), 1L);
        } else {
            // Update existing settings
            jdbcTemplate.update(
                    "UPDATE user_settings SET prefer_colored_map = ?, selected_language = ?, version = version + 1 WHERE user_id = ?",
                    userSettings.isPreferColoredMap(),
                    userSettings.getSelectedLanguage(),
                    userSettings.getUserId()
            );
            
            // Update user connections
            updateUserConnections(userSettings.getUserId(), userSettings.getConnectedUserAccounts());
            
            return findByUserId(userSettings.getUserId()).orElse(userSettings);
        }
    }
    
    public UserSettings getOrCreateDefaultSettings(Long userId) {
        return findByUserId(userId).orElseGet(() -> save(UserSettings.defaultSettings(userId)));
    }
    
    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM user_settings WHERE user_id = ?", userId);
    }
    
    private List<Long> getConnectedUserAccounts(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT to_user FROM connected_users WHERE from_user = ?",
                Long.class,
                userId);
    }
    
    private void updateUserConnections(Long userId, List<Long> connectedUserAccounts) {
        // First, remove all existing connections for this user
        jdbcTemplate.update(
                "DELETE FROM connected_users WHERE from_user = ? OR to_user = ?",
                userId, userId
        );
        
        // Then, add the new connections
        for (Long connectedUserId : connectedUserAccounts) {
            jdbcTemplate.update(
                    "INSERT INTO connected_users (from_user, to_user) VALUES (?, ?)",
                    userId, connectedUserId
            );
        }
    }
}
