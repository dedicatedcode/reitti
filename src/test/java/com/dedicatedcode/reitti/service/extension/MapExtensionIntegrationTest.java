package com.dedicatedcode.reitti.service.extension;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.extension.ExtensionRegistration;
import com.dedicatedcode.reitti.repository.MapExtensionJdbcService;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MapExtensionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private ExtensionCatalogService catalogService;
    @Autowired
    private ExtensionManifestService manifestService;
    @Autowired
    private MapExtensionJdbcService repository;
    @Autowired
    private ExtensionHttpClient extensionHttpClient;

    private HttpServer upstream;
    private String baseUrl;
    private final AtomicReference<String> manifest = new AtomicReference<>();
    private final AtomicReference<String> dataAuthorization = new AtomicReference<>();
    private final AtomicReference<String> dataCookie = new AtomicReference<>();
    private final AtomicReference<String> dataQuery = new AtomicReference<>();
    private final AtomicInteger dataCalls = new AtomicInteger();

    @DynamicPropertySource
    static void extensionProperties(DynamicPropertyRegistry registry) {
        registry.add("reitti.extensions.allowed-hosts", () -> "localhost,127.0.0.1,192.168.2.200");
        registry.add("reitti.extensions.request-timeout-ms", () -> "2000");
    }

    @BeforeEach
    void setUp() throws IOException {
        repository.findAll().forEach(registration -> repository.delete(registration.id()));
        manifest.set(manifest("1.0.0"));
        dataCalls.set(0);
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/manifest.json", exchange -> respond(exchange, 200, "application/json",
                manifest.get().getBytes(StandardCharsets.UTF_8)));
        upstream.createContext("/data", exchange -> {
            dataCalls.incrementAndGet();
            dataAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            dataCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            dataQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "application/json", "{\"source\":\"upstream\"}".getBytes(StandardCharsets.UTF_8));
        });
        upstream.createContext("/asset.png", exchange -> respond(exchange, 200, "image/png", new byte[]{1, 2, 3}));
        upstream.createContext("/slow-data", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 10);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        upstream.start();
        baseUrl = "http://localhost:" + upstream.getAddress().getPort();
    }

    @AfterEach
    void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void registrationSanitizesMetadataAndProxiesOnlyPersistedResources() throws Exception {
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", "test-secret");
        assertThat(registration.encryptedCredentials()).isNotBlank().isNotEqualTo("test-secret");

        mockMvc.perform(get("/api/v1/extensions").with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.revision").isNotEmpty())
                .andExpect(jsonPath("$.extensions[0].id").value(registration.id()))
                .andExpect(jsonPath("$.extensions[0].manifestId").value("test-extension"))
                .andExpect(jsonPath("$.extensions[0].iconUrl", startsWith("/api/v1/extensions/" + registration.id() + "/assets/")))
                .andExpect(jsonPath("$.extensions[0].layers.length()").value(1))
                .andExpect(jsonPath("$.extensions[0].layers[0].dataUrl")
                        .value("/api/v1/extensions/" + registration.id() + "/layers/vehicles/data"))
                .andExpect(jsonPath("$.extensions[0].layers[0].placement").value("behind-buildings"))
                .andExpect(jsonPath("$.extensions[0].layers[0].zIndex").value(-5))
                .andExpect(jsonPath("$.extensions[0].layers[0].mapping.color").value("red"))
                .andExpect(jsonPath("$.extensions[0].layers[0].mapping.endpoint").doesNotExist())
                .andExpect(jsonPath("$.extensions[0].layers[0].mapping.token").doesNotExist())
                .andExpect(content().string(not(containsString(baseUrl))));

        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .param("bbox", "8.0,47.0,9.0,48.0").param("zoom", "10")
                        .param("width", "800").param("height", "600")
                        .param("date", "2026-11-22")
                        .header("Cookie", "session=must-not-leak")
                        .with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"source\":\"upstream\"}"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertThat(dataAuthorization.get()).isEqualTo("Bearer test-secret");
        assertThat(dataCookie.get()).isNull();
        assertThat(dataQuery.get()).contains("fixed=yes").contains("bbox=8.0%2C47.0%2C9.0%2C48.0")
                .contains("zoom=10").contains("width=800").contains("height=600").contains("date=2026-11-22");

        String metadata = mockMvc.perform(get("/api/v1/extensions").with(user(testingService.randomUser())))
                .andReturn().getResponse().getContentAsString();
        String assetPrefix = "/api/v1/extensions/" + registration.id() + "/assets/";
        String hashedIcon = metadata.split(assetPrefix)[1].split("\"")[0];
        mockMvc.perform(get("/api/v1/extensions/{id}/assets/{asset}", registration.id(), hashedIcon)
                        .with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
        mockMvc.perform(get("/api/v1/extensions/{id}/assets/not-declared", registration.id())
                        .with(user(testingService.randomUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAndDuplicateManifestsAreRejected() {
        String duplicateLayers = """
                {"id":"bad","name":"Bad","version":"1","layers":[
                  {"id":"same","type":"static","protocol":"http","endpoint":"/one","renderer":"PathLayer","format":"json"},
                  {"id":"same","type":"static","protocol":"http","endpoint":"/two","renderer":"PathLayer","format":"json"}
                ]}
                """;
        assertThatThrownBy(() -> manifestService.parse(duplicateLayers.getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json"))).isInstanceOf(ExtensionException.class)
                .hasMessageContaining("unique");

        catalogService.create(baseUrl + "/manifest.json", null);
        assertThatThrownBy(() -> catalogService.create(baseUrl + "/manifest.json", null))
                .isInstanceOf(ExtensionException.class).hasMessageContaining("already registered");
        assertThat(repository.count()).isEqualTo(1);

        assertThatThrownBy(() -> manifestService.parse("{\"id\":\"bad\"}".getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json"))).isInstanceOf(ExtensionException.class);

        String scenegraph = """
                {"id":"3d","name":"3D","version":"1","layers":[{
                  "id":"buses","type":"polling","protocol":"http","endpoint":"/data","renderer":"ScenegraphLayer",
                  "format":"json","assets":{"modelUrl":"/bus.glb","trainModelUrl":"/train.glb"}
                }]}
                """;
        assertThat(manifestService.parse(scenegraph.getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json")).layers().getFirst().renderer())
                .isEqualTo("ScenegraphLayer");
        assertThatThrownBy(() -> manifestService.parse(scenegraph.replace("\"modelUrl\":\"/bus.glb\"", "\"iconUrl\":\"/bus.svg\"").getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json"))).isInstanceOf(ExtensionException.class);
    }

    @Test
    void placementAndZIndexAreValidated() {
        assertThat(manifestService.parse(manifest("1.0.0").getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json")).layers().getFirst().placement())
                .isEqualTo("behind-buildings");

        String invalidPlacement = manifest("1.0.0")
                .replace("\"placement\":\"behind-buildings\"", "\"placement\":\"underwater\"");
        assertThatThrownBy(() -> manifestService.parse(invalidPlacement.getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json")))
                .isInstanceOf(ExtensionException.class).hasMessageContaining("placement");

        String invalidZIndex = manifest("1.0.0")
                .replace("\"zIndex\":-5", "\"zIndex\":10001");
        assertThatThrownBy(() -> manifestService.parse(invalidZIndex.getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json")))
                .isInstanceOf(ExtensionException.class).hasMessageContaining("zIndex");

        String defaultPlacement = manifest("1.0.0")
                .replace(",\"placement\":\"behind-buildings\",\"zIndex\":-5", "");
        var layer = manifestService.parse(defaultPlacement.getBytes(StandardCharsets.UTF_8),
                java.net.URI.create(baseUrl + "/manifest.json")).layers().getFirst();
        assertThat(layer.placement()).isEqualTo("top");
        assertThat(layer.zIndex()).isZero();
    }

    @Test
    void scenegraphModelMapsAreValidatedAndProxied() {
        manifest.set("""
                {"id":"models","name":"Models","version":"1","layers":[{
                  "id":"vehicles","type":"polling","protocol":"http","endpoint":"/data","renderer":"ScenegraphLayer",
                  "format":"json","assets":{"modelUrl":"/asset.png","models":{
                    "ice":{"modelUrl":"/train.glb","scale":4,"minZoom":5}
                  }},"mapping":{"modelField":"model"}}
                ]}
                """);
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);

        var layer = catalogService.catalog().extensions().getFirst().layers().getFirst();
        assertThat(layer.mapping()).containsEntry("modelField", "model");
        assertThat(layer.assets()).containsKey("models");
        assertThat(layer.assets().toString()).contains("/api/v1/extensions/" + registration.id() + "/assets/")
                .doesNotContain(baseUrl);
        assertThat(manifestService.parseStored(registration.manifestJson()).layers().getFirst().assetUrls()).hasSize(2);
    }

    @Test
    void failedRefreshRetainsLastKnownGoodManifest() {
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);
        manifest.set("not-json");

        catalogService.refresh(registration.id());

        ExtensionRegistration failed = repository.findById(registration.id()).orElseThrow();
        assertThat(failed.version()).isEqualTo("1.0.0");
        assertThat(manifestService.parseStored(failed.manifestJson()))
                .isEqualTo(manifestService.parseStored(registration.manifestJson()));
        assertThat(failed.lastRefreshSuccess()).isFalse();
        assertThat(failed.lastError()).contains("valid JSON").doesNotContain("not-json");
        assertThat(catalogService.catalog().extensions()).hasSize(1);
    }

    @Test
    void disabledRegistrationDisappearsAndCannotBeProxied() throws Exception {
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);
        catalogService.toggle(registration.id());

        assertThat(catalogService.catalog().extensions()).isEmpty();
        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .with(user(testingService.randomUser())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/extensions/{id}/assets/unknown", registration.id())
                        .with(user(testingService.randomUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsRequireAdminAndCsrf() throws Exception {
        mockMvc.perform(get("/settings/extensions").with(user(testingService.admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registered Extensions")));
        mockMvc.perform(get("/settings/extensions").with(user(testingService.randomUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/extensions/{id}/toggle", 1).with(user(testingService.admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/extensions")
                        .param("url", baseUrl + "/manifest.json")
                        .with(csrf()).with(user(testingService.randomUser())))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();

        mockMvc.perform(post("/settings/extensions")
                        .param("url", baseUrl + "/manifest.json")
                        .with(csrf()).with(user(testingService.admin())))
                .andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void pollingCacheAndViewportValidationAreBounded() throws Exception {
        catalogService.create(baseUrl + "/manifest.json", null);
        ExtensionRegistration registration = repository.findAll().getFirst();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                            .with(user(testingService.randomUser())))
                    .andExpect(status().isOk());
        }
        assertThat(dataCalls.get()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .param("bbox", "invalid").with(user(testingService.randomUser())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .param("zoom", "31").with(user(testingService.randomUser())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .param("date", "not-a-date").with(user(testingService.randomUser())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void intervalLayersIgnoreViewportCacheBusting() throws Exception {
        manifest.set(manifest("1.0.0").replace("\"pollTrigger\":\"both\"", "\"pollTrigger\":\"interval\""));
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);

        for (String bbox : List.of("8.0,47.0,9.0,48.0", "10.0,47.0,11.0,48.0")) {
            mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                            .param("bbox", bbox).with(user(testingService.randomUser())))
                    .andExpect(status().isOk());
        }

        assertThat(dataCalls.get()).isEqualTo(1);
        assertThat(dataQuery.get()).isEqualTo("fixed=yes");
    }

    @Test
    void credentialsAreOnlyForwardedToTheManifestOrigin() throws Exception {
        AtomicReference<String> crossOriginAuthorization = new AtomicReference<>();
        HttpServer otherOrigin = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        otherOrigin.createContext("/other-data", exchange -> {
            crossOriginAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        });
        otherOrigin.createContext("/other-asset", exchange -> {
            crossOriginAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "image/png", new byte[]{1, 2, 3});
        });
        otherOrigin.start();
        try {
            String otherBaseUrl = "http://localhost:" + otherOrigin.getAddress().getPort();
            manifest.set(manifest("1.0.0")
                    .replace("\"/data?fixed=yes\"", "\"" + otherBaseUrl + "/other-data\"")
                    .replace("\"/asset.png\"", "\"" + otherBaseUrl + "/other-asset\""));
            ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", "test-secret");

            mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                            .with(user(testingService.randomUser())))
                    .andExpect(status().isOk());
            assertThat(crossOriginAuthorization.get()).isNull();

            String metadata = mockMvc.perform(get("/api/v1/extensions").with(user(testingService.randomUser())))
                    .andReturn().getResponse().getContentAsString();
            String assetPrefix = "/api/v1/extensions/" + registration.id() + "/assets/";
            String hashedIcon = metadata.split(assetPrefix)[1].split("\"")[0];
            mockMvc.perform(get("/api/v1/extensions/{id}/assets/{asset}", registration.id(), hashedIcon)
                            .with(user(testingService.randomUser())))
                    .andExpect(status().isOk());
            assertThat(crossOriginAuthorization.get()).isNull();
        } finally {
            otherOrigin.stop(0);
        }
    }

    @Test
    void explicitlyAllowedPrivateAddressIsAccepted() {
        assertThat(extensionHttpClient.validateUrl("http://192.168.2.200:8094/reitti-extension.json").getHost())
                .isEqualTo("192.168.2.200");
    }

    @Test
    void nonPublicSpecialUseAddressesAreRejected() {
        assertThatThrownBy(() -> extensionHttpClient.validateUrl("http://100.100.100.200/manifest.json"))
                .isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> extensionHttpClient.validateUrl("http://198.18.0.1/manifest.json"))
                .isInstanceOf(ExtensionException.class);
        assertThatThrownBy(() -> extensionHttpClient.validateUrl("http://240.0.0.1/manifest.json"))
                .isInstanceOf(ExtensionException.class);
    }

    @Test
    void changingManifestOriginDoesNotReuseStoredCredentials() throws IOException {
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", "test-secret");
        AtomicReference<String> newOriginAuthorization = new AtomicReference<>();
        HttpServer otherOrigin = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        otherOrigin.createContext("/manifest.json", exchange -> {
            newOriginAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "application/json", manifest.get().getBytes(StandardCharsets.UTF_8));
        });
        otherOrigin.start();
        try {
            catalogService.update(registration.id(),
                    "http://localhost:" + otherOrigin.getAddress().getPort() + "/manifest.json", null, false);

            assertThat(newOriginAuthorization.get()).isNull();
            assertThat(repository.findById(registration.id()).orElseThrow().encryptedCredentials()).isNull();
        } finally {
            otherOrigin.stop(0);
        }
    }

    @Test
    void staleAdministrativeWritesCannotUndoAToggle() {
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);
        ExtensionRegistration stale = repository.findById(registration.id()).orElseThrow();
        catalogService.toggle(registration.id());

        assertThatThrownBy(() -> repository.save(stale.withEnabled(true)))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(repository.findById(registration.id()).orElseThrow().enabled()).isFalse();
    }

    @Test
    void stalledResponseBodiesAreBoundedByTheRequestTimeout() throws Exception {
        manifest.set(manifest("1.0.0").replace("/data?fixed=yes", "/slow-data"));
        ExtensionRegistration registration = catalogService.create(baseUrl + "/manifest.json", null);

        mockMvc.perform(get("/api/v1/extensions/{id}/layers/vehicles/data", registration.id())
                        .with(user(testingService.randomUser())))
                .andExpect(status().isGatewayTimeout());
    }

    private String manifest(String version) {
        return """
                {
                  "$schema":"https://schemas.example.com/extension.json",
                  "id":"test-extension","name":"Test Extension","version":"%s","description":"Demo",
                  "icon":"/asset.png",
                  "layers":[
                    {"id":"vehicles","type":"polling","protocol":"http","endpoint":"/data?fixed=yes",
                     "pollIntervalMs":30000,"pollTrigger":"both","renderer":"IconLayer","format":"json",
                     "dataPath":"$.items","assets":{"iconUrl":"/asset.png"},
                     "mapping":{"color":"red","endpoint":"https://must-not-leak.example","token":"hidden"},
                     "minZoom":3,"staleAfterMs":60000,"placement":"behind-buildings","zIndex":-5},
                    {"id":"stream","type":"streaming","protocol":"websocket","endpoint":"wss://stream.example",
                     "renderer":"PathLayer"}
                  ]
                }
                """.formatted(version);
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
