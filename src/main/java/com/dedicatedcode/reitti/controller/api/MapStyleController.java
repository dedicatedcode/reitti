package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.MapStylePathUtils;
import com.dedicatedcode.reitti.service.MapStyleUrlValidator;
import com.dedicatedcode.reitti.service.RequestHelper;
import com.dedicatedcode.reitti.service.TileUrlUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/map")
public class MapStyleController {

    private static final String VECTOR_TILEJSON_URL = "https://tiles.dedicatedcode.com/planet";
    private static final String TERRAIN_TILE_URL = "https://tiles.mapterhorn.com/{z}/{x}/{y}.webp";
    private static final String SATELLITE_TILE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
    private static final String RUNTIME_TERRAIN_SOURCE = "reitti-terrain-source";
    private static final String RUNTIME_SATELLITE_SOURCE = "reitti-satellite-source";
    private static final String RUNTIME_BUILDING_SOURCE = "reitti-building-source";
    private static final String TERRAIN_PROXY_PATH = "/api/v1/tiles/terrain/{z}/{x}/{y}.webp";
    private static final String SATELLITE_PROXY_PATH = "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg";

    private final ObjectMapper objectMapper;
    private final ContextPathHolder contextPathHolder;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final MapStyleUrlValidator mapStyleUrlValidator;
    private final HttpClient httpClient;
    private final boolean tileCacheEnabled;

    public MapStyleController(
            ObjectMapper objectMapper,
            ContextPathHolder contextPathHolder,
            UserSettingsJdbcService userSettingsJdbcService,
            UserMapStyleJdbcService userMapStyleJdbcService,
            MapStyleUrlValidator mapStyleUrlValidator,
            @Value("${reitti.ui.tiles.cache.url:}") String cacheUrl) {
        this.objectMapper = objectMapper;
        this.contextPathHolder = contextPathHolder;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.mapStyleUrlValidator = mapStyleUrlValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.tileCacheEnabled = StringUtils.hasText(cacheUrl);
    }

