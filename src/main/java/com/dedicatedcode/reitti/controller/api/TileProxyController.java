package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import com.dedicatedcode.reitti.service.RequestHelper;
import com.dedicatedcode.reitti.service.TileUrlUtils;
import com.dedicatedcode.reitti.service.security.OutboundHttp;
import com.dedicatedcode.reitti.service.security.OutboundUrlValidator;
import com.dedicatedcode.reitti.service.security.UnsafeUrlException;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final String defaultTileService;
    private final String customTileService;
    private final String panoramaxBaseUrl;
    private final ObjectMapper objectMapper;
    private final UserMapStyleJdbcService userMapStyleJdbcService;
    private final MapLibreMapStylesService mapLibreMapStylesService;
    private final OutboundUrlValidator outboundUrlValidator;

    private record TileSource(String tileJsonUrl, List<String> tileUrlTemplates) {}

    public TileProxyController(
            @Value("${reitti.ui.tiles.cache.url:}") String tileCacheUrl,
            @Value("${reitti.ui.tiles.default.service}") String defaultTileService,
            @Value("${reitti.ui.tiles.custom.service:}") String customTileService,
            @Value("${reitti.panoramax.base-url:}") String panoramaxBaseUrl,
            ObjectMapper objectMapper,
            UserMapStyleJdbcService userMapStyleJdbcService,
            MapLibreMapStylesService mapLibreMapStylesService,
            OutboundUrlValidator outboundUrlValidator) {
        this.tileCacheUrl = tileCacheUrl;
        this.tileCacheEnabled = StringUtils.hasText(tileCacheUrl);
        this.defaultTileService = defaultTileService;
        this.customTileService = customTileService;
        this.panoramaxBaseUrl = panoramaxBaseUrl;
        this.objectMapper = objectMapper;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.mapLibreMapStylesService = mapLibreMapStylesService;
        this.outboundUrlValidator = outboundUrlValidator;
        this.httpClient = OutboundHttp.newHttpClient();
    }

    @GetMapping("/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getTileLegacy(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {

        String template = StringUtils.hasText(customTileService) ? customTileService : defaultTileService;
        if (!StringUtils.hasText(template)) {
            return ResponseEntity.notFound().build();
        }
        String upstreamTileUrl = template
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));
        URI upstreamTileUri = URI.create(upstreamTileUrl);
        log.trace("Fetching custom tile: {}", upstreamTileUri);

        if (this.tileCacheEnabled) {
            String tileUrl = tileCacheUrl + "/custom/";
            return fetchTile(tileUrl, MediaType.IMAGE_PNG_VALUE, "custom", Map.of(CUSTOM_UPSTREAM_HEADER, upstreamTileUrl), null);
        } else {
            return fetchTile(upstreamTileUrl, MediaType.IMAGE_PNG_VALUE, "custom", Map.of(), null);
        }
    }

    @GetMapping("/panoramax/{z}/{x}/{y}.mvt")
    public ResponseEntity<byte[]> getPanoramaxTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        if (!StringUtils.hasText(panoramaxBaseUrl)) {
            return ResponseEntity.notFound().build();
        }
        String upstreamTileUrl = panoramaxBaseUrl + "/map/" + z + "/" + x + "/" + y + ".mvt";
        log.trace("Fetching Panoramax coverage tile: {}", upstreamTileUrl);

        if (this.tileCacheEnabled) {
            String cachePath = URI.create(upstreamTileUrl).getRawPath();
            String tileUrl = tileCacheUrl + "/panoramax/tiles" + cachePath;
            return fetchTile(tileUrl, "application/x-protobuf", "panoramax", Map.of(CUSTOM_UPSTREAM_HEADER, upstreamTileUrl), null);
        } else {
            return fetchTile(upstreamTileUrl, "application/x-protobuf", "panoramax", Map.of(), null);
        }
    }

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
            @PathVariable String sourceId,
            HttpServletRequest request) {

        try {
            if (!isProxyTilesEnabled(user, styleId)) {
                return ResponseEntity.notFound().build();
            }
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId);
            if (source.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            TileSource tileSource = source.get();

            // Case 1: We have a TileJSON URL – fetch it and rewrite the tiles array
            if (tileSource.tileJsonUrl() != null && !tileSource.tileJsonUrl().isBlank()) {
                URI tileJsonUri = outboundUrlValidator.validate(tileSource.tileJsonUrl());

                OutboundHttp.Response response = fetchRaw(tileJsonUri, Map.of(), OutboundHttp.MAX_JSON_BYTES, outboundUrlValidator);
                if (response.statusCode() != 200) {
                    log.debug("Failed to fetch custom TileJSON [{}]: HTTP {}", tileJsonUri, response.statusCode());
                    return ResponseEntity.notFound().build();
                }

                JsonNode tileJson = objectMapper.readTree(responseBody(response));
                if (tileJson instanceof ObjectNode mutableTileJson && mutableTileJson.get("tiles") instanceof ArrayNode tiles && !tiles.isEmpty()) {
                    ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                    for (JsonNode tileNode : tiles) {
                        String tileUrl = tileNode.asString("");
                        if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                            rewrittenTiles.add(styleSourceTileUrl(styleId, sourceId, tileUrl, request));
                        } else if (!tileUrl.isBlank()) {
                            String resolvedTileUrl = tileJsonUri.resolve(tileUrl).toString();
                            rewrittenTiles.add(styleSourceTileUrl(styleId, sourceId, resolvedTileUrl, request));
                        } else {
                            rewrittenTiles.add(tileUrl);
                        }
                    }
                    mutableTileJson.set("tiles", rewrittenTiles);
                }

                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .body(tileJson);
            }

            // Case 2: We have a tile URL template directly – build a TileJSON on the fly
            if (!tileSource.tileUrlTemplates().isEmpty()) {
                String template = tileSource.tileUrlTemplates().getFirst();
                ObjectNode tileJson = objectMapper.createObjectNode();
                tileJson.put("tilejson", "2.2.0");
                tileJson.put("name", "Custom Tiles");
                ArrayNode tiles = objectMapper.createArrayNode();
                tiles.add(styleSourceTileUrl(styleId, sourceId, template, request));
                tileJson.set("tiles", tiles);
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .body(tileJson);
            }

            // No valid source configuration found
            return ResponseEntity.notFound().build();
        } catch (UnsafeUrlException e) {
            log.warn("Refusing to fetch custom TileJSON [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.notFound().build();
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
            if (!isProxyTilesEnabled(user, styleId)) {
                return ResponseEntity.notFound().build();
            }
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId);
            if (source.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            String template = tileTemplate(source.get());
            if (!StringUtils.hasText(template)) {
                return ResponseEntity.notFound().build();
            }
            String upstreamTileUrl = template
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{r}", "");

            URI upstreamTileUri = outboundUrlValidator.validate(upstreamTileUrl);
            log.trace("Fetching custom tile [{}/{}]: {}", styleId, sourceId, upstreamTileUri);

            if (this.tileCacheEnabled) {
                String tileUrl = tileCacheUrl + "/custom/";
                return fetchTile(tileUrl, contentTypeForExtension(ext), "custom", Map.of(CUSTOM_UPSTREAM_HEADER, upstreamTileUri.toString()), outboundUrlValidator);
            } else {
                return fetchTile(upstreamTileUri.toString(), contentTypeForExtension(ext), "custom", Map.of(), outboundUrlValidator);
            }
        } catch (UnsafeUrlException e) {
            log.warn("Refusing to fetch custom tile [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to resolve custom tile [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.warn("Failed to fetch custom tile [{}/{}]: {}", styleId, sourceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }


    /**
     * Only styles the user may access (own or shared) with tile proxying enabled are proxied. Fails closed if the
     * style does not exist.
     */
    private boolean isProxyTilesEnabled(User user, Long styleId) {
        if (user == null || styleId == null) {
            return false;
        }
        Optional<UserMapStyle> style = userMapStyleJdbcService.findById(user, styleId);
        MapStyleDataSource source = style.map(UserMapStyle::dataSource).orElse(null);
        return source != null && source.proxyTiles();
    }

    /**
     * @param redirectValidator validates redirect targets of user supplied upstreams, {@code null} for upstreams
     *                          configured by the administrator
     */
    private ResponseEntity<byte[]> fetchTile(String tileUrl, String contentType, String source, Map<String, String> requestHeaders, OutboundUrlValidator redirectValidator) {
        try {
            OutboundHttp.Response response = fetchRaw(URI.create(tileUrl), requestHeaders, OutboundHttp.MAX_TILE_BYTES, redirectValidator);

            if (response.statusCode() == 200) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(contentType));
                headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
                headers.add("Access-Control-Allow-Origin", "*");
                headers.add("X-Content-Type-Options", "nosniff");
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

    private OutboundHttp.Response fetchRaw(URI uri, Map<String, String> extraHeaders, long maxBytes, OutboundUrlValidator redirectValidator) throws Exception {
        Map<String, String> headers = new HashMap<>(extraHeaders);
        headers.put("User-Agent", "Reitti/1.0 (+https://github.com/dedicatedcode/reitti; contact: reitti@dedicatedcode.com)");
        headers.put(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
        return OutboundHttp.get(httpClient, uri, headers, Duration.ofSeconds(30), maxBytes, OutboundHttp.MAX_REDIRECTS, redirectValidator);
    }

    private byte[] responseBody(OutboundHttp.Response response) throws IOException {
        String contentEncoding = response.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse("");
        if (contentEncoding.equalsIgnoreCase("gzip")) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
                return OutboundHttp.readAtMost(gzipInputStream, OutboundHttp.MAX_JSON_BYTES);
            }
        }
        if (contentEncoding.equalsIgnoreCase("deflate")) {
            try (InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(response.body()))) {
                return OutboundHttp.readAtMost(inflaterInputStream, OutboundHttp.MAX_JSON_BYTES);
            }
        }
        return response.body();
    }

    private Optional<TileSource> resolveTileSource(User user, Long styleId, String sourceId) {
        // First, try to get original TileJSON URL (upstream tilejson)
        String tileJsonUrl = mapLibreMapStylesService.getOriginalTileJsonUrl(styleId, sourceId, user);
        if (tileJsonUrl != null && !tileJsonUrl.isBlank()) {
            return Optional.of(new TileSource(tileJsonUrl, List.of()));
        }
        // Fallback to tile URL template
        String originalTileUrl = mapLibreMapStylesService.getOriginalTileUrl(styleId, sourceId, user);
        if (originalTileUrl != null) {
            List<String> templates = new ArrayList<>();
            templates.add(normalizeTileTemplateForProxy(originalTileUrl));
            return Optional.of(new TileSource(null, templates));
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

        URI tileJsonUri = outboundUrlValidator.validate(source.tileJsonUrl());

        OutboundHttp.Response response = fetchRaw(tileJsonUri, Map.of(), OutboundHttp.MAX_JSON_BYTES, outboundUrlValidator);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch TileJSON: " + response.statusCode());
        }

        JsonNode tileJson = objectMapper.readTree(responseBody(response));

        JsonNode tiles = tileJson.get("tiles");
        if (!(tiles instanceof ArrayNode tileArray) || tileArray.isEmpty()) {
            return null;
        }

        String tileUrl = tileArray.get(0).asString("");
        if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
            return normalizeTileTemplateForProxy(tileUrl);
        }
        if (StringUtils.hasText(tileUrl)) {
            return normalizeTileTemplateForProxy(tileJsonUri.resolve(tileUrl).toString());
        }
        return null;
    }

    private String styleSourceTileUrl(Long styleId, String sourceId, String tileUrl, HttpServletRequest request) {
        String normalizedTileUrl = normalizeTileTemplateForProxy(tileUrl);
        return RequestHelper.getBaseUrl(request) + "/api/v1/tiles/styles/" + styleId + "/" + sourceId
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
