package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.controller.UserSettingsController;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;

@Service
public class AvatarService {

    private final JdbcTemplate jdbcTemplate;

    public AvatarService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AvatarData> getAvatarByUserId(Long userId) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT mime_type, binary_data, updated_at FROM user_avatars WHERE user_id = ?",
                    userId
            );

            String contentType = (String) result.get("mime_type");
            long updatedAt = ((Timestamp) result.get("updated_at")).getTime();
            byte[] imageData = (byte[]) result.get("binary_data");

            return Optional.of(new AvatarData(contentType, imageData, updatedAt));

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AvatarInfo> getInfo(Long userId) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT updated_at FROM user_avatars WHERE user_id = ?",
                    userId
            );

            long updatedAt = ((Timestamp) result.get("updated_at")).getTime();

            return Optional.of(new AvatarInfo(updatedAt));

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateAvatar(Long userId, String contentType, byte[] imageData) {

        jdbcTemplate.update("DELETE FROM user_avatars WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) " +
                        "VALUES (?, ?, ?) ",
                userId,
                contentType,
                imageData
        );
    }

    public void deleteAvatar(Long userId) {
        this.jdbcTemplate.update("DELETE FROM user_avatars WHERE user_id = ?", userId);
    }

    public String generateInitials(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "";
        }

        String trimmed = displayName.trim();

        // If display name contains whitespace, take first char of each word
        if (trimmed.contains(" ")) {
            StringBuilder initials = new StringBuilder();
            String[] words = trimmed.split("\\s+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    initials.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return initials.toString();
        } else {
            // No whitespace - take first two letters, or just one if that's all there is
            if (trimmed.length() >= 2) {
                return (Character.toUpperCase(trimmed.charAt(0)) + "" + Character.toUpperCase(trimmed.charAt(1)));
            } else {
                return Character.toUpperCase(trimmed.charAt(0)) + "";
            }
        }
    }
    public record AvatarData(String mimeType, byte[] imageData, long updatedAt) {}

    public record AvatarInfo(long updatedAt) {}
}
