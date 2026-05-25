package com.dedicatedcode.reitti.dto.map;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;

public class MapStyleFormDTO {
    private String id;
    private String label;
    private String mapType = "vector";
    private String styleInputType = "url";
    private String rasterSourceInputType = "tile_template";
    private String styleUrl;
    private String styleJson;
    private boolean shared;
    private boolean proxyTiles;
    private String tileUrlTemplate;
    private String tileJsonUrl;
    private String attribution;
    private String rasterAttributionOverride;
    private Integer minzoom;
    private Integer maxzoom;
    private Integer tileSize;
    private String scheme;
    private String attributionOverride;
    private String glyphsUrlOverride;
    private String spriteUrlOverride;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getMapType() { return mapType; }
    public void setMapType(String mapType) { this.mapType = mapType; }
    public String getStyleInputType() { return styleInputType; }
    public void setStyleInputType(String styleInputType) { this.styleInputType = styleInputType; }
    public String getRasterSourceInputType() { return rasterSourceInputType; }
    public void setRasterSourceInputType(String rasterSourceInputType) { this.rasterSourceInputType = rasterSourceInputType; }
    public String getStyleUrl() { return styleUrl; }
    public void setStyleUrl(String styleUrl) { this.styleUrl = styleUrl; }
    public String getStyleJson() { return styleJson; }
    public void setStyleJson(String styleJson) { this.styleJson = styleJson; }
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
    public boolean isProxyTiles() { return proxyTiles; }
    public void setProxyTiles(boolean proxyTiles) { this.proxyTiles = proxyTiles; }
    public String getTileUrlTemplate() { return tileUrlTemplate; }
    public void setTileUrlTemplate(String tileUrlTemplate) { this.tileUrlTemplate = tileUrlTemplate; }
    public String getTileJsonUrl() { return tileJsonUrl; }
    public void setTileJsonUrl(String tileJsonUrl) { this.tileJsonUrl = tileJsonUrl; }
    public String getAttribution() { return attribution; }
    public void setAttribution(String attribution) { this.attribution = attribution; }
    public String getRasterAttributionOverride() { return rasterAttributionOverride; }
    public void setRasterAttributionOverride(String rasterAttributionOverride) { this.rasterAttributionOverride = rasterAttributionOverride; }
    public Integer getMinzoom() { return minzoom; }
    public void setMinzoom(Integer minzoom) { this.minzoom = minzoom; }
    public Integer getMaxzoom() { return maxzoom; }
    public void setMaxzoom(Integer maxzoom) { this.maxzoom = maxzoom; }
    public Integer getTileSize() { return tileSize; }
    public void setTileSize(Integer tileSize) { this.tileSize = tileSize; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
    public String getAttributionOverride() { return attributionOverride; }
    public void setAttributionOverride(String attributionOverride) { this.attributionOverride = attributionOverride; }
    public String getGlyphsUrlOverride() { return glyphsUrlOverride; }
    public void setGlyphsUrlOverride(String glyphsUrlOverride) { this.glyphsUrlOverride = glyphsUrlOverride; }
    public String getSpriteUrlOverride() { return spriteUrlOverride; }
    public void setSpriteUrlOverride(String spriteUrlOverride) { this.spriteUrlOverride = spriteUrlOverride; }

    public SaveMapStyleRequest toSaveRequest() {
        boolean vector = !"raster".equals(mapType);
        String styleInput = "json".equals(styleInputType) ? styleJson : styleUrl;
        String dataSourceAttribution = "tilejson".equals(rasterSourceInputType) ? rasterAttributionOverride : attribution;
        MapStyleDataSource dataSource = new MapStyleDataSource(
                null,
                vector ? "vector" : "raster",
                tileJsonUrl,
                tileUrlTemplate,
                dataSourceAttribution,
                minzoom,
                maxzoom,
                tileSize,
                scheme,
                proxyTiles
        );
        MapStyleVectorOptions vectorOptions = new MapStyleVectorOptions(
                attributionOverride,
                glyphsUrlOverride,
                spriteUrlOverride
        );
        return new SaveMapStyleRequest(
                id,
                label,
                mapType,
                styleInputType,
                rasterSourceInputType,
                styleInput,
                shared,
                dataSource,
                vectorOptions
        );
    }
}
