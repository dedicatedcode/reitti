package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.MapLibreStyleDefinition;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapLibreMapStylesServiceTest {

    @Mock
    private UserMapStyleJdbcService userMapStyleJdbcService;

    @Mock
    private ContextPathHolder contextPathHolder;

    private ObjectMapper objectMapper;
    private MapLibreMapStylesService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // tileCachingEnabled = true (non-empty cache URL)
        service = new MapLibreMapStylesService("http://cache.local", userMapStyleJdbcService, objectMapper, contextPathHolder);
    }

    @Test
    void shouldBuildRasterStyleWithTileUrlTemplateAndProxy() {
        // Given
        User user = mock(User.class);
        when(contextPathHolder.getContextPath()).thenReturn("");

        MapStyleDataSource dataSource = new MapStyleDataSource(
                "my-raster-source", // sourceId
                null,                // type
                null,                // tileJsonUrl
                "https://tiles.example.com/{z}/{x}/{y}.png", // tileUrlTemplate
                "© Example",         // attribution
                0,                   // minzoom
                18,                  // maxzoom
                256,                 // tileSize
                null,                // scheme
                true                 // proxyTiles
        );

        UserMapStyle style = new UserMapStyle(
                1L,                    // id
                null,                  // userId
                "My Raster Style",     // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                dataSource,            // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        assertEquals(1, result.size());
        MapLibreStyleDefinition def = result.get(0);
        assertEquals("1", def.id());
        assertEquals("My Raster Style", def.label());
        assertEquals("raster", def.mapType());
        assertEquals("json", def.styleInputType());

        JsonNode styleJson = def.styleInput();
        assertNotNull(styleJson);
        assertEquals(8, styleJson.get("version").asInt());

        // Verify tile URL is proxied
        JsonNode sources = styleJson.get("sources");
        assertNotNull(sources);
        JsonNode source = sources.get("my-raster-source");
        assertNotNull(source);
        JsonNode tiles = source.get("tiles");
        assertNotNull(tiles);
        assertEquals(1, tiles.size());
        String proxiedUrl = tiles.get(0).asText();
        assertTrue(proxiedUrl.startsWith("/api/v1/tiles/styles/1/my-raster-source/"));
        assertTrue(proxiedUrl.contains("{z}/{x}/{y}.png"));
    }

    @Test
    void shouldBuildRasterStyleWithTileJsonUrlAndProxy() {
        // Given
        User user = mock(User.class);
        when(contextPathHolder.getContextPath()).thenReturn("");

        MapStyleDataSource dataSource = new MapStyleDataSource(
                "raster-tiles",      // sourceId
                null,                // type
                "https://tiles.example.com/tilejson.json", // tileJsonUrl
                null,                // tileUrlTemplate
                "© Example",         // attribution
                0,                   // minzoom
                22,                  // maxzoom
                512,                 // tileSize
                null,                // scheme
                true                 // proxyTiles
        );

        UserMapStyle style = new UserMapStyle(
                2L,                    // id
                null,                  // userId
                "Satellite",           // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                dataSource,            // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        assertEquals(1, result.size());
        MapLibreStyleDefinition def = result.get(0);
        JsonNode styleJson = def.styleInput();
        JsonNode source = styleJson.get("sources").get("raster-tiles");
        String proxiedUrl = source.get("url").asText();
        assertTrue(proxiedUrl.startsWith("/api/v1/tiles/styles/2/raster-tiles/tilejson.json"));
    }

    @Test
    void shouldPrependContextPathWhenConfigured() {
        // Given
        User user = mock(User.class);
        when(contextPathHolder.getContextPath()).thenReturn("/reitti");

        MapStyleDataSource dataSource = new MapStyleDataSource(
                "src",               // sourceId
                null,                // type
                null,                // tileJsonUrl
                "https://tiles.example.com/{z}/{x}/{y}.png", // tileUrlTemplate
                null,                // attribution
                0,                   // minzoom
                18,                  // maxzoom
                256,                 // tileSize
                null,                // scheme
                true                 // proxyTiles
        );

        UserMapStyle style = new UserMapStyle(
                3L,                    // id
                null,                  // userId
                "With Context",        // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                dataSource,            // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        JsonNode styleJson = result.get(0).styleInput();
        JsonNode tiles = styleJson.get("sources").get("src").get("tiles");
        String proxiedUrl = tiles.get(0).asText();
        assertTrue(proxiedUrl.startsWith("/reitti/api/v1/tiles/styles/3/src/"));
    }

    @Test
    void shouldNotProxyWhenTileCachingDisabled() {
        // Given
        service = new MapLibreMapStylesService("", userMapStyleJdbcService, objectMapper, contextPathHolder);
        User user = mock(User.class);

        MapStyleDataSource dataSource = new MapStyleDataSource(
                "raster-tiles",      // sourceId
                null,                // type
                null,                // tileJsonUrl
                "https://tiles.example.com/{z}/{x}/{y}.png", // tileUrlTemplate
                null,                // attribution
                0,                   // minzoom
                18,                  // maxzoom
                256,                 // tileSize
                null,                // scheme
                true                 // proxyTiles
        );

        UserMapStyle style = new UserMapStyle(
                4L,                    // id
                null,                  // userId
                "No Cache",            // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                dataSource,            // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        JsonNode styleJson = result.get(0).styleInput();
        JsonNode tiles = styleJson.get("sources").get("raster-tiles").get("tiles");
        String tileUrl = tiles.get(0).asText();
        assertEquals("https://tiles.example.com/{z}/{x}/{y}.png", tileUrl);
    }

    @Test
    void shouldBuildVectorStyleFromInlineJsonAndRewriteUrlsWhenProxied() {
        // Given
        User user = mock(User.class);
        when(contextPathHolder.getContextPath()).thenReturn("");

        String inlineStyle = """
                {
                    "version": 8,
                    "sources": {
                        "osm": {
                            "type": "vector",
                            "url": "https://tiles.example.com/planet.json"
                        }
                    },
                    "layers": []
                }
                """;

        MapStyleDataSource dataSource = new MapStyleDataSource(
                "osm",               // sourceId
                null,                // type
                null,                // tileJsonUrl
                null,                // tileUrlTemplate
                null,                // attribution
                0,                   // minzoom
                22,                  // maxzoom
                256,                 // tileSize
                null,                // scheme
                true                 // proxyTiles
        );

        UserMapStyle style = new UserMapStyle(
                5L,                    // id
                null,                  // userId
                "Proxied Vector",      // name
                "vector",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                inlineStyle,           // styleJson
                null,                  // styleUrl
                dataSource,            // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        JsonNode styleJson = result.get(0).styleInput();
        JsonNode osmSource = styleJson.get("sources").get("osm");
        String proxiedUrl = osmSource.get("url").asText();
        assertTrue(proxiedUrl.startsWith("/api/v1/tiles/styles/5/osm/tilejson.json"));
    }

    @Test
    void shouldNotRewriteUrlsWhenNotProxied() {
        // Given
        User user = mock(User.class);

        String inlineStyle = """
                {
                    "version": 8,
                    "sources": {
                        "osm": {
                            "type": "vector",
                            "url": "https://tiles.example.com/planet.json"
                        }
                    },
                    "layers": []
                }
                """;

        // No data source, so proxyTiles defaults to false
        UserMapStyle style = new UserMapStyle(
                6L,                    // id
                null,                  // userId
                "Not Proxied",         // name
                "vector",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                inlineStyle,           // styleJson
                null,                  // styleUrl
                null,                  // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        JsonNode styleJson = result.get(0).styleInput();
        JsonNode osmSource = styleJson.get("sources").get("osm");
        assertEquals("https://tiles.example.com/planet.json", osmSource.get("url").asText());
    }

    @Test
    void shouldReturnEmptyListWhenNoStyles() {
        // Given
        User user = mock(User.class);
        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of());

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipStyleWithInvalidData() {
        // Given
        User user = mock(User.class);

        // Style with no data source and no styleJson/styleUrl for raster
        UserMapStyle invalidStyle = new UserMapStyle(
                7L,                    // id
                null,                  // userId
                "Invalid",             // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                null,                  // dataSource
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        UserMapStyle validStyle = new UserMapStyle(
                8L,                    // id
                null,                  // userId
                "Valid Raster",        // name
                "raster",              // mapType
                null,                  // styleInputType
                null,                  // rasterSourceInputType
                null,                  // styleJson
                null,                  // styleUrl
                new MapStyleDataSource( // dataSource
                        "src",         // sourceId
                        null,          // type
                        null,          // tileJsonUrl
                        "https://example.com/{z}/{x}/{y}.png", // tileUrlTemplate
                        null,          // attribution
                        0,             // minzoom
                        18,            // maxzoom
                        256,           // tileSize
                        null,          // scheme
                        false          // proxyTiles
                ),
                null,                  // vectorOptions
                false,                 // shared
                1L                     // version
        );

        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(invalidStyle, validStyle));

        // When
        List<MapLibreStyleDefinition> result = service.getConfig(user);

        // Then
        assertEquals(1, result.size());
        assertEquals("8", result.get(0).id());
    }
}