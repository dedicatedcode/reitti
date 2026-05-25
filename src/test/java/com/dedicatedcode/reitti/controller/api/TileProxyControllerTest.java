package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.MapStylePathUtils;
import com.dedicatedcode.reitti.service.MapStyleUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileProxyControllerTest {
    private static final String JAWG_SOURCE_ID = "streets-v2+landcover-v1.1+hillshade-v1";
    private static final String JAWG_TILE_URL = "https://tile.jawg.io/streets-v2+landcover-v1.1+hillshade-v1/{z}/{x}/{y}.pbf?access-token=test-token";

    @Test
    void resolvesReadableSourcePathIdBackToOriginalTileTemplate() throws Exception {
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
            User user = new User(7L, "test", null, "Test", null, null, null, null);
            UserMapStyle style = new UserMapStyle(
                    42L,
                    user.getId(),
                    "Jawg",
                    "vector",
                    "json",
                    "tile_template",
                    """
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
                            """.formatted(JAWG_SOURCE_ID, JAWG_TILE_URL),
                    null,
                    new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, true),
                    null,
                    false,
                    1L
            );
            UserMapStyleJdbcService userMapStyleJdbcService = mock(UserMapStyleJdbcService.class);
            when(userMapStyleJdbcService.findById(user, 42L)).thenReturn(Optional.of(style));

            TileProxyController controller = new TileProxyController(
                    "http://127.0.0.1:" + tileCache.getAddress().getPort(),
                    new ObjectMapper(),
                    userMapStyleJdbcService,
                    new MapStyleUrlValidator(mock(I18nService.class))
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user,
                    "custom-42",
                    MapStylePathUtils.sourcePathId(JAWG_SOURCE_ID),
                    15,
                    17619,
                    10758,
                    "pbf"
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(upstreamHeader.get()).isEqualTo(
                    "https://tile.jawg.io/streets-v2+landcover-v1.1+hillshade-v1/15/17619/10758.pbf?access-token=test-token"
            );
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
            User user = new User(7L, "test", null, "Test", null, null, null, null);
            UserMapStyle style = new UserMapStyle(
                    42L,
                    user.getId(),
                    "Jawg",
                    "vector",
                    "json",
                    "tile_template",
                    """
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
                            """.formatted(JAWG_SOURCE_ID, JAWG_TILE_URL),
                    null,
                    new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, false),
                    null,
                    false,
                    1L
            );
            UserMapStyleJdbcService userMapStyleJdbcService = mock(UserMapStyleJdbcService.class);
            when(userMapStyleJdbcService.findById(user, 42L)).thenReturn(Optional.of(style));

            TileProxyController controller = new TileProxyController(
                    "http://127.0.0.1:" + tileCache.getAddress().getPort(),
                    new ObjectMapper(),
                    userMapStyleJdbcService,
                    new MapStyleUrlValidator(mock(I18nService.class))
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user,
                    "custom-42",
                    MapStylePathUtils.sourcePathId(JAWG_SOURCE_ID),
                    15,
                    17619,
                    10758,
                    "pbf"
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(upstreamHeader.get()).isNull();
        } finally {
            tileCache.stop(0);
        }
    }

    @Test
    void proxiesCustomStyleTileUrlsDirectlyWhenCacheIsDisabled() throws Exception {
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
            User user = new User(7L, "test", null, "Test", null, null, null, null);
            UserMapStyle style = new UserMapStyle(
                    42L,
                    user.getId(),
                    "Jawg",
                    "vector",
                    "json",
                    "tile_template",
                    """
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
                            """.formatted(JAWG_SOURCE_ID, upstreamUrl),
                    null,
                    new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, true),
                    null,
                    false,
                    1L
            );
            UserMapStyleJdbcService userMapStyleJdbcService = mock(UserMapStyleJdbcService.class);
            when(userMapStyleJdbcService.findById(user, 42L)).thenReturn(Optional.of(style));

            TileProxyController controller = new TileProxyController(
                    "",
                    new ObjectMapper(),
                    userMapStyleJdbcService,
                    new MapStyleUrlValidator(mock(I18nService.class))
            );

            ResponseEntity<byte[]> response = controller.getStyleSourceTile(
                    user,
                    "custom-42",
                    MapStylePathUtils.sourcePathId(JAWG_SOURCE_ID),
                    15,
                    17619,
                    10758,
                    "pbf"
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("tile");
            assertThat(upstreamHeader.get()).isNull();
        } finally {
            upstream.stop(0);
        }
    }
}
