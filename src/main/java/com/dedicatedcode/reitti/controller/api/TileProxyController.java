package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.ConditionalOnPropertyNotEmpty;
import com.dedicatedcode.reitti.service.RemoteTileUrlValidator;
import com.dedicatedcode.reitti.service.SafeHttpClient;
import com.dedicatedcode.reitti.service.TileProxySignatureService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@RestController
@RequestMapping("/api/v1/tiles")
@ConditionalOnPropertyNotEmpty("reitti.ui.tiles.cache.url")
public class TileProxyController {
    private static final Logger log = LoggerFactory.getLogger(TileProxyController.class);
    private static final String CUSTOM_UPSTREAM_HEADER = "X-Reitti-Upstream-Url";
    private static final Duration PRIVATE_TILE_DIRECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String tileCacheUrl;
    private final ObjectMapper objectMapper;
    private final TileProxySignatureService tileProxySignatureService;
    private final RemoteTileUrlValidator remoteTileUrlValidator;
    private final SafeHttpClient safeHttpClient;

    // Maps source names to internal paths and coordinate ordering
    private record SourceConfig(String path, boolean swapXY, String contentType) {}
    
    private static final Map<String, SourceConfig> SOURCES = Map.of(
        "raster", new SourceConfig("/osm/", false, MediaType.IMAGE_PNG_VALUE),
        "osm", new SourceConfig("/osm/", false, "application/x-protobuf"),
        "vector", new SourceConfig("/vector/", false, "application/x-protobuf"),
        "terrain", new SourceConfig("/terrain/", false, "image/webp"),
        "satellite", new SourceConfig("/satellite/", true, "image/jpeg")
    );

