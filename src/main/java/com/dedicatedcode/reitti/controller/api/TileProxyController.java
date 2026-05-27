package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import com.dedicatedcode.reitti.service.TileUrlUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@RestController
@RequestMapping("/api/v1/tiles")
public class TileProxyController {
    private static final Logger log = LoggerFactory.getLogger(TileProxyController.class);
    private static final String CUSTOM_UPSTREAM_HEADER = "X-Reitti-Upstream-Url";

    private final HttpClient httpClient;
    private final String tileCacheUrl;
    private final boolean tileCacheEnabled;
    private final ObjectMapper objectMapper;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final MapLibreMapStylesService mapLibreMapStylesService;
    private final ContextPathHolder contextPathHolder;

    // Maps source names to internal paths and coordinate ordering
    private record SourceConfig(String path, boolean swapXY, String contentType) {}
    private record TileSource(String tileJsonUrl, List<String> tileUrlTemplates, boolean proxyTiles) {}

    private static final Map<String, SourceConfig> SOURCES = Map.of(
        "raster", new SourceConfig("/osm/", false, MediaType.IMAGE_PNG_VALUE),
        "osm", new SourceConfig("/osm/", false, "application/x-protobuf"),
        "vector", new SourceConfig("/vector/", false, "application/x-protobuf"),
        "terrain", new SourceConfig("/terrain/", false, "image/webp"),
        "satellite", new SourceConfig("/satellite/", true, "image/jpeg")
    );

