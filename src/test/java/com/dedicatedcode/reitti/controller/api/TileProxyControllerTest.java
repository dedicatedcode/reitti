package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileProxyControllerTest {

    private static final String JAWG_SOURCE_ID = "streets-v2+landcover-v1.1+hillshade-v1";
    private static final String JAWG_TILE_URL = "https://tile.jawg.io/streets-v2+landcover-v1.1+hillshade-v1/{z}/{x}/{y}.pbf?access-token=test-token";
    private static final long USER_ID = 7L;
    private static final long STYLE_ID = 42L;
    private static final int Z = 15;
    private static final int X = 17619;
    private static final int Y = 10758;
    private static final String EXT = "pbf";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Convenience helper to create a minimal style JSON with one source
    private ObjectNode createStyleJson(String sourceId, String tileUrl) throws Exception {
        String json = String.format("""
                {
                  "version": 8,
                  "sources": {
                    "%s": {
                      "type": "vector",
                      "tiles": ["%s"]
                    }
                  },
                  "layers": []
                }
                """, sourceId, tileUrl);
        return (ObjectNode) objectMapper.readTree(json);
    }

    private User createTestUser() {
        return new User(USER_ID, "test", null, "Test", null, null, null, null);
    }

    private UserMapStyle createStyle(boolean proxyTiles) {
        String styleJson = null;
        try {
            styleJson = createStyleJson(JAWG_SOURCE_ID, JAWG_TILE_URL).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new UserMapStyle(
                STYLE_ID,
                USER_ID,
                "Jawg",
                "vector",
                "json",
                "tile_template",
                styleJson,
                null,
                new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, proxyTiles),
                null,
                false,
                false,
                1L
        );
    }

    // ------------------------------------------------------------------
    // Tests for getStyleSourceTile (proxied custom tile)
    // ------------------------------------------------------------------

    @Test
    void resolvesReadableSourcePathIdBackToOriginalTileTemplate() throws Exception {
        // Start a local HTTP server that acts as the tile cache.
        HttpServer tileCache = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> upstreamHeader = new AtomicReference<>();
        tileCache.createContext("/custom/", exchange -> {
            upstreamHeader.set(exchange.getRequestHeaders().getFirst("X-Reitti-Upstream-Url"));
            byte[] body = "tile".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        tileCache.start();

        try {
            User user = createTestUser();
            UserMapStyle style = createStyle(true);               // proxyTiles = true
            UserMapStyleJdbcService jdbc = mock(UserMapStyleJdbcService.class);
            when(jdbc.findById(user, STYLE_ID)).thenReturn(Optional.of(style));

            ObjectNode styleJson = createStyleJson(JAWG_SOURCE_ID, JAWG_TILE_URL);
            MapLibreMapStylesService stylesService = mock(MapLibreMapStylesService.class);
            when(stylesService.getCompleteStyleJson(eq(style.id()), eq(user)))
                    .thenReturn(styleJson);

            TileProxyController controller = new TileProxyController(
                    "http://127.0.0.1:" + tileCache.getAddress().getPort(),
                    new ObjectMapper(),
                    jdbc,
                    stylesService,
                    new ContextPathHolder("")
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user,
                    style.id(),
                    JAWG_SOURCE_ID,
                    Z, X, Y, EXT
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(upstreamHeader.get())
                    .isEqualTo("https://tile.jawg.io/streets-v2+landcover-v1.1+hillshade-v1/15/17619/10758.pbf?access-token=test-token");
        } finally {
            tileCache.stop(0);
        }
    }

    @Test
    void doesNotProxyCustomStyleTileUrlsWhenDisabled() throws Exception {
        HttpServer tileCache = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> upstreamHeader = new AtomicReference<>();
        tileCache.createContext("/custom/", exchange -> {
            upstreamHeader.set(exchange.getRequestHeaders().getFirst("X-Reitti-Upstream-Url"));
            byte[] body = "tile".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        tileCache.start();

        try {
            User user = createTestUser();
            UserMapStyle style = createStyle(false);              // proxyTiles = false
            UserMapStyleJdbcService jdbc = mock(UserMapStyleJdbcService.class);
            when(jdbc.findById(user, STYLE_ID)).thenReturn(Optional.of(style));

            ObjectNode styleJson = createStyleJson(JAWG_SOURCE_ID, JAWG_TILE_URL);
            MapLibreMapStylesService stylesService = mock(MapLibreMapStylesService.class);
            when(stylesService.getCompleteStyleJson(eq(style.id()), eq(user)))
                    .thenReturn(styleJson);

            TileProxyController controller = new TileProxyController(
                    "http://127.0.0.1:" + tileCache.getAddress().getPort(),
                    new ObjectMapper(),
                    jdbc,
                    stylesService,
                    new ContextPathHolder("")
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user, style.id(),
                    JAWG_SOURCE_ID,
                    Z, X, Y, EXT
            );

            assertThat(response.getStatusCode().is4xxClientError()).isTrue();
            assertThat(upstreamHeader.get()).isNull();
        } finally {
            tileCache.stop(0);
        }
    }

    @Test
    void proxiesCustomStyleTileUrlsDirectlyWhenCacheIsDisabled() throws Exception {
        // Start a local server that simulates the original tile provider (no cache involved).
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> upstreamHeader = new AtomicReference<>();
        upstream.createContext("/streets/15/17619/10758.pbf", exchange -> {
            upstreamHeader.set(exchange.getRequestHeaders().getFirst("X-Reitti-Upstream-Url"));
            byte[] body = "tile".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();

        try {
            String upstreamUrl = "http://127.0.0.1:" + upstream.getAddress().getPort() + "/streets/{z}/{x}/{y}.pbf";
            User user = createTestUser();
            String styleJsonStr = createStyleJson(JAWG_SOURCE_ID, upstreamUrl).toString();
            UserMapStyle style = new UserMapStyle(
                    STYLE_ID,
                    USER_ID,
                    "Jawg",
                    "vector",
                    "json",
                    "tile_template",
                    styleJsonStr,
                    null,
                    new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, true),
                    null,
                    false,
                    false,
                    1L
            );
            UserMapStyleJdbcService jdbc = mock(UserMapStyleJdbcService.class);
            when(jdbc.findById(user, STYLE_ID)).thenReturn(Optional.of(style));

            ObjectNode styleJson = createStyleJson(JAWG_SOURCE_ID, upstreamUrl);
            MapLibreMapStylesService stylesService = mock(MapLibreMapStylesService.class);
            when(stylesService.getCompleteStyleJson(eq(style.id()), eq(user)))
                    .thenReturn(styleJson);

            TileProxyController controller = new TileProxyController(
                    "",                          // cache disabled (empty url)
                    new ObjectMapper(),
                    jdbc,
                    stylesService,
                    new ContextPathHolder("")
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user, style.id(),
                    JAWG_SOURCE_ID,
                    Z, X, Y, EXT
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("tile");
            assertThat(upstreamHeader.get()).isNull();   // no custom header when fetching directly
        } finally {
            upstream.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Tests for getStyleSourceTileJson
    // ------------------------------------------------------------------

    @Test
    void returnsTileJsonForCustomSource() throws Exception {
        HttpServer tileCache = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tileCache.createContext("/tilejson/", exchange -> {
            String body = """
                    {
                      "tiles": ["https://example.com/{z}/{x}/{y}.pbf"]
                    }
                    """;
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, b.length);
            exchange.getResponseBody().write(b);
            exchange.close();
        });
        tileCache.start();

        try {
            String tileJsonUrl = "http://127.0.0.1:" + tileCache.getAddress().getPort() + "/tilejson/test.json";
            User user = createTestUser();
            UserMapStyle style = new UserMapStyle(
                    STYLE_ID, USER_ID, "Raster", "raster", "json",
                    "tile_template", null, null,
                    new MapStyleDataSource("raster-source", "raster", tileJsonUrl, null, null, null, null, 256, null, true),
                    null, false, false, 1L
            );
            UserMapStyleJdbcService jdbc = mock(UserMapStyleJdbcService.class);
            when(jdbc.findById(user, STYLE_ID)).thenReturn(Optional.of(style));

            // The service should return a raster style that contains the tileJsonUrl.
            ObjectNode rasterStyle = objectMapper.createObjectNode();
            rasterStyle.put("version", 8);
            rasterStyle.put("name", "Raster");
            ObjectNode sources = objectMapper.createObjectNode();
            ObjectNode source = objectMapper.createObjectNode();
            source.put("type", "raster");
            source.put("url", tileJsonUrl);
            source.put("tileSize", 256);
            sources.set("raster-source", source);
            rasterStyle.set("sources", sources);

            MapLibreMapStylesService stylesService = mock(MapLibreMapStylesService.class);
            when(stylesService.getCompleteStyleJson(eq(style.id()), eq(user)))
                    .thenReturn(rasterStyle);

            TileProxyController controller = new TileProxyController(
                    "http://127.0.0.1:" + tileCache.getAddress().getPort(),
                    new ObjectMapper(),
                    jdbc,
                    stylesService,
                    new ContextPathHolder("")
            );

            ResponseEntity<JsonNode> response = controller.getStyleSourceTileJson(
                    user, style.id(), "raster-source"
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().get("tiles").get(0).asText())
                    .startsWith("/api/v1/tiles/styles/custom-");
        } finally {
            tileCache.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Tests for getStyleJson
    // ------------------------------------------------------------------


    @Test
    void returnsNotFoundForMissingCustomStyle() throws Exception {
        MapLibreMapStylesService stylesService = mock(MapLibreMapStylesService.class);
        when(stylesService.getCompleteStyleJson(eq(999L), any()))
                .thenReturn(null);

        TileProxyController controller = new TileProxyController(
                "",
                new ObjectMapper(),
                mock(UserMapStyleJdbcService.class),
                stylesService,
                new ContextPathHolder("")
        );

        ResponseEntity<JsonNode> response = controller.getStyleJson(null, 999L);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