    public TileProxyController(
            @Value("${reitti.ui.tiles.cache.url}") String tileCacheUrl,
            ObjectMapper objectMapper,
            TileProxySignatureService tileProxySignatureService,
            RemoteTileUrlValidator remoteTileUrlValidator,
            SafeHttpClient safeHttpClient) {
        this.tileCacheUrl = tileCacheUrl;
        this.objectMapper = objectMapper;
        this.tileProxySignatureService = tileProxySignatureService;
        this.remoteTileUrlValidator = remoteTileUrlValidator;
        this.safeHttpClient = safeHttpClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
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

    @GetMapping("/custom/tilejson/{encodedTileJsonUrl}/{signature}.json")
    public ResponseEntity<JsonNode> getCustomTileJson(
            @PathVariable String encodedTileJsonUrl,
            @PathVariable String signature,
            HttpServletRequest request) {

        try {
            if (!tileProxySignatureService.isValid(encodedTileJsonUrl, signature)) {
                return ResponseEntity.status(403).build();
            }

            String tileJsonUrl = decodeTemplate(encodedTileJsonUrl);
            URI tileJsonUri = remoteTileUrlValidator.requirePublicHttpUrl(tileJsonUrl, "Custom TileJSON URL");

            HttpResponse<byte[]> response = fetchPublicUrl(tileJsonUrl, "Custom TileJSON URL");
            if (response.statusCode() != 200) {
                log.debug("Failed to fetch custom TileJSON [{}]: HTTP {}", tileJsonUri, response.statusCode());
                return ResponseEntity.notFound().build();
            }

            JsonNode tileJson = objectMapper.readTree(responseBody(response));
            if (tileJson instanceof ObjectNode mutableTileJson && mutableTileJson.get("tiles") instanceof ArrayNode tiles) {
                ArrayNode rewrittenTiles = objectMapper.createArrayNode();
                tiles.forEach(tile -> {
                    String tileUrl = tile.asText("");
                    if (tileUrl.startsWith("http://") || tileUrl.startsWith("https://")) {
                        rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(tileUrl) ? customTileUrl(request, tileUrl) : tileUrl);
                    } else if (!tileUrl.isBlank()) {
                        String resolvedTileUrl = tileJsonUri.resolve(tileUrl).toString();
                        rewrittenTiles.add(remoteTileUrlValidator.isServerFetchAllowedTemplate(resolvedTileUrl) ? customTileUrl(request, resolvedTileUrl) : tileUrl);
                    } else {
                        rewrittenTiles.add(tileUrl);
                    }
                });
                mutableTileJson.set("tiles", rewrittenTiles);
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .body(tileJson);
        } catch (Exception e) {
            log.warn("Failed to fetch custom TileJSON [{}]: {}", encodedTileJsonUrl, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/custom/{encodedTemplate}/{signature}/{z}/{x}/{y}.{ext}")
    public ResponseEntity<byte[]> getCustomTile(
            @PathVariable String encodedTemplate,
            @PathVariable String signature,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable String ext) {

        try {
            if (!tileProxySignatureService.isValid(encodedTemplate, signature)) {
                return ResponseEntity.status(403).build();
            }

            String template = decodeTemplate(encodedTemplate);
            String upstreamTileUrl = template
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{r}", "");

            URI upstreamTileUri = remoteTileUrlValidator.requirePublicHttpUrl(upstreamTileUrl, "Custom tile URL");

            if (remoteTileUrlValidator.isValidLocalUrl(upstreamTileUrl)) {
                log.trace("Fetching private custom tile directly [{}]: {}", encodedTemplate, upstreamTileUri);
                return fetchPrivateTile(upstreamTileUrl, contentTypeForExtension(ext));
            }

            String tileUrl = tileCacheUrl + "/custom/";
            log.trace("Fetching custom tile [{}]: {}", encodedTemplate, upstreamTileUri);
            return fetchTile(tileUrl, contentTypeForExtension(ext), "custom", Map.of(CUSTOM_UPSTREAM_HEADER, upstreamTileUrl));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode custom tile template [{}]: {}", encodedTemplate, e.getMessage());
            return ResponseEntity.badRequest().build();
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

            String tileUrl = tileCacheUrl + config.path() + coordPath;
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

    private ResponseEntity<byte[]> fetchPrivateTile(String upstreamTileUrl, String contentType) {
        try {
            HttpResponse<byte[]> response = fetchRaw(upstreamTileUrl, Map.of(), PRIVATE_TILE_DIRECT_TIMEOUT);

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
            }

            log.debug("Failed to fetch private custom tile directly: HTTP {}", response.statusCode());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("Failed to fetch private custom tile directly: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private HttpResponse<byte[]> fetchRaw(String url) throws Exception {
        return fetchRaw(url, Map.of());
    }

    private HttpResponse<byte[]> fetchRaw(String url, Map<String, String> headers) throws Exception {
        return fetchRaw(url, headers, Duration.ofSeconds(30));
    }

    private HttpResponse<byte[]> fetchRaw(String url, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .GET();
        headers.forEach(requestBuilder::header);

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpResponse<byte[]> fetchPublicUrl(String url, String fieldName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .GET()
                .build();

        return safeHttpClient.sendFollowingPublicRedirects(
                httpClient,
                request,
                HttpResponse.BodyHandlers.ofByteArray(),
                fieldName
        );
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

    private String customTileUrl(HttpServletRequest request, String tileUrl) {
        String normalizedTileUrl = normalizeTileTemplateForProxy(tileUrl);
        String encodedTemplate = encodeTemplate(normalizedTileUrl);
        return getBaseUrl(request) + "/api/v1/tiles/custom/" + encodedTemplate + "/" + tileProxySignatureService.sign(encodedTemplate) + "/{z}/{x}/{y}." + TileUrlUtils.extractTileExtension(normalizedTileUrl);
    }

    private String normalizeTileTemplateForProxy(String tileUrl) {
        return tileUrl.replace("{r}", "");
    }

    private String encodeTemplate(String template) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(template.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeTemplate(String encodedTemplate) {
        return new String(Base64.getUrlDecoder().decode(encodedTemplate), StandardCharsets.UTF_8);
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