    @GetMapping(value = "/reitti.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getStyle(@AuthenticationPrincipal User user, HttpServletRequest request) throws IOException {
        boolean preferColored = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).isPreferColoredMap();
        String stylePath = preferColored ? "static/map/colored.json" : "static/map/reitti.json";
        JsonNode style = objectMapper.readTree(new ClassPathResource(stylePath).getInputStream());
        return buildStyleResponse(style, request, "reitti", true);
    }

    @GetMapping(value = "/custom/{id}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getUserCustomStyle(@AuthenticationPrincipal User user, @PathVariable long id, HttpServletRequest request) throws IOException, InterruptedException {
        Optional<UserMapStyle> style = userMapStyleJdbcService.findById(user, id);
        if (style.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            JsonNode styleJson = readUserStyle(style.get());
            styleJson = applyVectorOptions(styleJson, style.get().vectorOptions());
            styleJson = applyCustomDataSource(styleJson, style.get().dataSource());
            return buildStyleResponse(styleJson, request, style.get().frontendId(), shouldProxyTiles(style.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private JsonNode readUserStyle(UserMapStyle style) throws IOException, InterruptedException {
        if ("raster".equals(style.mapType())) {
            return buildRasterStyle(style);
        }
        if (StringUtils.hasText(style.styleJson())) {
            return objectMapper.readTree(style.styleJson());
        }
        return fetchRemoteStyle(style);
    }

    private JsonNode fetchRemoteStyle(UserMapStyle style) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(mapStyleUrlValidator.requireHttpUrl(style.styleUrl(), "Vector style URL"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unable to fetch map style: " + response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        if (json instanceof ObjectNode objectNode) {
            getOrCreateMetadata(objectNode).put("reitti:style-url", style.styleUrl());
        }
        return json;
    }

    private JsonNode buildRasterStyle(UserMapStyle style) {
        MapStyleDataSource dataSource = style.dataSource();

        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        populateDataSourceFields(source, dataSource);
        source.put("tileSize", effectiveRasterTileSize(dataSource));
        source.put("scheme", StringUtils.hasText(dataSource.scheme()) ? dataSource.scheme() : "xyz");

        String rasterSourceId = StringUtils.hasText(dataSource.sourceId()) ? dataSource.sourceId() : "raster";

        ObjectNode rasterLayer = objectMapper.createObjectNode();
        rasterLayer.put("id", "custom-raster-layer");
        rasterLayer.put("type", "raster");
        rasterLayer.put("source", rasterSourceId);

        ObjectNode styleJson = objectMapper.createObjectNode();
        styleJson.put("version", 8);
        styleJson.put("name", style.name());
        styleJson.set("sources", objectMapper.createObjectNode().set(rasterSourceId, source));
        styleJson.set("layers", objectMapper.createArrayNode().add(rasterLayer));
        return styleJson;
    }

    private int effectiveRasterTileSize(MapStyleDataSource dataSource) {
        String tileUrlTemplate = dataSource.tileUrlTemplate();
        if (StringUtils.hasText(tileUrlTemplate) && (tileUrlTemplate.contains("{r}") || tileUrlTemplate.contains("@2x"))) {
            return 256;
        }
        return dataSource.tileSize() != null ? dataSource.tileSize() : 256;
    }

    private void populateDataSourceFields(ObjectNode source, MapStyleDataSource dataSource) {
        if (StringUtils.hasText(dataSource.tileJsonUrl())) {
            source.put("url", dataSource.tileJsonUrl());
        }
        if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            source.set("tiles", singleTileArray(dataSource.tileUrlTemplate()));
        }
        if (StringUtils.hasText(dataSource.attribution())) {
            source.put("attribution", dataSource.attribution());
        }
        if (dataSource.minzoom() != null) {
            source.put("minzoom", dataSource.minzoom());
        }
        if (dataSource.maxzoom() != null) {
            source.put("maxzoom", dataSource.maxzoom());
        }
        if (dataSource.tileSize() != null) {
            source.put("tileSize", dataSource.tileSize());
        }
        if (StringUtils.hasText(dataSource.scheme())) {
            source.put("scheme", dataSource.scheme());
        }
    }

    private JsonNode applyVectorOptions(JsonNode style, MapStyleVectorOptions options) {
        if (options == null) {
            return style;
        }
        ObjectNode mutableStyle = style.deepCopy();
        if (StringUtils.hasText(options.glyphsUrlOverride())) {
            mutableStyle.put("glyphs", options.glyphsUrlOverride());
        }
        if (StringUtils.hasText(options.spriteUrlOverride())) {
            mutableStyle.put("sprite", options.spriteUrlOverride());
        }
        if (StringUtils.hasText(options.attributionOverride())) {
            applyAttributionOverride(mutableStyle, options.attributionOverride());
        }
        return mutableStyle;
    }

    private void applyAttributionOverride(ObjectNode mutableStyle, String attribution) {
        getOrCreateMetadata(mutableStyle).put("reitti:attribution-override", attribution);

        if (mutableStyle.get("sources") instanceof ObjectNode sourcesObject) {
            sourcesObject.fields().forEachRemaining(entry -> {
                if (entry.getValue() instanceof ObjectNode source) {
                    source.put("attribution", attribution);
                }
            });
        }
    }

    private JsonNode applyCustomDataSource(JsonNode style, MapStyleDataSource dataSource) {
        if (dataSource == null || !StringUtils.hasText(dataSource.sourceId())) {
            return style;
        }
        if (!StringUtils.hasText(dataSource.tileJsonUrl()) && !StringUtils.hasText(dataSource.tileUrlTemplate())) {
            return style;
        }
        ObjectNode mutableStyle = style.deepCopy();
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", StringUtils.hasText(dataSource.type()) ? dataSource.type() : "vector");
        populateDataSourceFields(source, dataSource);
        if ("raster".equals(dataSource.type())) {
            source.put("tileSize", effectiveRasterTileSize(dataSource));
            source.put("scheme", StringUtils.hasText(dataSource.scheme()) ? dataSource.scheme() : "xyz");
        }
        ensureSourcesNode(mutableStyle).set(dataSource.sourceId(), source);
        return mutableStyle;
    }

    private ResponseEntity<JsonNode> buildStyleResponse(JsonNode style, HttpServletRequest request, String styleId, boolean proxyCustomTiles) {
        style = ensureRuntimeSources(style, request);
        if (proxyCustomTiles) {
            style = rewriteUrlsForProxy(style, request, styleId);
        }
        if (!contextPathHolder.getContextPath().equals("/")) {
            style = rewriteResourceUrls(style);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(style);
    }

    private JsonNode ensureRuntimeSources(JsonNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        ObjectNode sources = ensureSourcesNode(mutableStyle);
        String baseUrl = RequestHelper.getBaseUrl(request);

        if (!sources.has(RUNTIME_TERRAIN_SOURCE)) {
            sources.set(RUNTIME_TERRAIN_SOURCE, buildTerrainSource(tileCacheEnabled ? baseUrl + TERRAIN_PROXY_PATH : TERRAIN_TILE_URL));
        }
        if (!sources.has(RUNTIME_SATELLITE_SOURCE)) {
            sources.set(RUNTIME_SATELLITE_SOURCE, buildSatelliteSource(tileCacheEnabled ? baseUrl + SATELLITE_PROXY_PATH : SATELLITE_TILE_URL));
        }
        if (!styleHasBuildingLayer(mutableStyle) && !sources.has(RUNTIME_BUILDING_SOURCE)) {
            sources.set(RUNTIME_BUILDING_SOURCE, buildBuildingSource());
        }

        return mutableStyle;
    }

    private ObjectNode buildTerrainSource(String tileUrl) {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster-dem");
        source.set("tiles", singleTileArray(tileUrl));
        source.put("tileSize", 256);
        source.put("encoding", "terrarium");
        source.put("maxzoom", 14);
        source.put("attribution", "© <a href='https://mapterhorn.com' target='_blank'>Mapterhorn</a>");
        return source;
    }

    private ObjectNode buildSatelliteSource(String tileUrl) {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        source.set("tiles", singleTileArray(tileUrl));
        source.put("tileSize", 256);
        source.put("maxzoom", 18);
        source.put("attribution", "Powered by <a href='https://www.esri.com' target='_blank'>Esri</a> | Sources: Esri, Maxar, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community");
        return source;
    }

    private ObjectNode buildBuildingSource() {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "vector");
        source.put("url", VECTOR_TILEJSON_URL);
        source.put("minzoom", 0);
        source.put("maxzoom", 14);
        source.put("attribution", "© <a href='https://openfreemap.org' target='_blank'>OpenFreeMap</a> © <a href='https://www.openstreetmap.org/copyright' target='_blank'>OSM</a>");
        return source;
    }

    private boolean styleHasBuildingLayer(ObjectNode style) {
        if (!(style.get("layers") instanceof ArrayNode layerArray)) {
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

    private JsonNode rewriteResourceUrls(JsonNode style) {
        ObjectNode mutableStyle = style.deepCopy();
        if (mutableStyle.get("glyphs") instanceof TextNode glyphsText && glyphsText.asText().startsWith("/")) {
            mutableStyle.set("glyphs", new TextNode(contextPathHolder.getContextPath() + glyphsText.asText()));
        }
        return mutableStyle;
    }

    private JsonNode rewriteUrlsForProxy(JsonNode style, HttpServletRequest request, String styleId) {
        ObjectNode mutableStyle = style.deepCopy();
        if (!(mutableStyle.get("sources") instanceof ObjectNode mutableSources)) {
            return mutableStyle;
        }
        String baseUrl = RequestHelper.getBaseUrl(request);
        URI styleBaseUri = getStyleBaseUri(mutableStyle).orElse(null);
        List<String> sourceIds = new ArrayList<>();
        mutableSources.fieldNames().forEachRemaining(sourceIds::add);

        Map<String, String> reservedRasterTiles = reservedRasterTileUrls(baseUrl);
        mutableSources.fields().forEachRemaining(entry -> {
            if (!(entry.getValue() instanceof ObjectNode source)) {
                return;
            }
            String reservedTileUrl = reservedRasterTiles.get(entry.getKey());
            if (reservedTileUrl != null) {
                if (RUNTIME_SATELLITE_SOURCE.equals(entry.getKey()) || "satellite-source".equals(entry.getKey())) {
                    source.put("type", "raster");
                }
                source.set("tiles", singleTileArray(reservedTileUrl));
                return;
            }
            rewriteTileSource(source, baseUrl, styleId, entry.getKey(), sourceIds, styleBaseUri);
        });
        return mutableStyle;
    }

    private Map<String, String> reservedRasterTileUrls(String baseUrl) {
        Map<String, String> reserved = new LinkedHashMap<>();
        reserved.put("terrain-source", baseUrl + TERRAIN_PROXY_PATH);
        reserved.put("satellite-source", baseUrl + SATELLITE_PROXY_PATH);
        reserved.put(RUNTIME_TERRAIN_SOURCE, baseUrl + TERRAIN_PROXY_PATH);
        reserved.put(RUNTIME_SATELLITE_SOURCE, baseUrl + SATELLITE_PROXY_PATH);
        return reserved;
    }

    private void rewriteTileSource(ObjectNode source, String baseUrl, String styleId, String sourceId, List<String> allSourceIds, URI styleBaseUri) {
        String sourceUrl = source.path("url").asText("");
        String firstTileUrl = getFirstTileUrl(source);

        if (sourceUrl.contains("tiles.dedicatedcode.com") || firstTileUrl.contains("tiles.dedicatedcode.com")) {
            source.remove("url");
            source.set("tiles", singleTileArray(baseUrl + "/api/v1/tiles/vector/{z}/{x}/{y}.pbf"));
            return;
        }
        if (isHttpUrl(sourceUrl)) {
            source.set("url", new TextNode(styleSourceTileJsonUrl(baseUrl, styleId, sourceId, allSourceIds)));
            return;
        }
        rewriteTileTemplates(source, baseUrl, styleId, sourceId, allSourceIds, styleBaseUri);
    }

    private void rewriteTileTemplates(ObjectNode source, String baseUrl, String styleId, String sourceId, List<String> allSourceIds, URI styleBaseUri) {
        if (!(source.get("tiles") instanceof ArrayNode tileArray) || tileArray.isEmpty()) {
            return;
        }
        String firstTileUrl = tileArray.get(0).asText("");
        ArrayNode rewrittenTiles = objectMapper.createArrayNode();
        if (isHttpUrl(firstTileUrl)) {
            rewrittenTiles.add(styleSourceTileUrl(baseUrl, styleId, sourceId, allSourceIds, firstTileUrl));
        } else if (styleBaseUri != null && containsTilePlaceholders(firstTileUrl)) {
            rewrittenTiles.add(styleSourceTileUrl(baseUrl, styleId, sourceId, allSourceIds, styleBaseUri.resolve(firstTileUrl).toString()));
        } else {
            rewrittenTiles.add(firstTileUrl);
        }
        source.set("tiles", rewrittenTiles);
    }

    private String styleSourceTileJsonUrl(String baseUrl, String styleId, String sourceId, List<String> allSourceIds) {
        return baseUrl + "/api/v1/tiles/styles/" + styleId + "/" + MapStylePathUtils.sourcePathId(sourceId, allSourceIds) + "/tilejson.json";
    }

    private String styleSourceTileUrl(String baseUrl, String styleId, String sourceId, List<String> allSourceIds, String tileUrl) {
        String normalizedTileUrl = tileUrl.replace("{r}", "");
        return baseUrl + "/api/v1/tiles/styles/" + styleId + "/" + MapStylePathUtils.sourcePathId(sourceId, allSourceIds)
                + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private ArrayNode singleTileArray(String tileUrl) {
        ArrayNode tiles = objectMapper.createArrayNode();
        tiles.add(tileUrl);
        return tiles;
    }

    private String getFirstTileUrl(ObjectNode source) {
        if (source.get("tiles") instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            return tileArray.get(0).asText("");
        }
        return "";
    }

    private ObjectNode ensureSourcesNode(ObjectNode mutableStyle) {
        if (mutableStyle.get("sources") instanceof ObjectNode sourcesObject) {
            return sourcesObject;
        }
        ObjectNode sourcesObject = objectMapper.createObjectNode();
        mutableStyle.set("sources", sourcesObject);
        return sourcesObject;
    }

    private ObjectNode getOrCreateMetadata(ObjectNode node) {
        if (node.get("metadata") instanceof ObjectNode existing) {
            return existing;
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        node.set("metadata", metadata);
        return metadata;
    }

    private Optional<URI> getStyleBaseUri(ObjectNode style) {
        if (!(style.get("metadata") instanceof ObjectNode metadataObject)) {
            return Optional.empty();
        }
        String styleUrl = metadataObject.path("reitti:style-url").asText("");
        if (!StringUtils.hasText(styleUrl)) {
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(styleUrl));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean isHttpUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private static boolean containsTilePlaceholders(String tileUrl) {
        return tileUrl.contains("{z}") && tileUrl.contains("{x}") && tileUrl.contains("{y}");
    }

    private static boolean shouldProxyTiles(UserMapStyle style) {
        return style.dataSource() != null && style.dataSource().proxyTiles();
    }
}