    public TileProxyController(
            @Value("${reitti.ui.tiles.cache.url:}") String tileCacheUrl,
            ObjectMapper objectMapper,
            UserMapStyleJdbcService userMapStyleJdbcService,
            MapLibreMapStylesService mapLibreMapStylesService,
            ContextPathHolder contextPathHolder) {
        this.tileCacheUrl = tileCacheUrl;
        this.tileCacheEnabled = StringUtils.hasText(tileCacheUrl);
        this.objectMapper = objectMapper;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.mapLibreMapStylesService = mapLibreMapStylesService;
        this.contextPathHolder = contextPathHolder;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Serves the complete MapLibre style JSON (with runtime sources and optionally proxied URLs).
     */
    @GetMapping("/styles/{styleId}/style.json")
    public ResponseEntity<JsonNode> getStyleJson(
            @AuthenticationPrincipal User user,
            @PathVariable Long styleId) {

        try {
            JsonNode styleJson = mapLibreMapStylesService.getCompleteStyleJson(styleId, user);
            if (styleJson == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .body(styleJson);
        } catch (Exception e) {
            log.warn("Failed to serve style JSON [{}]: {}", styleId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/styles/{styleId}/{sourceId}/tilejson.json")
    public ResponseEntity<JsonNode> getStyleSourceTileJson(
            @AuthenticationPrincipal User user,
            @PathVariable Long styleId,
            @PathVariable String sourceId) {

        try {
            boolean proxyTiles = isProxyTilesEnabled(user, styleId);
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId, proxyTiles);
            if (source.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            String tileJsonUrl = source.get().tileJsonUrl();
            if (!StringUtils.hasText(tileJsonUrl)) {
                return ResponseEntity.notFound().build();
            }
            if (!source.get().proxyTiles()) {
                return ResponseEntity.notFound().build();
            }
            URI tileJsonUri = URI.create(tileJsonUrl);

            HttpResponse<byte[]> response = fetchRaw(tileJsonUrl, Map.of());
            if (response.statusCode() != 200) {
                log.debug("Failed to fetch custom TileJSON [{}]: HTTP {}", tileJsonUri, response.statusCode());
                return ResponseEntity.notFound().build();
            }

            JsonNode tileJson = objectMapper.readTree(responseBody(response));
            if (tileJson instanceof ObjectNode mutableTileJson && mutableTileJson.get("tiles") instanceof ArrayNode tiles && !tiles.isEmpty()) {
                ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                String firstTileUrl = tiles.get(0).asText("");
                if (firstTileUrl.startsWith("http://") || firstTileUrl.startsWith("https://")) {
                    rewrittenTiles.add(styleSourceTileUrl(styleId, sourceId, firstTileUrl));
                } else if (!firstTileUrl.isBlank()) {
                    String resolvedTileUrl = tileJsonUri.resolve(firstTileUrl).toString();
                    rewrittenTiles.add(styleSourceTileUrl(styleId, sourceId, resolvedTileUrl));
                } else {
                    rewrittenTiles.add(firstTileUrl);
                }
                mutableTileJson.set("tiles", rewrittenTiles);
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .body(tileJson);
        } catch (Exception e) {
            log.warn("Failed to fetch custom TileJSON [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/styles/{styleId}/{sourceId}/{z}/{x}/{y}.{ext}")
    public ResponseEntity<byte[]> getStyleSourceTile(
            @AuthenticationPrincipal User user,
            @PathVariable Long styleId,
            @PathVariable String sourceId,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable String ext) {

        try {
            boolean proxyTiles = isProxyTilesEnabled(user, styleId);
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId, proxyTiles);
            if (source.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            String template = tileTemplate(source.get());
            if (!StringUtils.hasText(template)) {
                return ResponseEntity.notFound().build();
            }
            if (!source.get().proxyTiles()) {
                return ResponseEntity.notFound().build();
            }
            String upstreamTileUrl = template
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{r}", "");

            URI upstreamTileUri = URI.create(upstreamTileUrl);
            log.trace("Fetching custom tile [{}/{}]: {}", styleId, sourceId, upstreamTileUri);

            if (this.tileCacheEnabled) {
                String tileUrl = tileCacheUrl + "/custom/";
                return fetchTile(tileUrl, contentTypeForExtension(ext), "custom", Map.of(CUSTOM_UPSTREAM_HEADER, upstreamTileUrl));
            }

            return fetchTile(upstreamTileUrl, contentTypeForExtension(ext), "custom");
        } catch (IllegalArgumentException e) {
            log.warn("Failed to resolve custom tile [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.warn("Failed to fetch custom tile [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }


    private boolean isProxyTilesEnabled(User user, Long styleId) {
        try {
            Optional<UserMapStyle> style = userMapStyleJdbcService.findById(user, styleId);
            if (style.isPresent()) {
                MapStyleDataSource source = style.get().dataSource();
                if (source != null) {
                    return source.proxyTiles();
                }
            }
        } catch (NumberFormatException ignored) {}
        return true;
    }

    private ResponseEntity<byte[]> fetchTile(String tileUrl, String contentType, String source) {
        return fetchTile(tileUrl, contentType, source, Map.of());
    }

    private ResponseEntity<byte[]> fetchTile(String tileUrl, String contentType, String source, Map<String, String> requestHeaders) {
        try {
            HttpResponse<byte[]> response = fetchRaw(tileUrl, requestHeaders);

            if (response.statusCode() == 200) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(contentType));
                headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
                headers.add("Access-Control-Allow-Origin", "*");
                response.headers()
                        .firstValue(HttpHeaders.CONTENT_ENCODING)
                        .ifPresent(contentEncoding -> headers.add(HttpHeaders.CONTENT_ENCODING, contentEncoding));

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(response.body());
            } else {
                log.debug("Failed to fetch tile from {}: HTTP {}", source, response.statusCode());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch tile from {}: {}", source, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private HttpResponse<byte[]> fetchRaw(String url, Map<String, String> extraHeaders) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .GET();
        extraHeaders.forEach(requestBuilder::header);
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private byte[] responseBody(HttpResponse<byte[]> response) throws IOException {
        String contentEncoding = response.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse("");
        if (contentEncoding.equalsIgnoreCase("gzip")) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
                return gzipInputStream.readAllBytes();
            }
        }
        if (contentEncoding.equalsIgnoreCase("deflate")) {
            try (InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(response.body()))) {
                return inflaterInputStream.readAllBytes();
            }
        }
        return response.body();
    }

    private Optional<TileSource> resolveTileSource(User user, Long styleId, String sourceId, boolean proxyTiles) {
        String originalTileUrl = mapLibreMapStylesService.getOriginalTileUrl(styleId, sourceId, user);
        if (originalTileUrl != null) {
            List<String> templates = new ArrayList<>();
            if (originalTileUrl.endsWith(".json")) {
                return Optional.of(new TileSource(originalTileUrl, List.of(), proxyTiles));
            } else {
                templates.add(normalizeTileTemplateForProxy(originalTileUrl));
                return Optional.of(new TileSource(null, templates, proxyTiles));
            }
        }
        throw new IllegalArgumentException("No original tile URL found for style " + styleId + " and source " + sourceId);
    }

    private String tileTemplate(TileSource source) throws Exception {
        if (!source.tileUrlTemplates().isEmpty()) {
            return source.tileUrlTemplates().getFirst();
        }

        if (!StringUtils.hasText(source.tileJsonUrl())) {
            return null;
        }

        String tileJsonUrl = source.tileJsonUrl();
        URI tileJsonUri = URI.create(tileJsonUrl);

        HttpResponse<byte[]> response = fetchRaw(tileJsonUrl, Map.of());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch TileJSON: " + response.statusCode());
        }

        JsonNode tileJson = objectMapper.readTree(responseBody(response));

        JsonNode tiles = tileJson.get("tiles");
        if (!(tiles instanceof ArrayNode tileArray) || tileArray.isEmpty()) {
            return null;
        }

        String tileUrl = tileArray.get(0).asText("");
        if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
            return normalizeTileTemplateForProxy(tileUrl);
        }
        if (StringUtils.hasText(tileUrl)) {
            return normalizeTileTemplateForProxy(tileJsonUri.resolve(tileUrl).toString());
        }
        return null;
    }

    private String styleSourceTileUrl(Long styleId, String sourceId, String tileUrl) {
        String normalizedTileUrl = normalizeTileTemplateForProxy(tileUrl);
        return contextPathHolder.getContextPath() + "/api/v1/tiles/styles/" + styleId + "/" + sourceId
                + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private String normalizeTileTemplateForProxy(String tileUrl) {
        return tileUrl.replace("{r}", "");
    }

    private String contentTypeForExtension(String ext) {
        return switch (ext.toLowerCase()) {
            case "pbf", "mvt" -> "application/x-protobuf";
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
