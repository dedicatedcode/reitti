package com.dedicatedcode.reitti.dto.timeline;

public record DeviceTimelineData(
        Long id,
        String name,
        String avatarUrl,
        String avatarFallback,
        boolean showAvatarOnMap,
        String color,
        String metadataUrl,
        String streamUrl,
        boolean active
) {
}
