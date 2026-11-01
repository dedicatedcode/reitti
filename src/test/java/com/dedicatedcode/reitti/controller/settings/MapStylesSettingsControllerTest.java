package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MapStylesSettingsControllerTest {

    private static HttpServer httpServer;
    private static String baseUrl;

    @BeforeAll
    static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/tilejson.json", exchange -> respond(exchange, 200,
                "{\"tilejson\":\"2.2.0\",\"tiles\":[\"https://tiles.example.com/{z}/{x}/{y}.png\"]}"));
        httpServer.createContext("/style.json", exchange -> respond(exchange, 200,
                "{\"version\":8,\"sources\":{\"basemap\":{\"type\":\"raster\",\"tiles\":[\"https://tiles.example.com/{z}/{x}/{y}.png\"]}},"
                        + "\"layers\":[{\"id\":\"basemap\",\"type\":\"raster\",\"source\":\"basemap\"}]}"));
        httpServer.createContext("/no-tiles.json", exchange -> respond(exchange, 200, "{\"name\":\"something\"}"));
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopHttpServer() {
        httpServer.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserMapStyleJdbcService userMapStyleJdbcService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void nonAdminCreatingSharedStyleShouldBeForbidden() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Shared Style")
                        .param("mapType", "vector")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "url-template")
                        .param("vectorStyleUrl", "https://example.com/style.json")
                        .param("shared", "on"))
                .andExpect(status().isForbidden());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .noneMatch(style -> "Shared Style".equals(style.name()));
    }

    @Test
    void nonAdminUpdatingStyleToSharedShouldBeForbidden() throws Exception {
        UserMapStyle saved = userMapStyleJdbcService.save(testUser, createStyle("Own Style", false));

        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("id", saved.id().toString())
                        .param("name", "Own Style")
                        .param("mapType", "vector")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "url-template")
                        .param("vectorStyleUrl", "https://example.com/style.json")
                        .param("shared", "on"))
                .andExpect(status().isForbidden());

        assertThat(userMapStyleJdbcService.findOwnedById(testUser, saved.id()))
                .get()
                .extracting(UserMapStyle::shared)
                .isEqualTo(false);
    }

    @Test
    void nonAdminCreatingStyleWithoutSharedFlagShouldSucceed() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Private Style")
                        .param("mapType", "vector")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "url-template")
                        .param("vectorStyleUrl", "https://example.com/style.json"))
                .andExpect(status().isOk());

        List<UserMapStyle> styles = userMapStyleJdbcService.findAll(testUser);
        assertThat(styles)
                .filteredOn(style -> "Private Style".equals(style.name()))
                .singleElement()
                .extracting(UserMapStyle::shared)
                .isEqualTo(false);
    }

    @Test
    void adminCreatingSharedStyleShouldSucceed() throws Exception {
        User admin = testingService.admin();

        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(admin))
                        .param("name", "Admin Shared Style")
                        .param("mapType", "vector")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "url-template")
                        .param("vectorStyleUrl", "https://example.com/style.json")
                        .param("shared", "on"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(admin))
                .filteredOn(style -> "Admin Shared Style".equals(style.name()))
                .singleElement()
                .extracting(UserMapStyle::shared)
                .isEqualTo(true);
    }

    @Test
    void rasterStyleWithStyleJsonAsTileJsonUrlShouldBeRejected() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Broken Raster Style")
                        .param("mapType", "raster")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "json-url")
                        .param("rasterTileJsonUrl", baseUrl + "/style.json"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .noneMatch(style -> "Broken Raster Style".equals(style.name()));
    }

    @Test
    void rasterStyleWithJsonWithoutTilesShouldBeRejected() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Empty Raster Style")
                        .param("mapType", "raster")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "json-url")
                        .param("rasterTileJsonUrl", baseUrl + "/no-tiles.json"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .noneMatch(style -> "Empty Raster Style".equals(style.name()));
    }

    @Test
    void rasterStyleWithUnreachableTileJsonUrlShouldBeRejected() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Unreachable Raster Style")
                        .param("mapType", "raster")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "json-url")
                        .param("rasterTileJsonUrl", "http://127.0.0.1:1/tile.json"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .noneMatch(style -> "Unreachable Raster Style".equals(style.name()));
    }

    @Test
    void rasterStyleWithValidTileJsonUrlShouldBeSaved() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Working Raster Style")
                        .param("mapType", "raster")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "json-url")
                        .param("rasterTileJsonUrl", baseUrl + "/tilejson.json"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .filteredOn(style -> "Working Raster Style".equals(style.name()))
                .singleElement()
                .extracting(style -> style.dataSource().tileJsonUrl())
                .isEqualTo(baseUrl + "/tilejson.json");
    }

    @Test
    void rasterStyleWithNonHttpTileJsonUrlShouldBeSaved() throws Exception {
        mockMvc.perform(post("/settings/map-styles").with(csrf()).with(user(testUser))
                        .param("name", "Relative Raster Style")
                        .param("mapType", "raster")
                        .param("styleInputType", "url")
                        .param("rasterSourceInputType", "json-url")
                        .param("rasterTileJsonUrl", "/tiles/tile.json"))
                .andExpect(status().isOk());

        assertThat(userMapStyleJdbcService.findAll(testUser))
                .filteredOn(style -> "Relative Raster Style".equals(style.name()))
                .singleElement()
                .extracting(style -> style.dataSource().tileJsonUrl())
                .isEqualTo("/tiles/tile.json");
    }

    private UserMapStyle createStyle(String name, boolean shared) {
        return new UserMapStyle(
                null,
                testUser.getId(),
                name,
                "vector",
                "url",
                "url-template",
                null,
                "https://example.com/style.json",
                new MapStyleDataSource(null, "vector", null, null, null, null, null, null, null, false),
                new MapStyleVectorOptions(null, null, null),
                false,
                shared,
                null);
    }
}
