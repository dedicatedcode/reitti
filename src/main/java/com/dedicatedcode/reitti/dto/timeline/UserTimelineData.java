package com.dedicatedcode.reitti.dto.timeline;

import java.util.List;

public record UserTimelineData(
        String userId,
        String displayName,
        String avatarFallback,
        String userAvatarUrl,
        String baseColor,
        List<? extends TimelineEntry> entries,
        String rawLocationPointsUrl,
        String processedVisitsUrl,
        String mapMetaDataUrl,
        String mapStreamDataUrl,
        String h3CellUrl,
        List<DeviceTimelineData> devices,
        boolean active) {
}
