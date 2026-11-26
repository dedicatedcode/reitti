package com.dedicatedcode.reitti.model.extension;

import java.util.Map;

public record ExtensionLayer(
        String id,
        String name,
        String description,
        String type,
        String protocol,
        String endpoint,
        Long pollIntervalMs,
        String pollTrigger,
        String renderer,
        String format,
        String dataPath,
        Map<String, Object> assets,
        Map<String, Object> mapping,
        Integer minZoom,
        Long staleAfterMs,
        String placement,
        Integer zIndex,
        Map<String, String> assetUrls) {
}
