package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.RemoteTileUrlValidator;
import com.dedicatedcode.reitti.service.SafeHttpClient;
import com.dedicatedcode.reitti.service.TileProxySignatureService;
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
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private final TileProxySignatureService tileProxySignatureService;
    private final RemoteTileUrlValidator remoteTileUrlValidator;
    private final SafeHttpClient safeHttpClient;
    private final HttpClient httpClient;
    private final boolean tileCacheEnabled;
    
    public MapStyleController(
            ObjectMapper objectMapper,
            ContextPathHolder contextPathHolder,
            UserSettingsJdbcService userSettingsJdbcService,
            UserMapStyleJdbcService userMapStyleJdbcService,
            TileProxySignatureService tileProxySignatureService,
            RemoteTileUrlValidator remoteTileUrlValidator,
            SafeHttpClient safeHttpClient,
            @Value("${reitti.ui.tiles.cache.url:}") String cacheUrl) {
        this.objectMapper = objectMapper;
        this.contextPathHolder = contextPathHolder;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.tileProxySignatureService = tileProxySignatureService;
        this.remoteTileUrlValidator = remoteTileUrlValidator;
        this.safeHttpClient = safeHttpClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.tileCacheEnabled = StringUtils.hasText(cacheUrl);
    }

    @GetMapping(value = "/reitti.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getStyle(@AuthenticationPrincipal User user, HttpServletRequest request) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/map/reitti.json");
        ClassPathResource coloredResource = new ClassPathResource("static/map/colored.json");

        JsonNode style;
        if (this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).isPreferColoredMap()) {
            style = objectMapper.readTree(coloredResource.getInputStream());
        } else {
            style = objectMapper.readTree(resource.getInputStream());
        }

        return buildStyleResponse(style, request);
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
            return buildStyleResponse(styleJson, request);
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

        HttpRequest request = HttpRequest.newBuilder(remoteTileUrlValidator.requirePublicHttpUrl(style.styleUrl(), "Vector style URL"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = safeHttpClient.sendFollowingPublicRedirects(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                "Vector style URL"
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unable to fetch map style: " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        if (json instanceof ObjectNode objectNode) {
            ObjectNode metadata = objectNode.has("metadata") && objectNode.get("metadata") instanceof ObjectNode existing
                    ? existing
                    : objectMapper.createObjectNode();
            metadata.put("reitti:style-url", style.styleUrl());
            objectNode.set("metadata", metadata);
        }
        return json;
    }

    private JsonNode buildRasterStyle(UserMapStyle style) {
        MapStyleDataSource dataSource = style.dataSource();
        ObjectNode styleJson = objectMapper.createObjectNode();
        styleJson.put("version", 8);
        styleJson.put("name", style.name());

        ObjectNode sources = objectMapper.createObjectNode();
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        if (StringUtils.hasText(dataSource.tileJsonUrl())) {
            source.put("url", dataSource.tileJsonUrl());
        } else if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(dataSource.tileUrlTemplate());
            source.set("tiles", tiles);
        }
        if (StringUtils.hasText(dataSource.attribution())) {
            source.put("attribution", dataSource.attribution());
        }
        source.put("minzoom", dataSource.minzoom() != null ? dataSource.minzoom() : 0);
        source.put("maxzoom", dataSource.maxzoom() != null ? dataSource.maxzoom() : 19);
        source.put("tileSize", effectiveRasterTileSize(dataSource));
        source.put("scheme", StringUtils.hasText(dataSource.scheme()) ? dataSource.scheme() : "xyz");
        sources.set("custom-raster-source", source);
        styleJson.set("sources", sources);

        ArrayNode layers = objectMapper.createArrayNode();
        ObjectNode rasterLayer = objectMapper.createObjectNode();
        rasterLayer.put("id", "custom-raster-layer");
        rasterLayer.put("type", "raster");
        rasterLayer.put("source", "custom-raster-source");
        layers.add(rasterLayer);
        styleJson.set("layers", layers);

        return styleJson;
    }

    private int effectiveRasterTileSize(MapStyleDataSource dataSource) {
        if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            String tileUrlTemplate = dataSource.tileUrlTemplate();
            if (tileUrlTemplate.contains("{r}") || tileUrlTemplate.contains("@2x")) {
                return 256;
            }
        }
        return dataSource.tileSize() != null ? dataSource.tileSize() : 256;
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
            ObjectNode metadata = mutableStyle.has("metadata") && mutableStyle.get("metadata") instanceof ObjectNode existing
                    ? existing
                    : objectMapper.createObjectNode();
            metadata.put("reitti:attribution-override", options.attributionOverride());
            mutableStyle.set("metadata", metadata);

            JsonNode sources = mutableStyle.get("sources");
            if (sources instanceof ObjectNode sourcesObject) {
                sourcesObject.fields().forEachRemaining(entry -> {
                    if (entry.getValue() instanceof ObjectNode source) {
                        source.put("attribution", options.attributionOverride());
                    }
                });
            }
        }
        return mutableStyle;
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
        if (StringUtils.hasText(dataSource.tileJsonUrl())) {
            source.put("url", dataSource.tileJsonUrl());
        }
        if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(dataSource.tileUrlTemplate());
            source.set("tiles", tiles);
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
        sources.set(dataSource.sourceId(), source);
        return mutableStyle;
    }

    private ResponseEntity<JsonNode> buildStyleResponse(JsonNode style, HttpServletRequest request) {
        style = ensureRuntimeSources(style, request);

        if (this.tileCacheEnabled) {
            style = rewriteUrlsForProxy(style, request);
        }

        if (!this.contextPathHolder.getContextPath().equals("/")) {
            style = rewriteResourceUrls(style, request);
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(style);
    }

    private JsonNode ensureRuntimeSources(JsonNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        ObjectNode mutableSources = ensureSourcesNode(mutableStyle);
        String baseUrl = getBaseUrl(request);

        if (!mutableSources.has(RUNTIME_TERRAIN_SOURCE)) {
            ObjectNode terrainSource = objectMapper.createObjectNode();
            terrainSource.put("type", "raster-dem");
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(this.tileCacheEnabled ? baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp" : TERRAIN_TILE_URL);
            terrainSource.set("tiles", tiles);
            terrainSource.put("tileSize", 256);
            terrainSource.put("encoding", "terrarium");
            terrainSource.put("maxzoom", 14);
            terrainSource.put("attribution", "© <a href='https://mapterhorn.com' target='_blank'>Mapterhorn</a>");
            mutableSources.set(RUNTIME_TERRAIN_SOURCE, terrainSource);
        }

        if (!mutableSources.has(RUNTIME_SATELLITE_SOURCE)) {
            ObjectNode satelliteSource = objectMapper.createObjectNode();
            satelliteSource.put("type", "raster");
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(this.tileCacheEnabled ? baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg" : SATELLITE_TILE_URL);
            satelliteSource.set("tiles", tiles);
            satelliteSource.put("tileSize", 256);
            satelliteSource.put("maxzoom", 18);
            satelliteSource.put("attribution", "Powered by <a href='https://www.esri.com' target='_blank'>Esri</a> | Sources: Esri, Maxar, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community");
            mutableSources.set(RUNTIME_SATELLITE_SOURCE, satelliteSource);
        }

        if (!styleHasBuildingLayer(mutableStyle) && !mutableSources.has(RUNTIME_BUILDING_SOURCE)) {
            ObjectNode buildingSource = objectMapper.createObjectNode();
            buildingSource.put("type", "vector");
            buildingSource.put("url", VECTOR_TILEJSON_URL);
            buildingSource.put("minzoom", 0);
            buildingSource.put("maxzoom", 14);
            buildingSource.put("attribution", "© <a href='https://openfreemap.org' target='_blank'>OpenFreeMap</a> © <a href='https://www.openstreetmap.org/copyright' target='_blank'>OSM</a>");
            mutableSources.set(RUNTIME_BUILDING_SOURCE, buildingSource);
        }

        return mutableStyle;
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

    private JsonNode rewriteResourceUrls(JsonNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        JsonNode glyphs = mutableStyle.get("glyphs");
        if (glyphs instanceof TextNode glyphsText && glyphsText.asText().startsWith("/")) {
            mutableStyle.set("glyphs", new TextNode(this.contextPathHolder.getContextPath() + glyphsText.asText()));
        }
        return mutableStyle;
    }

    private JsonNode rewriteUrlsForProxy(JsonNode style, HttpServletRequest request) {
        ObjectNode mutableStyle = style.deepCopy();
        String baseUrl = getBaseUrl(request);
        
        JsonNode sources = mutableStyle.get("sources");
        if (sources instanceof ObjectNode) {
            ObjectNode mutableSources = (ObjectNode) sources;
            URI styleBaseUri = getStyleBaseUri(mutableStyle).orElse(null);
            
            mutableSources.fields().forEachRemaining(entry -> {
                if (entry.getValue() instanceof ObjectNode source) {
                    String sourceType = source.path("type").asText();
                    if ("vector".equals(sourceType)) {
                        rewriteVectorSource(source, baseUrl, styleBaseUri);
                    } else if ("raster".equals(sourceType)) {
                        rewriteCustomRasterSource(source, baseUrl, styleBaseUri);
                    }
                }
            });

            rewriteRasterSource(mutableSources, "terrain-source", baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp");
            rewriteRasterSource(mutableSources, "satellite-source", baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg");
            rewriteRasterSource(mutableSources, RUNTIME_TERRAIN_SOURCE, baseUrl + "/api/v1/tiles/terrain/{z}/{x}/{y}.webp");
            rewriteRasterSource(mutableSources, RUNTIME_SATELLITE_SOURCE, baseUrl + "/api/v1/tiles/satellite/{z}/{x}/{y}.jpg");
        }
        
        return mutableStyle;
    }

    private void rewriteVectorSource(ObjectNode source, String baseUrl, URI styleBaseUri) {
        String sourceUrl = source.path("url").asText("");
        String firstTileUrl = "";
        JsonNode tiles = source.get("tiles");
        if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            firstTileUrl = tileArray.get(0).asText("");
        }

        if (sourceUrl.contains("tiles.dedicatedcode.com") || firstTileUrl.contains("tiles.dedicatedcode.com")) {
            source.remove("url");
            ArrayNode rewrittenTiles = objectMapper.createArrayNode();
            rewrittenTiles.add(baseUrl + "/api/v1/tiles/vector/{z}/{x}/{y}.pbf");
            source.set("tiles", rewrittenTiles);
            return;
        }

        if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            if (!remoteTileUrlValidator.isServerFetchAllowedUrl(sourceUrl)) {
                return;
            }
            String encodedSourceUrl = encodeTileTemplate(sourceUrl);
            source.set("url", new TextNode(baseUrl + "/api/v1/tiles/custom/tilejson/" + encodedSourceUrl + "/" + tileProxySignatureService.sign(encodedSourceUrl) + ".json"));
            return;
        }

        if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            ArrayNode rewrittenTiles = objectMapper.createArrayNode();
            tileArray.forEach(tile -> {
                String tileUrl = tile.asText("");
                if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                    rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(tileUrl) ? customTileUrl(baseUrl, tileUrl) : tileUrl);
                } else if (styleBaseUri != null && containsTilePlaceholders(tileUrl)) {
                    String resolvedTileUrl = styleBaseUri.resolve(tileUrl).toString();
                    rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(resolvedTileUrl) ? customTileUrl(baseUrl, resolvedTileUrl) : tileUrl);
                } else {
                    rewrittenTiles.add(tileUrl);
                }
            });
            source.set("tiles", rewrittenTiles);
        }
    }

    private void rewriteCustomRasterSource(ObjectNode source, String baseUrl, URI styleBaseUri) {
        String sourceUrl = source.path("url").asText("");
        if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            if (!remoteTileUrlValidator.isServerFetchAllowedUrl(sourceUrl)) {
                return;
            }
            String encodedSourceUrl = encodeTileTemplate(sourceUrl);
            source.set("url", new TextNode(baseUrl + "/api/v1/tiles/custom/tilejson/" + encodedSourceUrl + "/" + tileProxySignatureService.sign(encodedSourceUrl) + ".json"));
            return;
        }

        JsonNode tiles = source.get("tiles");
        if (tiles instanceof ArrayNode tileArray && !tileArray.isEmpty()) {
            ArrayNode rewrittenTiles = objectMapper.createArrayNode();
            tileArray.forEach(tile -> {
                String tileUrl = tile.asText("");
                if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                    rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(tileUrl) ? customTileUrl(baseUrl, tileUrl) : tileUrl);
                } else if (styleBaseUri != null && containsTilePlaceholders(tileUrl)) {
                    String resolvedTileUrl = styleBaseUri.resolve(tileUrl).toString();
                    rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(resolvedTileUrl) ? customTileUrl(baseUrl, resolvedTileUrl) : tileUrl);
                } else {
                    rewrittenTiles.add(tileUrl);
                }
            });
            source.set("tiles", rewrittenTiles);
        }
    }

    private String customTileUrl(String baseUrl, String tileUrl) {
        String normalizedTileUrl = normalizeTileTemplateForProxy(tileUrl);
        String encodedTileUrl = encodeTileTemplate(normalizedTileUrl);
        return baseUrl + "/api/v1/tiles/custom/" + encodedTileUrl + "/" + tileProxySignatureService.sign(encodedTileUrl) + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private String normalizeTileTemplateForProxy(String tileUrl) {
        return tileUrl.replace("{r}", "");
    }

    private String encodeTileTemplate(String tileUrl) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tileUrl.getBytes(StandardCharsets.UTF_8));
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

    private void rewriteRasterSource(ObjectNode sources, String sourceName, String tileUrl) {
        if (sources.has(sourceName) && sources.get(sourceName) instanceof ObjectNode source) {
            if ("satellite-source".equals(sourceName) || RUNTIME_SATELLITE_SOURCE.equals(sourceName)) {
                source.put("type", "raster");
            }
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(tileUrl);
            source.set("tiles", tiles);
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        return url.toString();
    }
}
