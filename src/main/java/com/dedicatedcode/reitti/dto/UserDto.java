package com.dedicatedcode.reitti.dto;

public record UserDto(
    Long id,
    String username,
    String displayName,
    String avatarUrl,
    String avatarFallback
) {
}
