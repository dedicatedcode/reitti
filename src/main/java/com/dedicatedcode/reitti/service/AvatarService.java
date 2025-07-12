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


    public record AvatarData(String mimeType, byte[] imageData, long updatedAt) {}

    public record AvatarInfo(long updatedAt) {}
}
