package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.MapStylePathUtils;
import com.dedicatedcode.reitti.service.MapStyleUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapStyleControllerTest {
    private static final String JAWG_SOURCE_ID = "streets-v2+landcover-v1.1+hillshade-v1";
    private static final String JAWG_TILE_URL = "https://tile.jawg.io/streets-v2+landcover-v1.1+hillshade-v1/{z}/{x}/{y}.pbf?access-token=test-token";

    @Test
    void rewritesProxyTileUrlsWithEncodedStyleAndSourcePathSegments() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
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

        MapStyleController controller = new MapStyleController(
                objectMapper,
                new ContextPathHolder(""),
                mock(UserSettingsJdbcService.class),
                userMapStyleJdbcService,
                new MapStyleUrlValidator(mock(I18nService.class)),
                "http://tile-cache"
        );

        ResponseEntity<JsonNode> response = controller.getUserCustomStyle(user, 42L, new MockHttpServletRequest());

        JsonNode tiles = response.getBody().path("sources").path(JAWG_SOURCE_ID).path("tiles");
        assertThat(tiles.get(0).asText()).isEqualTo(
                "http://localhost/api/v1/tiles/styles/custom-42/sources/" + MapStylePathUtils.sourcePathId(JAWG_SOURCE_ID) + "/tiles/0/{z}/{x}/{y}.pbf"
        );
    }
}
