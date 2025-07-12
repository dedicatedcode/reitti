package com.dedicatedcode.reitti.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
                    "SELECT mime_type, binary_data FROM user_avatars WHERE user_id = ?",
                    userId
            );

            String contentType = (String) result.get("mime_type");
            byte[] imageData = (byte[]) result.get("binary_data");

            return Optional.of(new AvatarData(contentType, imageData));

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public record AvatarData(String mimeType, byte[] imageData) {}
}
