package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.devices.Device;

import java.util.List;

public record UserTimelineData(
        String userId,
        String displayName,
        String avatarFallback,
        String userAvatarUrl,
        String baseColor,
        List<TimelineEntry> entries,
        String rawLocationPointsUrl,
        String processedVisitsUrl,
        String mapMetaDataUrl,
        String mapStreamDataUrl,
        List<Device> devices) {
}
