package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.MapLibreStyleDefinition;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class MapLibreMapStylesService {
    private static final Logger log = LoggerFactory.getLogger(MapLibreMapStylesService.class);

    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final ContextPathHolder contextPathHolder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean tileCachingEnabled;

    public MapLibreMapStylesService(
            UserMapStyleJdbcService userMapStyleJdbcService,
            ContextPathHolder contextPathHolder,
            ObjectMapper objectMapper,
            @Value("${reitti.ui.tiles.cache.url:}") String cacheUrl) {
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.contextPathHolder = contextPathHolder;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.tileCachingEnabled = StringUtils.hasText(cacheUrl);
    }

    @Cacheable("mapStyles")
    public List<MapLibreStyleDefinition> getConfig(User user) {
        List<UserMapStyle> all = this.userMapStyleJdbcService.findAll(user);
        List<MapLibreStyleDefinition> definitions = new ArrayList<>();
        for (UserMapStyle style : all) {
            try {
                definitions.add(buildStyleDefinition(style));
            } catch (Exception e) {
                log.warn("Failed to build style definition for style [{}]: {}", style.id(), e.getMessage());
            }
        }
        return definitions;
    }

    @Cacheable(value = "mapStyleJson", key = "#styleId + ':' + (#user != null ? #user.id : 'anon')")
    public JsonNode getCompleteStyleJson(String styleId, User user) {
        try {
            Long customId = getCustomId(styleId);
            if (customId == null || user == null) {
                return null;
            }
            Optional<UserMapStyle> styleOpt = userMapStyleJdbcService.findById(user, customId);
            if (styleOpt.isEmpty()) {
                return null;
            }
            UserMapStyle style = styleOpt.get();
            return buildCustomStyleJson(style);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the original upstream tile URL template for a given style and source.
     * For the built-in "reitti" style, uses the hardcoded map.
     * For custom styles, returns the original URL from the style JSON (before rewriting).
     */
    public String getOriginalTileUrl(String styleId, String sourceId, User user) {
        try {
            Long customId = getCustomId(styleId);
            if (customId == null || user == null) {
                return null;
            }
            Optional<UserMapStyle> styleOpt = userMapStyleJdbcService.findById(user, customId);
            if (styleOpt.isEmpty()) {
                return null;
            }
            UserMapStyle style = styleOpt.get();
            // Build the style JSON without rewriting (by passing proxyEnabled=false)
            JsonNode originalStyle = buildCustomStyleJsonInternal(style, false);
            if (originalStyle == null) {
                return null;
            }
            JsonNode source = findSource(originalStyle, sourceId);
            if (source == null) {
                return null;
            }
            // Try "tiles" array first
            JsonNode tiles = source.get("tiles");
            if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
                String tileUrl = tileArray.get(0).asText("");
                if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                    return tileUrl;
                }
            }
            // Try "url" (TileJSON)
            String url = source.path("url").asText("");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get original tile URL for [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return null;
        }
    }

    private static Long getCustomId(String styleId) {
        try {
            return Long.parseLong(styleId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode buildCustomStyleJson(UserMapStyle style) throws IOException {
        boolean shouldProxy = tileCachingEnabled
                && style.dataSource() != null
                && style.dataSource().proxyTiles();

        return buildCustomStyleJsonInternal(style, shouldProxy);
    }

    private JsonNode buildCustomStyleJsonInternal(UserMapStyle style, boolean shouldProxy) throws IOException {
        String styleId = String.valueOf(style.id());

        JsonNode styleJson;
        if ("raster".equals(style.mapType())) {
            styleJson = buildRasterStyleJson(style, shouldProxy, styleId);
        } else if ("vector".equals(style.mapType())) {
            styleJson = buildVectorStyleJson(style, shouldProxy, styleId);
        } else {
            return null;
        }

        if (styleJson == null) {
            return null;
        }
        return finalizeStyle((ObjectNode) styleJson, styleId, shouldProxy);
    }

    private JsonNode buildRasterStyleJson(UserMapStyle style, boolean shouldProxy, String styleId) {
        MapStyleDataSource dataSource = style.dataSource();
        if (dataSource == null) {
            return null;
        }

        ObjectNode rasterStyle = objectMapper.createObjectNode();
        rasterStyle.put("version", 8);
        rasterStyle.put("name", style.name() != null ? style.name() : "Raster");

        String sourceId = dataSource.sourceId() != null ? dataSource.sourceId() : "raster-tiles";
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        source.put("tileSize", dataSource.tileSize() != null ? dataSource.tileSize() : 256);
        if (StringUtils.hasText(dataSource.attribution())) {
            source.put("attribution", dataSource.attribution());
        }
        if (dataSource.minzoom() != null) {
            source.put("minzoom", dataSource.minzoom());
        }
        if (dataSource.maxzoom() != null) {
            source.put("maxzoom", dataSource.maxzoom());
        }

        if (StringUtils.hasText(dataSource.tileJsonUrl())) {
            String tileJsonUrl = dataSource.tileJsonUrl();
            if (shouldProxy) {
                tileJsonUrl = proxyTileJsonUrl(styleId, sourceId);
            }
            source.put("url", tileJsonUrl);
        } else if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            String tileUrl = dataSource.tileUrlTemplate();
            if (shouldProxy) {
                tileUrl = proxyTileUrl(styleId, sourceId, tileUrl);
            }
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(tileUrl);
            source.set("tiles", tiles);
        } else {
            return null;
        }

        ObjectNode sources = objectMapper.createObjectNode();
        sources.set(sourceId, source);
        rasterStyle.set("sources", sources);

        ObjectNode layer = objectMapper.createObjectNode();
        layer.put("id", "raster-layer");
        layer.put("type", "raster");
        layer.put("source", sourceId);

        ArrayNode layers = objectMapper.createArrayNode();
        layers.add(layer);
        rasterStyle.set("layers", layers);

        return rasterStyle;
    }

    private JsonNode buildVectorStyleJson(UserMapStyle style, boolean shouldProxy, String styleId) throws IOException {
        JsonNode styleNode;

        if (StringUtils.hasText(style.styleJson())) {
            styleNode = objectMapper.readTree(style.styleJson());
        } else if (StringUtils.hasText(style.styleUrl())) {
            styleNode = fetchStyleJson(style.styleUrl());
        } else {
            return null;
        }

        if (shouldProxy && styleNode instanceof ObjectNode) {
            rewriteTileUrlsInStyle((ObjectNode) styleNode, styleId);
        }

        return styleNode;
    }

    private JsonNode finalizeStyle(ObjectNode style, String styleId, boolean proxyEnabled) {
        // Do not add runtime sources for the reitti style (they are already in the JSON)
        if (!"reitti".equals(styleId)) {
            ensureRuntimeSources(style);
        }
        rewriteResourceUrls(style);

        if (proxyEnabled) {
            rewriteTileUrlsInStyle(style, styleId);
        }

        return style;
    }

    private void ensureRuntimeSources(ObjectNode style) {
        ObjectNode sources = ensureSourcesNode(style);

        if (!sources.has("reitti-terrain-source")) {
            sources.set("reitti-terrain-source", buildTerrainSource());
        }
        if (!sources.has("reitti-satellite-source")) {
            sources.set("reitti-satellite-source", buildSatelliteSource());
        }
        if (!styleHasBuildingLayer(style) && !sources.has("reitti-building-source")) {
            sources.set("reitti-building-source", buildBuildingSource());
        }
    }

    private ObjectNode buildTerrainSource() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster-dem");
        String url = tileCachingEnabled
                ? contextPathHolder.getContextPath() + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp"
                : "https://tiles.mapterhorn.com/{z}/{x}/{y}.webp";
        source.set("tiles", singleTileArray(url));
        source.put("tileSize", 256);
        source.put("encoding", "terrarium");
        source.put("maxzoom", 14);
        source.put("attribution", "© <a href='https://mapterhorn.com' target='_blank'>Mapterhorn</a>");
        return source;
    }

    private ObjectNode buildSatelliteSource() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        String url = tileCachingEnabled
                ? contextPathHolder.getContextPath() + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg"
                : "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
        source.set("tiles", singleTileArray(url));
        source.put("tileSize", 256);
        source.put("maxzoom", 18);
        source.put("attribution", "Powered by <a href='https://www.esri.com' target='_blank'>Esri</a> | Sources: Esri, Maxar, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community");
        return source;
    }

    private ObjectNode buildBuildingSource() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "vector");
        source.put("url", "https://tiles.dedicatedcode.com/planet");
        source.put("minzoom", 0);
        source.put("maxzoom", 14);
        source.put("attribution", "© <a href='https://openfreemap.org' target='_blank'>OpenFreeMap</a> © <a href='https://www.openstreetmap.org/copyright' target='_blank'>OSM</a>");
        return source;
    }

    private boolean styleHasBuildingLayer(ObjectNode style) {
        JsonNode layers = style.get("layers");
        if (!(layers instanceof ArrayNode layerArray)) {
            return false;
        }
        for (JsonNode layer : layerArray) {
            String layerType = layer.path("type").asText("");
            if (!"fill".equals(layerType) && !"fill-extrusion".equals(layerType)) {
                continue;
            }
            String layerId = layer.path("id").asText("").toLowerCase();
            String sourceLayer = layer.path("source-layer").asText("").toLowerCase();
            if (layerId.contains("building") || sourceLayer.contains("building")) {
                return true;
            }
        }
        return false;
    }

    private void rewriteResourceUrls(ObjectNode style) {
        if (style.get("glyphs") instanceof TextNode glyphsText && glyphsText.asText().startsWith("/")) {
            style.set("glyphs", new TextNode(contextPathHolder.getContextPath() + glyphsText.asText()));
        }
    }

    private void rewriteTileUrlsInStyle(ObjectNode style, String styleId) {
        JsonNode sources = style.path("sources");
        if (!(sources instanceof ObjectNode sourcesObject)) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : sourcesObject.properties()) {
            String sourceId = entry.getKey();
            JsonNode source = entry.getValue();
            if (!(source instanceof ObjectNode sourceNode)) {
                continue;
            }

            // Rewrite "url" (TileJSON URL)
            if (sourceNode.has("url")) {
                String url = sourceNode.get("url").asText("");
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    sourceNode.put("url", proxyTileJsonUrl(styleId, sourceId));
                }
            }

            // Rewrite "tiles" array
            if (sourceNode.has("tiles") && sourceNode.get("tiles") instanceof ArrayNode tiles) {
                ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                for (JsonNode tile : tiles) {
                    String tileUrl = tile.asText("");
                    if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                        rewrittenTiles.add(proxyTileUrl(styleId, sourceId, tileUrl));
                    } else {
                        rewrittenTiles.add(tileUrl);
                    }
                }
                sourceNode.set("tiles", rewrittenTiles);
            }
        }
    }

    private String proxyTileUrl(String styleId, String sourceId, String originalUrl) {
        String ext = TileUrlUtils.extractTileExtension(originalUrl);
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/" + sourceId + "/{z}/{x}/{y}." + ext;
    }

    private String proxyTileJsonUrl(String styleId, String sourceId) {
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/" + sourceId + "/tilejson.json";
    }

    private JsonNode fetchStyleJson(String styleUrl) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(styleUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Failed to fetch style JSON: HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching style JSON", e);
        }
    }

    private ObjectNode ensureSourcesNode(ObjectNode mutableStyle) {
        if (mutableStyle.get("sources") instanceof ObjectNode existing) {
            return existing;
        }
        ObjectNode sources = objectMapper.createObjectNode();
        mutableStyle.set("sources", sources);
        return sources;
    }

    private ArrayNode singleTileArray(String tileUrl) {
        ArrayNode tiles = objectMapper.createArrayNode();
        tiles.add(tileUrl);
        return tiles;
    }

    private MapLibreStyleDefinition buildStyleDefinition(UserMapStyle style) {
        String styleId = String.valueOf(style.id());
        String contextPath = contextPathHolder.getContextPath();

        return new MapLibreStyleDefinition(
                styleId,
                style.name(),
                style.mapType(),
                "url",
                contextPath + "/api/v1/tiles/styles/" + styleId + "/style.json",
                buildCapabilities(style));
    }

    private Map<String, Object> buildCapabilities(UserMapStyle style) {
        Map<String, Object> caps = new HashMap<>();
        if ("vector".equals(style.mapType())) {
            caps.put("terrainSourceId", "reitti-terrain-source");
            caps.put("hillshadeLayerId", "reitti-terrain-hillshade");
            caps.put("satelliteLayerId", "reitti-satellite-layer");
            caps.put("building3dLayerIds", Collections.singletonList("reitti-building-3d"));
        }
        return caps;
    }

    // Helper to find a source node in a style JSON
    private JsonNode findSource(JsonNode style, String sourceId) {
        JsonNode sources = style.path("sources");
        if (!(sources instanceof ObjectNode sourcesObject)) {
            return null;
        }
        JsonNode exactSource = sourcesObject.get(sourceId);
        if (exactSource instanceof ObjectNode) {
            return exactSource;
        }
        List<String> allSourceIds = new ArrayList<>();
        sourcesObject.fieldNames().forEachRemaining(allSourceIds::add);
        for (Map.Entry<String, JsonNode> entry : sourcesObject.properties()) {
            if (entry.getValue() instanceof ObjectNode) {
                return entry.getValue();
            }
        }
        return null;
    }
}
