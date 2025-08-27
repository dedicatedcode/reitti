package com.dedicatedcode.reitti.dto;

import java.util.List;

public record UserTimelineData(
        String userId,
        String displayName,
        String avatarFallback,
        String userAvatarUrl,
        String baseColor,
        List<TimelineEntry> entries,
        String rawLocationPointsUrl
) {
}
