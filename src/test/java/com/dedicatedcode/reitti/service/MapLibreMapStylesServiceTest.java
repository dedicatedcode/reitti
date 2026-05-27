package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.MapLibreStyleDefinition;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapLibreMapStylesServiceTest {

    @Mock
    private UserMapStyleJdbcService userMapStyleJdbcService;

    @Mock
    private ContextPathHolder contextPathHolder;

    @Mock
    private UserSettingsJdbcService userSettingsJdbcService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MapLibreMapStylesService service;

    @BeforeEach
    void setUp() {
        lenient().when(contextPathHolder.getContextPath()).thenReturn("");
        service = new MapLibreMapStylesService(
                userMapStyleJdbcService,
                contextPathHolder,
                userSettingsJdbcService,
                objectMapper,
                "" // tile cache disabled
        );
    }

    @Test
    void getCompleteStyleJsonReturnsReittiStyleWithRuntimeSources() {
        User user = createUser();
        UserSettings userSettings = mock(UserSettings.class);
        when(userSettings.isPreferColoredMap()).thenReturn(false);
        when(userSettingsJdbcService.getOrCreateDefaultSettings(user.getId())).thenReturn(userSettings);

        JsonNode result = service.getCompleteStyleJson("reitti", user);
        assertThat(result).isNotNull();
        assertThat(result.get("version").asInt()).isEqualTo(8);

        // Verify runtime sources are present
        JsonNode sources = result.get("sources");
        assertThat(sources).isNotNull();
        assertThat(sources.has("reitti-terrain-source")).isTrue();
        assertThat(sources.has("reitti-satellite-source")).isTrue();
    }

    @Test
    void getCompleteStyleJsonReturnsNullForNonexistentCustomStyle() {
        // styleId that is not "reitti" and not parseable as custom -> resolveCustomId returns empty -> null
        User user = createUser();
        JsonNode result = service.getCompleteStyleJson("unknown-style", user);
        assertThat(result).isNull();
    }

    @Test
    void getCompleteStyleJsonReturnsCustomRasterStyleJson() throws Exception {
        User user = createUser();
        // Use a raster style with a tile URL template (simpler code path)
        String tileUrlTemplate = "https://example.com/{z}/{x}/{y}.png";
        // The dataSource must provide the tileUrlTemplate, otherwise buildRasterStyleJson returns null.
        UserMapStyle style = new UserMapStyle(
                1L, user.getId(), "RasterTest", "raster", "json",
                tileUrlTemplate, null, null,
                new MapStyleDataSource("raster-source", "raster", null, tileUrlTemplate, null, null, null, 256, null, false),
                null, false, 1L);
        when(userMapStyleJdbcService.findById(user, 1L)).thenReturn(Optional.of(style));

        JsonNode result = service.getCompleteStyleJson("1", user);
        assertThat(result).isNotNull();
        assertThat(result.path("version").asInt()).isEqualTo(8);
        // Verify the source is present
        JsonNode sources = result.path("sources");
        assertThat(sources.has("raster-source")).isTrue();
    }

    @Test
    void getConfigReturnsStyleDefinitions() {
        User user = createUser();
        UserMapStyle style = new UserMapStyle(
                1L, user.getId(), "Style1", "vector", "json",
                null, "{}", null,
                new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, false),
                null, false, 1L);
        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(style));

        List<MapLibreStyleDefinition> config = service.getConfig(user);
        assertThat(config).hasSize(1);
        MapLibreStyleDefinition def = config.get(0);
        assertThat(def.label()).isEqualTo("Style1");
        assertThat(def.mapType()).isEqualTo("vector");
    }

    @Test
    void getConfigDoesNotSkipStylesWithNullMapType() {
        User user = createUser();
        UserMapStyle goodStyle = new UserMapStyle(
                1L, user.getId(), "Good", "vector", "json",
                null, "{}", null,
                new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, false),
                null, false, 1L);
        UserMapStyle styleWithNullMapType = new UserMapStyle(
                2L, user.getId(), null, null, null,
                null, null, null,
                null, null, false, 1L);
        when(userMapStyleJdbcService.findAll(user)).thenReturn(List.of(goodStyle, styleWithNullMapType));

        List<MapLibreStyleDefinition> config = service.getConfig(user);
        // Both styles are included (the service does not skip null mapType)
        assertThat(config).hasSize(2);
        assertThat(config.get(0).label()).isEqualTo("Good");
        assertThat(config.get(1).label()).isNull();
        assertThat(config.get(1).mapType()).isNull();
    }

    private User createUser() {
        return new User(1L, "testuser", null, "Test", null, null, null, null);
    }
}
