package com.dedicatedcode.reitti.service;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class MapStylePathUtils {
    private MapStylePathUtils() {
    }

    public static String sourcePathId(String sourceId) {
        String sourceKey = StringUtils.hasText(sourceId) ? sourceId : "source";
        String slug = StringUtils.hasText(sourceId)
                ? sourceId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "")
                : "source";
        if (!StringUtils.hasText(slug)) {
            slug = "source";
        }
        return slug + "-" + Integer.toUnsignedString(sourceKey.hashCode(), 36);
    }

    public static boolean matchesSourcePathId(String pathId, String sourceId) {
        return sourceId != null && (sourceId.equals(pathId) || sourcePathId(sourceId).equals(pathId));
    }
}
