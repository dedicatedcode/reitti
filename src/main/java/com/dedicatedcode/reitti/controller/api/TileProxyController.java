package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.MapStylePathUtils;
import com.dedicatedcode.reitti.service.MapStyleUrlValidator;
import com.dedicatedcode.reitti.service.TileUrlUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
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
import java.nio.charset.StandardCharsets;
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
    private final MapStyleUrlValidator mapStyleUrlValidator;
    private final Cache<String, JsonNode> styleJsonCache;

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

    private static final Map<String, String> SOURCE_UPSTREAM_URLS = Map.of(
        "raster", "https://tile.openstreetmap.org/",
        "osm", "https://tile.openstreetmap.org/",
        "vector", "https://tiles.dedicatedcode.com/planet/latest/",
        "terrain", "https://tiles.mapterhorn.com/",
        "satellite", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
    );

    public TileProxyController(
            @Value("${reitti.ui.tiles.cache.url:}") String tileCacheUrl,
            ObjectMapper objectMapper,
            UserMapStyleJdbcService userMapStyleJdbcService,
            MapStyleUrlValidator mapStyleUrlValidator) {
        this.tileCacheUrl = tileCacheUrl;
        this.tileCacheEnabled = StringUtils.hasText(tileCacheUrl);
        this.objectMapper = objectMapper;
        this.userMapStyleJdbcService = userMapStyleJdbcService;
        this.mapStyleUrlValidator = mapStyleUrlValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.styleJsonCache = Caffeine.newBuilder()
                .maximumSize(20)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    @GetMapping("/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getTileLegacy(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            HttpServletRequest request) {
        return getTile("raster", z, x, y, "png", request);
    }

    @GetMapping("/styles/{styleId}/sources/{sourceId}/tilejson.json")
    public ResponseEntity<JsonNode> getStyleSourceTileJson(
            @AuthenticationPrincipal User user,
            @PathVariable String styleId,
            @PathVariable String sourceId,
            HttpServletRequest request) {

        try {
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId);
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
            URI tileJsonUri = mapStyleUrlValidator.requireHttpUrl(tileJsonUrl, "Custom TileJSON URL");

            HttpResponse<byte[]> response = fetchUrl(tileJsonUrl);
            if (response.statusCode() != 200) {
                log.debug("Failed to fetch custom TileJSON [{}]: HTTP {}", tileJsonUri, response.statusCode());
                return ResponseEntity.notFound().build();
            }

            JsonNode tileJson = objectMapper.readTree(responseBody(response));
            if (tileJson instanceof ObjectNode mutableTileJson && mutableTileJson.get("tiles") instanceof ArrayNode tiles) {
                ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                for (int i = 0; i < tiles.size(); i++) {
                    JsonNode tile = tiles.get(i);
                    String tileUrl = tile.asText("");
                    if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                        rewrittenTiles.add(styleSourceTileUrl(request, styleId, sourceId, i, tileUrl));
                    } else if (!tileUrl.isBlank()) {
                        String resolvedTileUrl = tileJsonUri.resolve(tileUrl).toString();
                        rewrittenTiles.add(styleSourceTileUrl(request, styleId, sourceId, i, resolvedTileUrl));
                    } else {
                        rewrittenTiles.add(tileUrl);
                    }
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

    @GetMapping("/styles/{styleId}/sources/{sourceId}/tiles/{tileIndex}/{z}/{x}/{y}.{ext}")
    public ResponseEntity<byte[]> getStyleSourceTile(
            @AuthenticationPrincipal User user,
            @PathVariable String styleId,
            @PathVariable String sourceId,
            @PathVariable int tileIndex,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable String ext) {

        try {
            Optional<TileSource> source = resolveTileSource(user, styleId, sourceId);
            if (source.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            String template = tileTemplate(source.get(), tileIndex);
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

            URI upstreamTileUri = mapStyleUrlValidator.requireHttpUrl(upstreamTileUrl, "Custom tile URL");
            log.trace("Fetching custom tile [{}/{}]: {}", styleId, sourceId, upstreamTileUri);

            if (this.tileCacheEnabled) {
                String tileUrl = tileCacheUrl + "/custom-vector/";
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

    @GetMapping("/{source}/{z}/{x}/{y}.{ext}")
    public ResponseEntity<byte[]> getTile(
            @PathVariable String source,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable String ext,
            HttpServletRequest request) {

        SourceConfig config = SOURCES.get(source);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            String coordPath = config.swapXY()
                ? String.format("%d/%d/%d", z, y, x)
                : String.format("%d/%d/%d.%s", z, x, y, ext);

            String tileUrl;
            if (this.tileCacheEnabled) {
                tileUrl = tileCacheUrl + config.path() + coordPath;
            } else {
                String upstreamBaseUrl = SOURCE_UPSTREAM_URLS.get(source);
                if (!StringUtils.hasText(upstreamBaseUrl)) {
                    return ResponseEntity.notFound().build();
                }
                tileUrl = upstreamBaseUrl + coordPath;
            }
            if (StringUtils.hasText(request.getQueryString())) {
                tileUrl += "?" + request.getQueryString();
            }
            log.trace("Fetching tile [{}]: {}", source, coordPath);

            return fetchTile(tileUrl, config.contentType(), source);

        } catch (Exception e) {
            log.warn("Failed to fetch tile {}/{}/{} from {}: {}", x, y, z, source, e.getMessage());
            return ResponseEntity.notFound().build();
        }
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

    private HttpResponse<byte[]> fetchRaw(String url, Map<String, String> headers) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .GET();
        headers.forEach(requestBuilder::header);

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpResponse<byte[]> fetchUrl(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
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

    private Optional<TileSource> resolveTileSource(User user, String styleId, String sourceId) throws IOException, InterruptedException {
        if (!StringUtils.hasText(styleId) || !StringUtils.hasText(sourceId)) {
            return Optional.empty();
        }

        if ("reitti".equals(styleId)) {
            return sourceFromStyle(readClasspathStyle("static/map/reitti.json"), sourceId, null, true);
        }

        Optional<Long> customStyleId = UserMapStyleJdbcService.resolveCustomId(styleId);
        if (customStyleId.isEmpty() || user == null) {
            return Optional.empty();
        }

        Optional<UserMapStyle> style = userMapStyleJdbcService.findById(user, customStyleId.get());
        if (style.isEmpty()) {
            return Optional.empty();
        }

        Optional<TileSource> dataSource = sourceFromDataSource(style.get(), sourceId);
        if (dataSource.isPresent()) {
            return dataSource;
        }

        return sourceFromUserStyle(style.get(), sourceId);
    }

    private Optional<TileSource> sourceFromDataSource(UserMapStyle style, String sourceId) {
        MapStyleDataSource dataSource = style.dataSource();
        if (dataSource == null || !MapStylePathUtils.matchesSourcePathId(sourceId, dataSource.sourceId())) {
            return Optional.empty();
        }

        List<String> tileTemplates = new ArrayList<>();
        if (StringUtils.hasText(dataSource.tileUrlTemplate())) {
            tileTemplates.add(normalizeTileTemplateForProxy(dataSource.tileUrlTemplate()));
        }
        return Optional.of(new TileSource(dataSource.tileJsonUrl(), tileTemplates, dataSource.proxyTiles()));
    }

    private JsonNode parseUserStyleJson(UserMapStyle style) throws IOException {
        String cacheKey = "local:" + style.id() + ":" + style.version();
        try {
            return styleJsonCache.get(cacheKey, k -> {
                try {
                    return objectMapper.readTree(style.styleJson());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Optional<TileSource> sourceFromUserStyle(UserMapStyle style, String sourceId) throws IOException, InterruptedException {
        if (!"vector".equals(style.mapType())) {
            return Optional.empty();
        }

        if (StringUtils.hasText(style.styleJson())) {
            return sourceFromStyle(parseUserStyleJson(style), sourceId, null, shouldProxyTiles(style));
        }

        if (!StringUtils.hasText(style.styleUrl())) {
            return Optional.empty();
        }

        return sourceFromStyle(fetchAndParseStyleJson(style), sourceId, URI.create(style.styleUrl()), shouldProxyTiles(style));
    }

    private Optional<TileSource> sourceFromStyle(JsonNode style, String sourceId, URI styleBaseUri, boolean proxyTiles) {
        JsonNode source = findSource(style, sourceId);
        if (source == null) {
            return Optional.empty();
        }

        String tileJsonUrl = resolveSourceUrl(source.path("url").asText(""), styleBaseUri);
        List<String> tileTemplates = new ArrayList<>();
        JsonNode tiles = source.get("tiles");
        if (tiles instanceof ArrayNode tileArray) {
            tileArray.forEach(tile -> {
                String tileUrl = resolveTileTemplate(tile.asText(""), styleBaseUri);
                if (StringUtils.hasText(tileUrl)) {
                    tileTemplates.add(normalizeTileTemplateForProxy(tileUrl));
                }
            });
        }
        return Optional.of(new TileSource(tileJsonUrl, tileTemplates, proxyTiles));
    }

    private JsonNode findSource(JsonNode style, String sourceId) {
        JsonNode sources = style.path("sources");
        if (!(sources instanceof ObjectNode sourcesObject)) {
            return null;
        }
        JsonNode exactSource = sourcesObject.get(sourceId);
        if (exactSource instanceof ObjectNode) {
            return exactSource;
        }
        var fields = sourcesObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue() instanceof ObjectNode && MapStylePathUtils.matchesSourcePathId(sourceId, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveSourceUrl(String sourceUrl, URI styleBaseUri) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            return sourceUrl;
        }
        if (styleBaseUri != null) {
            return styleBaseUri.resolve(sourceUrl).toString();
        }
        return null;
    }

    private String resolveTileTemplate(String tileUrl, URI styleBaseUri) {
        if (!StringUtils.hasText(tileUrl)) {
            return null;
        }
        if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
            return tileUrl;
        }
        if (styleBaseUri != null && containsTilePlaceholders(tileUrl)) {
            return styleBaseUri.resolve(tileUrl).toString();
        }
        return null;
    }

    private String tileTemplate(TileSource source, int tileIndex) throws Exception {
        if (tileIndex >= 0 && tileIndex < source.tileUrlTemplates().size()) {
            return source.tileUrlTemplates().get(tileIndex);
        }

        if (!StringUtils.hasText(source.tileJsonUrl())) {
            return null;
        }

        String tileJsonUrl = source.tileJsonUrl();
        URI tileJsonUri = mapStyleUrlValidator.requireHttpUrl(tileJsonUrl, "Custom TileJSON URL");

        JsonNode tileJson;
        try {
            tileJson = styleJsonCache.get("tilejson:" + tileJsonUrl, k -> {
                try {
                    HttpResponse<byte[]> response = fetchUrl(tileJsonUrl);
                    if (response.statusCode() != 200) {
                        throw new IOException("Failed to fetch TileJSON: " + response.statusCode());
                    }
                    return objectMapper.readTree(responseBody(response));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new Exception("Failed to fetch custom TileJSON", e.getCause());
        }

        JsonNode tiles = tileJson.get("tiles");
        if (!(tiles instanceof ArrayNode tileArray) || tileIndex < 0 || tileIndex >= tileArray.size()) {
            return null;
        }

        String tileUrl = tileArray.get(tileIndex).asText("");
        if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
            return normalizeTileTemplateForProxy(tileUrl);
        }
        if (StringUtils.hasText(tileUrl)) {
            return normalizeTileTemplateForProxy(tileJsonUri.resolve(tileUrl).toString());
        }
        return null;
    }

    private JsonNode fetchAndParseStyleJson(UserMapStyle style) throws IOException, InterruptedException {
        String styleUrl = style.styleUrl();
        String cacheKey = "url:" + style.id() + ":" + style.version() + ":" + styleUrl;
        
        try {
            return styleJsonCache.get(cacheKey, k -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder(mapStyleUrlValidator.requireHttpUrl(styleUrl, "Vector style URL"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IOException("Unable to fetch map style: " + response.statusCode());
                    }

                    return objectMapper.readTree(response.body());
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioEx) {
                throw ioEx;
            }
            if (e.getCause() instanceof InterruptedException intEx) {
                throw intEx;
            }
            throw e;
        }
    }

    private JsonNode readClasspathStyle(String path) throws IOException {
        String cacheKey = "classpath:" + path;
        try {
            return styleJsonCache.get(cacheKey, k -> {
                try {
                    return objectMapper.readTree(new ClassPathResource(path).getInputStream());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private boolean containsTilePlaceholders(String tileUrl) {
        return tileUrl.contains("{z}") && tileUrl.contains("{x}") && tileUrl.contains("{y}");
    }

    private boolean shouldProxyTiles(UserMapStyle style) {
        return style.dataSource() != null && style.dataSource().proxyTiles();
    }

    private String styleSourceTileUrl(HttpServletRequest request, String styleId, String sourceId, int tileIndex, String tileUrl) {
        String normalizedTileUrl = normalizeTileTemplateForProxy(tileUrl);
        return getBaseUrl(request) + "/api/v1/tiles/styles/" + styleId + "/sources/" + MapStylePathUtils.sourcePathId(sourceId)
                + "/tiles/" + tileIndex + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private String normalizeTileTemplateForProxy(String tileUrl) {
        return tileUrl.replace("{r}", "");
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
