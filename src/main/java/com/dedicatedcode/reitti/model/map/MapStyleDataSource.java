package com.dedicatedcode.reitti.model.map;

public record MapStyleDataSource(
        String sourceId,
        String type,
        String tileJsonUrl,
        String tileUrlTemplate,
        String attribution,
        Integer minzoom,
        Integer maxzoom,
        Integer tileSize,
        String scheme,
        boolean proxyTiles
) {
}
