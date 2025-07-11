package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/avatars")
public class AvatarController {

    private final JdbcTemplate jdbcTemplate;

    public AvatarController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long userId) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                    "SELECT mime_type, binary_data FROM user_avatars WHERE user_id = ?",
                    userId
            );

            String contentType = (String) result.get("mime_type");
            byte[] imageData = (byte[]) result.get("binary_data");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(imageData.length);

            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);

        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
    }

}