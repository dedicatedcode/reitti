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
    public MapStyleDataSource withProxyTiles(boolean proxyTiles) {
        return new MapStyleDataSource(sourceId, type, tileJsonUrl, tileUrlTemplate, attribution,
                minzoom, maxzoom, tileSize, scheme, proxyTiles);
    }
}
