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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
            ObjectNode metadata = getOrCreateMetadata(objectNode);
            metadata.put("reitti:style-url", style.styleUrl());
            objectNode.set("metadata", metadata);
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

        ObjectNode sources = objectMapper.createObjectNode();
        sources.set("custom-raster-source", source);

        ObjectNode rasterLayer = objectMapper.createObjectNode();
        rasterLayer.put("id", "custom-raster-layer");
        rasterLayer.put("type", "raster");
        rasterLayer.put("source", "custom-raster-source");

        ArrayNode layers = objectMapper.createArrayNode();
        layers.add(rasterLayer);

        ObjectNode styleJson = objectMapper.createObjectNode();
        styleJson.put("version", 8);
        styleJson.put("name", style.name());
        styleJson.set("sources", sources);
        styleJson.set("layers", layers);
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
        ObjectNode metadata = getOrCreateMetadata(mutableStyle);
        metadata.put("reitti:attribution-override", attribution);
        mutableStyle.set("metadata", metadata);

        JsonNode sources = mutableStyle.get("sources");
        if (sources instanceof ObjectNode sourcesObject) {
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
        ObjectNode sources = ensureSourcesNode(mutableStyle);
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", StringUtils.hasText(dataSource.type()) ? dataSource.type() : "vector");
        populateDataSourceFields(source, dataSource);
        sources.set(dataSource.sourceId(), source);
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
            String tileUrl = tileCacheEnabled ? baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp" : TERRAIN_TILE_URL;
            sources.set(RUNTIME_TERRAIN_SOURCE, buildTerrainSource(tileUrl));
        }
        if (!sources.has(RUNTIME_SATELLITE_SOURCE)) {
            String tileUrl = tileCacheEnabled ? baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg" : SATELLITE_TILE_URL;
            sources.set(RUNTIME_SATELLITE_SOURCE, buildSatelliteSource(tileUrl));
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

    private JsonNode rewriteResourceUrls(JsonNode style) {
        ObjectNode mutableStyle = style.deepCopy();
        JsonNode glyphs = mutableStyle.get("glyphs");
        if (glyphs instanceof TextNode glyphsText && glyphsText.asText().startsWith("/")) {
            mutableStyle.set("glyphs", new TextNode(contextPathHolder.getContextPath() + glyphsText.asText()));
        }
        return mutableStyle;
    }

    private JsonNode rewriteUrlsForProxy(JsonNode style, HttpServletRequest request, String styleId) {
        ObjectNode mutableStyle = style.deepCopy();
        String baseUrl = RequestHelper.getBaseUrl(request);
        JsonNode sources = mutableStyle.get("sources");
        if (!(sources instanceof ObjectNode mutableSources)) {
            return mutableStyle;
        }
        URI styleBaseUri = getStyleBaseUri(mutableStyle).orElse(null);
        mutableSources.fields().forEachRemaining(entry -> {
            if (entry.getValue() instanceof ObjectNode source) {
                rewriteTileSource(source, baseUrl, styleId, entry.getKey(), styleBaseUri);
            }
        });
        rewriteRasterSource(mutableSources, "terrain-source", baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp");
        rewriteRasterSource(mutableSources, "satellite-source", baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg");
        rewriteRasterSource(mutableSources, RUNTIME_TERRAIN_SOURCE, baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp");
        rewriteRasterSource(mutableSources, RUNTIME_SATELLITE_SOURCE, baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg");
        return mutableStyle;
    }

    private void rewriteTileSource(ObjectNode source, String baseUrl, String styleId, String sourceId, URI styleBaseUri) {
        String sourceUrl = source.path("url").asText("");
        String firstTileUrl = getFirstTileUrl(source);

        if (sourceUrl.contains("tiles.dedicatedcode.com") || firstTileUrl.contains("tiles.dedicatedcode.com")) {
            source.remove("url");
            source.set("tiles", singleTileArray(baseUrl + "/api/v1/tiles/vector/{z}/{x}/{y}.pbf"));
            return;
        }
        if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            source.set("url", new TextNode(styleSourceTileJsonUrl(baseUrl, styleId, sourceId)));
            return;
        }
        rewriteTileTemplates(source, baseUrl, styleId, sourceId, styleBaseUri);
    }

    private void rewriteTileTemplates(ObjectNode source, String baseUrl, String styleId, String sourceId, URI styleBaseUri) {
        JsonNode tiles = source.get("tiles");
        if (!(tiles instanceof ArrayNode tileArray) || tileArray.isEmpty()) {
            return;
        }
        ArrayNode rewrittenTiles = objectMapper.createArrayNode();
        for (int i = 0; i < tileArray.size(); i++) {
            String tileUrl = tileArray.get(i).asText("");
            if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                rewrittenTiles.add(styleSourceTileUrl(baseUrl, styleId, sourceId, i, tileUrl));
            } else if (styleBaseUri != null && containsTilePlaceholders(tileUrl)) {
                rewrittenTiles.add(styleSourceTileUrl(baseUrl, styleId, sourceId, i, styleBaseUri.resolve(tileUrl).toString()));
            } else {
                rewrittenTiles.add(tileUrl);
            }
        }
        source.set("tiles", rewrittenTiles);
    }

    private void rewriteRasterSource(ObjectNode sources, String sourceName, String tileUrl) {
        if (!(sources.get(sourceName) instanceof ObjectNode source)) {
            return;
        }
        if ("satellite-source".equals(sourceName) || RUNTIME_SATELLITE_SOURCE.equals(sourceName)) {
            source.put("type", "raster");
        }
        source.set("tiles", singleTileArray(tileUrl));
    }

    private String styleSourceTileJsonUrl(String baseUrl, String styleId, String sourceId) {
        return baseUrl + "/api/v1/tiles/styles/" + styleId + "/sources/" + MapStylePathUtils.sourcePathId(sourceId) + "/tilejson.json";
    }

    private String styleSourceTileUrl(String baseUrl, String styleId, String sourceId, int tileIndex, String tileUrl) {
        String normalizedTileUrl = tileUrl.replace("{r}", "");
        return baseUrl + "/api/v1/tiles/styles/" + styleId + "/sources/" + MapStylePathUtils.sourcePathId(sourceId)
                + "/tiles/" + tileIndex + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private ArrayNode singleTileArray(String tileUrl) {
        ArrayNode tiles = objectMapper.createArrayNode();
        tiles.add(tileUrl);
        return tiles;
    }

    private String getFirstTileUrl(ObjectNode source) {
        JsonNode tiles = source.get("tiles");
        if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            return tileArray.get(0).asText("");
        }
        return "";
    }

    private ObjectNode ensureSourcesNode(ObjectNode mutableStyle) {
        JsonNode sources = mutableStyle.get("sources");
        if (sources instanceof ObjectNode sourcesObject) {
            return sourcesObject;
        }
        ObjectNode sourcesObject = objectMapper.createObjectNode();
        mutableStyle.set("sources", sourcesObject);
        return sourcesObject;
    }

    private ObjectNode getOrCreateMetadata(ObjectNode node) {
        return node.has("metadata") && node.get("metadata") instanceof ObjectNode existing
                ? existing
                : objectMapper.createObjectNode();
    }

    private Optional<URI> getStyleBaseUri(ObjectNode style) {
        JsonNode metadata = style.get("metadata");
        if (!(metadata instanceof ObjectNode metadataObject)) {
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

    private boolean containsTilePlaceholders(String tileUrl) {
        return tileUrl.contains("{z}") && tileUrl.contains("{x}") && tileUrl.contains("{y}");
    }

    private boolean shouldProxyTiles(UserMapStyle style) {
        return style.dataSource() != null && style.dataSource().proxyTiles();
    }

}