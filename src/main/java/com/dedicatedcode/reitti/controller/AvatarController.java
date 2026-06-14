package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.service.AvatarService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/avatars")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long userId) {
        Optional<AvatarService.AvatarData> avatarData = avatarService.getAvatarByUserId(userId);
        if (avatarData.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
        return serveBinary(avatarData.get());
    }

    @GetMapping("/{userId}/{deviceId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long userId, @PathVariable Long deviceId) {
        Optional<AvatarService.AvatarData> avatarData = avatarService.getAvatarDeviceId(userId, deviceId);
        if (avatarData.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
        return serveBinary(avatarData.get());
    }

    private static ResponseEntity<byte[]> serveBinary(AvatarService.AvatarData avatar) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(avatar.mimeType()));
        headers.setContentLength(avatar.imageData().length);
        headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS));

        return new ResponseEntity<>(avatar.imageData(), headers, HttpStatus.OK);
    }
}
