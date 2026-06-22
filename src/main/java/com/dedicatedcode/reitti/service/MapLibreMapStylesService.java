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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MapLibreMapStylesService {
    private static final Logger log = LoggerFactory.getLogger(MapLibreMapStylesService.class);

    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final ContextPathHolder contextPathHolder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean tileCachingEnabled;

    // Cache for original tile URLs: key = styleId + ":" + sourceId, value = original tile URL template
    private final ConcurrentHashMap<String, String> originalTileUrlCache = new ConcurrentHashMap<>();
    // Cache for original TileJSON URLs: key = styleId + ":" + sourceId + ":tilejson", value = original TileJSON URL
    private final ConcurrentHashMap<String, String> originalTileJsonUrlCache = new ConcurrentHashMap<>();

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
    public JsonNode getCompleteStyleJson(Long styleId, User user) {
        try {
            Optional<UserMapStyle> styleOpt = userMapStyleJdbcService.findById(user, styleId);
            if (styleOpt.isEmpty()) {
                return null;
            }
            UserMapStyle style = styleOpt.get();
            return buildCustomStyleJson(style);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getOriginalTileUrl(Long styleId, String sourceId, User user) {
        String cacheKey = styleId + ":" + sourceId;
        // 1. Check tile URL cache
        String cachedTileUrl = originalTileUrlCache.get(cacheKey);
        if (cachedTileUrl != null) {
            return cachedTileUrl;
        }
        // 2. Check TileJSON URL cache (if we have a TileJSON URL, fetch it and cache the tile URL)
        String tileJsonCacheKey = cacheKey + ":tilejson";
        String cachedTileJsonUrl = originalTileJsonUrlCache.get(tileJsonCacheKey);
        if (cachedTileJsonUrl != null) {
            try {
                String tileUrl = fetchTileUrlFromTileJson(cachedTileJsonUrl);
                if (tileUrl != null) {
                    originalTileUrlCache.put(cacheKey, tileUrl);
                    return tileUrl;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch tile URL from cached TileJSON [{}]: {}", cachedTileJsonUrl, e.getMessage());
            }
        }
        // 3. Fallback: parse the style JSON (without proxying) to get the original URL
        try {
            Optional<UserMapStyle> styleOpt = userMapStyleJdbcService.findById(user, styleId);
            if (styleOpt.isEmpty()) {
                return null;
            }
            UserMapStyle style = styleOpt.get();
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
                    originalTileUrlCache.put(cacheKey, tileUrl);
                    return tileUrl;
                }
            }
            // Try "url" (TileJSON)
            String url = source.path("url").asText("");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Store the TileJSON URL and then fetch the tile URL
                originalTileJsonUrlCache.put(tileJsonCacheKey, url);
                String tileUrl = fetchTileUrlFromTileJson(url);
                if (tileUrl != null) {
                    originalTileUrlCache.put(cacheKey, tileUrl);
                    return tileUrl;
                }
                return url; // fallback: return the TileJSON URL itself
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get original tile URL for [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the original TileJSON URL for a given style and source, if it exists.
     * This method populates the internal caches if necessary.
     */
    public String getOriginalTileJsonUrl(Long styleId, String sourceId, User user) {
        String tileJsonCacheKey = styleId + ":" + sourceId + ":tilejson";
        // Check cache
        String cached = originalTileJsonUrlCache.get(tileJsonCacheKey);
        if (cached != null) {
            return cached;
        }
        // Trigger getOriginalTileUrl which will fill the cache if appropriate
        try {
            getOriginalTileUrl(styleId, sourceId, user);
        } catch (Exception e) {
            log.debug("Could not populate tilejson cache: {}", e.getMessage());
        }
        // Re-check
        cached = originalTileJsonUrlCache.get(tileJsonCacheKey);
        return cached; // may be null
    }

    private String fetchTileUrlFromTileJson(String tileJsonUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tileJsonUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to fetch TileJSON: HTTP " + response.statusCode());
        }
        JsonNode tileJson = objectMapper.readTree(response.body());
        JsonNode tiles = tileJson.get("tiles");
        if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            String tileUrl = tileArray.get(0).asText("");
            if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                return tileUrl;
            }
        }
        return null;
    }

    private JsonNode buildCustomStyleJson(UserMapStyle style) throws IOException {
        boolean shouldProxy = tileCachingEnabled
                && style.dataSource() != null
                && style.dataSource().proxyTiles();

        return buildCustomStyleJsonInternal(style, shouldProxy);
    }

    private JsonNode buildCustomStyleJsonInternal(UserMapStyle style, boolean shouldProxy) throws IOException {
        Long styleId = style.id();

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

    private JsonNode buildRasterStyleJson(UserMapStyle style, boolean shouldProxy, Long styleId) {
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
            String originalTileJsonUrl = dataSource.tileJsonUrl();
            if (shouldProxy) {
                // Store original TileJSON URL in cache
                String tileJsonCacheKey = styleId + ":" + sourceId + ":tilejson";
                originalTileJsonUrlCache.put(tileJsonCacheKey, originalTileJsonUrl);
                source.put("url", proxyTileJsonUrl(styleId, sourceId));
            } else {
                source.put("url", originalTileJsonUrl);
            }
        } else if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            String originalTileUrl = dataSource.tileUrlTemplate();
            if (shouldProxy) {
                // Store original tile URL in cache
                String cacheKey = styleId + ":" + sourceId;
                originalTileUrlCache.put(cacheKey, originalTileUrl);
                String proxiedUrl = proxyTileUrl(styleId, sourceId, originalTileUrl);
                ArrayNode tiles = objectMapper.createArrayNode();
                tiles.add(proxiedUrl);
                source.set("tiles", tiles);
            } else {
                ArrayNode tiles = objectMapper.createArrayNode();
                tiles.add(originalTileUrl);
                source.set("tiles", tiles);
            }
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

    private JsonNode buildVectorStyleJson(UserMapStyle style, boolean shouldProxy, Long styleId) throws IOException {
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

    private JsonNode finalizeStyle(ObjectNode style, Long styleId, boolean proxyEnabled) {

        if (proxyEnabled) {
            rewriteResourceUrls(style);
            rewriteTileUrlsInStyle(style, styleId);
        }

        return style;
    }

    private void rewriteResourceUrls(ObjectNode style) {
        if (style.get("glyphs") instanceof TextNode glyphsText && glyphsText.asText().startsWith("/")) {
            style.set("glyphs", new TextNode(contextPathHolder.getContextPath() + glyphsText.asText()));
        }
    }

    private void rewriteTileUrlsInStyle(ObjectNode style, Long styleId) {
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
                String originalUrl = sourceNode.get("url").asText("");
                if (originalUrl.startsWith("http://") || originalUrl.startsWith("https://")) {
                    // Store original TileJSON URL in cache
                    String tileJsonCacheKey = styleId + ":" + sourceId + ":tilejson";
                    originalTileJsonUrlCache.put(tileJsonCacheKey, originalUrl);
                    sourceNode.put("url", proxyTileJsonUrl(styleId, sourceId));
                }
            }

            // Rewrite "tiles" array
            if (sourceNode.has("tiles") && sourceNode.get("tiles") instanceof ArrayNode tiles) {
                ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                for (JsonNode tile : tiles) {
                    String tileUrl = tile.asText("");
                    if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                        // Store original tile URL in cache
                        String cacheKey = styleId + ":" + sourceId;
                        originalTileUrlCache.put(cacheKey, tileUrl);
                        rewrittenTiles.add(proxyTileUrl(styleId, sourceId, tileUrl));
                    } else {
                        rewrittenTiles.add(tileUrl);
                    }
                }
                sourceNode.set("tiles", rewrittenTiles);
            }
        }
    }

    private String proxyTileUrl(Long styleId, String sourceId, String originalUrl) {
        // originalUrl is already stored in cache by the caller (rewriteTileUrlsInStyle or buildRasterStyleJson)
        String ext = TileUrlUtils.extractTileExtension(originalUrl);
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/" + sourceId + "/{z}/{x}/{y}." + ext;
    }

    private String proxyTileJsonUrl(Long styleId, String sourceId) {
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

    private MapLibreStyleDefinition buildStyleDefinition(UserMapStyle style) {
        Long styleId = style.id();
        String contextPath = contextPathHolder.getContextPath();

        return new MapLibreStyleDefinition(
                styleId,
                style.name(),
                style.mapType(),
                "url",
                contextPath + "/api/v1/tiles/styles/" + styleId + "/style.json",
                detectCapabilities(style));
    }

    private JsonNode findSource(JsonNode style, String sourceId) {
        JsonNode sources = style.path("sources");
        if (sources instanceof ObjectNode sourcesObject) {
            return sourcesObject.get(sourceId);
        }
        return null;
    }

    private Map<String, Object> detectCapabilities(UserMapStyle style) {
        if ("raster".equals(style.mapType())) {
            return Map.of();
        }

        // Obtain the raw style JSON either from inline JSON or from style URL.
        JsonNode styleJson = getStyleJsonForDetection(style);
        if (!(styleJson instanceof ObjectNode root)) {
            return Map.of();
        }

        Map<String, Object> caps = new HashMap<>();

        // 1. Terrain source (raster-dem)
        JsonNode sources = root.path("sources");
        if (sources instanceof ObjectNode sourcesObj) {
            sourcesObj.properties().forEach(entry -> {
                JsonNode source = entry.getValue();
                if (source.isObject() && "raster-dem".equals(source.path("type").asText())) {
                    caps.put("terrainSourceId", entry.getKey());
                }
            });
        }

        // 2. Hillshade layer (type = "hillshade")
        JsonNode layers = root.path("layers");
        if (layers instanceof ArrayNode layerArray) {
            for (JsonNode layer : layerArray) {
                if ("hillshade".equals(layer.path("type").asText())) {
                    caps.put("hillshadeLayerId", layer.path("id").asText());
                    break;
                }
            }

            // 3. Satellite layer (raster source with known satellite patterns)
            String satelliteLayerId = detectSatelliteLayer(root, layerArray);
            if (satelliteLayerId != null) {
                caps.put("satelliteLayerId", satelliteLayerId);
            }

            // 4. Building 3D layers (fill-extrusion with building in source-layer or id)
            List<String> building3dIds = new ArrayList<>();
            for (JsonNode layer : layerArray) {
                if (!"fill-extrusion".equals(layer.path("type").asText())) {
                    continue;
                }
                String id = layer.path("id").asText("");
                String sourceLayer = layer.path("source-layer").asText("");
                if (id.toLowerCase().contains("building") || sourceLayer.toLowerCase().contains("building")) {
                    building3dIds.add(id);
                }
            }
            if (!building3dIds.isEmpty()) {
                caps.put("building3dLayerIds", building3dIds);
            }
        }

        return caps;
    }

    private String detectSatelliteLayer(ObjectNode style, ArrayNode layers) {
        ObjectNode sources = style.path("sources").isObject() ? (ObjectNode) style.path("sources") : null;
        if (sources == null) return null;

        for (JsonNode layer : layers) {
            if (!"raster".equals(layer.path("type").asText())) continue;
            String sourceId = layer.path("source").asText("");
            JsonNode source = sources.get(sourceId);
            if (source == null || !source.isObject()) continue;

            // Check source URL for known satellite imagery endpoints
            String sourceUrl = source.path("url").asText("");
            JsonNode tiles = source.get("tiles");
            String firstTileUrl = (tiles instanceof ArrayNode && !tiles.isEmpty()) ? tiles.get(0).asText("") : "";
            String combinedUrl = sourceUrl + " " + firstTileUrl;
            boolean isSatelliteUrl = combinedUrl.contains("arcgisonline")
                    || combinedUrl.contains("world_imagery")
                    || combinedUrl.contains("sentinel")
                    || combinedUrl.contains("planet");

            boolean nameContainsSatellite = layer.path("id").asText("").toLowerCase().contains("satellite")
                    || sourceId.toLowerCase().contains("satellite");

            if (isSatelliteUrl || nameContainsSatellite) {
                return layer.path("id").asText();
            }
        }
        return null;
    }

    private JsonNode getStyleJsonForDetection(UserMapStyle style) {
        try {
            if (style.styleJson() != null && !style.styleJson().isBlank()) {
                return objectMapper.readTree(style.styleJson());
            }
            if (style.styleUrl() != null && !style.styleUrl().isBlank()) {
                return fetchStyleJson(style.styleUrl());
            }
        } catch (Exception e) {
            log.warn("Could not parse style JSON for capability detection [{}]: {}", style.id(), e.getMessage());
        }
        return null;
    }
}
