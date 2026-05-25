package com.dedicatedcode.reitti.service;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class MapStylePathUtils {
    private MapStylePathUtils() {
    }

    public static String sourcePathId(String sourceId) {
        return sourcePathId(sourceId, List.of(sourceId == null ? "" : sourceId));
    }

    public static String sourcePathId(String sourceId, Collection<String> allSourceIds) {
        String base = baseSlug(sourceId);
        List<String> colliding = allSourceIds.stream()
                .filter(id -> baseSlug(id).equals(base))
                .distinct()
                .sorted()
                .toList();
        if (colliding.size() <= 1) {
            return base;
        }
        int index = colliding.indexOf(sourceId);
        if (index < 0) {
            return base;
        }
        return base + "-" + (index + 1);
    }

    public static boolean matchesSourcePathId(String pathId, String sourceId) {
        return matchesSourcePathId(pathId, sourceId, List.of(sourceId == null ? "" : sourceId));
    }

    public static boolean matchesSourcePathId(String pathId, String sourceId, Collection<String> allSourceIds) {
        return sourceId != null && (sourceId.equals(pathId) || sourcePathId(sourceId, allSourceIds).equals(pathId));
    }

    private static String baseSlug(String sourceId) {
        if (!StringUtils.hasText(sourceId)) {
            return "source";
        }
        String slug = sourceId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(slug) ? slug : "source";
    }
}
