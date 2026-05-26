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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
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
private final boolean tileCachingEnabled;
private final ObjectMapper objectMapper;
private final HttpClient httpClient;
    private final ContextPathHolder contextPathHolder;

    public MapLibreMapStylesService(
        @Value("${reitti.ui.tiles.cache.url:}") String cacheUrl,
        UserMapStyleJdbcService userMapStyleJdbcService,
        ObjectMapper objectMapper,
        ContextPathHolder contextPathHolder) {
    this.userMapStyleJdbcService = userMapStyleJdbcService;
    this.tileCachingEnabled = StringUtils.hasText(cacheUrl);
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.contextPathHolder = contextPathHolder;
    }

    @Cacheable("mapStyles")
    public List<MapLibreStyleDefinition> getConfig(User user) {
        List<UserMapStyle> all = this.userMapStyleJdbcService.findAll(user);
        List<MapLibreStyleDefinition> definitions = new ArrayList<>();
        for (UserMapStyle style : all) {
            try {
                MapLibreStyleDefinition def = buildStyleDefinition(style);
                if (def != null) {
                    definitions.add(def);
                }
            } catch (Exception e) {
                log.warn("Failed to build style definition for style [{}]: {}", style.id(), e.getMessage());
            }
        }
        return definitions;
    }

    private MapLibreStyleDefinition buildStyleDefinition(UserMapStyle style) {
        String styleId = String.valueOf(style.id());
        MapStyleDataSource dataSource = style.dataSource();
        boolean shouldBeProxied = this.tileCachingEnabled && dataSource != null && dataSource.proxyTiles();

        JsonNode styleJson = buildCompleteStyleJson(style, shouldBeProxied, styleId);
        if (styleJson == null) {
            return null;
        }

        return new MapLibreStyleDefinition(
            styleId,
            style.name(),
            style.mapType(),
            "json",
            styleJson,
            buildCapabilities(style)
        );
    }

    private JsonNode buildCompleteStyleJson(UserMapStyle style, boolean shouldBeProxied, String styleId) {
        if ("vector".equals(style.mapType())) {
            return buildVectorStyleJson(style, shouldBeProxied, styleId);
        } else if ("raster".equals(style.mapType())) {
            return buildRasterStyleJson(style, shouldBeProxied, styleId);
        }
        log.warn("Unsupported map type: {}", style.mapType());
        return null;
    }

    private JsonNode buildVectorStyleJson(UserMapStyle style, boolean shouldBeProxied, String styleId) {
        JsonNode styleNode;

        if (StringUtils.hasText(style.styleJson())) {
            try {
                styleNode = objectMapper.readTree(style.styleJson());
            } catch (IOException e) {
                log.warn("Failed to parse style JSON for style [{}]: {}", styleId, e.getMessage());
                return null;
            }
        } else if (StringUtils.hasText(style.styleUrl())) {
            try {
                styleNode = fetchStyleJson(style.styleUrl());
            } catch (Exception e) {
                log.warn("Failed to fetch style JSON from [{}]: {}", style.styleUrl(), e.getMessage());
                return null;
            }
        } else {
            log.warn("Vector style [{}] has no styleJson or styleUrl", styleId);
            return null;
        }

        if (shouldBeProxied && styleNode instanceof ObjectNode) {
            rewriteTileUrlsInStyle((ObjectNode) styleNode, styleId);
        }

        return styleNode;
    }

    private JsonNode buildRasterStyleJson(UserMapStyle style, boolean shouldBeProxied, String styleId) {
        MapStyleDataSource dataSource = style.dataSource();
        if (dataSource == null) {
            log.warn("Raster style [{}] has no data source", styleId);
            return null;
        }

        ObjectNode rasterStyle = objectMapper.createObjectNode();
        rasterStyle.put("version", 8);
        rasterStyle.put("name", style.name());

        ObjectNode sources = objectMapper.createObjectNode();
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "raster");
        source.put("tileSize", dataSource.tileSize() != null ? dataSource.tileSize() : 256);
        if (StringUtils.hasText(dataSource.attribution())) {
            source.put("attribution", dataSource.attribution());
        }

        String sourceId = dataSource.sourceId() != null ? dataSource.sourceId() : "raster-tiles";

        if (StringUtils.hasText(dataSource.tileJsonUrl())) {
            String tileJsonUrl = dataSource.tileJsonUrl();
            if (shouldBeProxied) {
                tileJsonUrl = proxyTileJsonUrl(styleId, sourceId);
            }
            source.put("url", tileJsonUrl);
        } else if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            String tileUrl = dataSource.tileUrlTemplate();
            if (shouldBeProxied) {
                tileUrl = proxyTileUrl(styleId, sourceId, tileUrl);
            }
            ArrayNode tiles = objectMapper.createArrayNode();
            tiles.add(tileUrl);
            source.set("tiles", tiles);
        } else {
            log.warn("Raster style [{}] has no tileJsonUrl or tileUrlTemplate", styleId);
            return null;
        }

        if (dataSource.minzoom() != null) {
            source.put("minzoom", dataSource.minzoom());
        }
        if (dataSource.maxzoom() != null) {
            source.put("maxzoom", dataSource.maxzoom());
        }

        sources.set(sourceId, source);
        rasterStyle.set("sources", sources);

        ObjectNode layer = objectMapper.createObjectNode();
        layer.put("id", "raster-layer");
        layer.put("type", "raster");
        layer.put("source", sourceId);
        if (dataSource.minzoom() != null) {
            layer.put("minzoom", dataSource.minzoom());
        }
        if (dataSource.maxzoom() != null) {
            layer.put("maxzoom", dataSource.maxzoom());
        }

        ArrayNode layers = objectMapper.createArrayNode();
        layers.add(layer);
        rasterStyle.set("layers", layers);

        return rasterStyle;
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
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/"
                + MapStylePathUtils.sourcePathId(sourceId) + "/{z}/{x}/{y}." + ext;
    }

    private String proxyTileJsonUrl(String styleId, String sourceId) {
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/"
                + MapStylePathUtils.sourcePathId(sourceId) + "/tilejson.json";
    }

    private JsonNode fetchStyleJson(String styleUrl) throws IOException, InterruptedException {
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
}