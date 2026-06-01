package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.MapLibreMapStylesService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileProxyControllerTest {

    @Mock
    private MapLibreMapStylesService mapLibreMapStylesService;
    @Mock
    private UserMapStyleJdbcService userMapStyleJdbcService;
    @Mock
    private ContextPathHolder contextPathHolder;

    private ObjectMapper objectMapper;
    private TileProxyController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        controller = new TileProxyController(
                "",                        // tileCacheUrl (empty => caching disabled)
                objectMapper,
                userMapStyleJdbcService,
                mapLibreMapStylesService,
                contextPathHolder
        );
    }

    @Test
    void testGetStyleSourceTileJsonWithTileUrlTemplate() throws Exception {
        // Arrange
        User user = mock(User.class);
        Long styleId = 1L;
        String sourceId = "dedicatedcode";

        // Simulate a style that has proxyTiles = true
        MapStyleDataSource dataSource = mock(MapStyleDataSource.class);
        when(dataSource.proxyTiles()).thenReturn(true);

        UserMapStyle style = mock(UserMapStyle.class);
        when(style.dataSource()).thenReturn(dataSource);

        when(userMapStyleJdbcService.findById(eq(user), eq(styleId)))
                .thenReturn(Optional.of(style));

        // getOriginalTileUrl returns a tile URL template (not TileJSON)
        String tileUrlTemplate = "https://tiles.dedicatedcode.com/planet/20260422_001001_pt/{z}/{x}/{y}.pbf";
        when(mapLibreMapStylesService.getOriginalTileUrl(styleId, sourceId, user))
                .thenReturn(tileUrlTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        // Optionally set context path if needed
        request.setContextPath("");
        when(contextPathHolder.getContextPath()).thenReturn("");

        // Act
        ResponseEntity<JsonNode> response = controller.getStyleSourceTileJson(user, styleId, sourceId, request);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = response.getBody();
        assertNotNull(body);

        // The response should be a TileJSON with a "tiles" array
        assertTrue(body.has("tiles"), "Response should contain a 'tiles' array");
        ArrayNode tiles = (ArrayNode) body.get("tiles");
        assertFalse(tiles.isEmpty(), "Tiles array should not be empty");

        String proxiedUrl = tiles.get(0).asText();
        // Verify the proxied URL structure
        assertTrue(proxiedUrl.startsWith("http://localhost:8080/api/v1/tiles/styles/1/dedicatedcode/"),
                   "Proxied URL should start with the correct path");
        assertTrue(proxiedUrl.endsWith(".pbf"),
                   "Proxied URL should end with .pbf extension");
        // Ensure placeholders are present
        assertTrue(proxiedUrl.contains("{z}"));
        assertTrue(proxiedUrl.contains("{x}"));
        assertTrue(proxiedUrl.contains("{y}"));
    }
}
