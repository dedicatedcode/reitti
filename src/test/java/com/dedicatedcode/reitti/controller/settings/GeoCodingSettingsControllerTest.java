package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geocoding.GeocoderType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.GeocodeServiceJdbcService;
import com.dedicatedcode.reitti.service.geocoding.GeocodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class GeoCodingSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private GeocodeServiceJdbcService geocodeServiceJdbcService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = testingService.admin();
    }

    @Test
    void updateService_WithTypeSwitch_ShouldUpdateExistingService() throws Exception {
        // Given an existing PHOTON service
        GeocodeService existing = geocodeServiceJdbcService.save(new GeocodeService(
                "UpdateTest_" + UUID.randomUUID(),
                "https://photon.example.com",
                true, 0, null, null,
                GeocoderType.PHOTON, 1, Map.of()
        ));
        long countBefore = geocodeServiceJdbcService.count();

        // When switching the type and submitting the update with the id
        mockMvc.perform(post("/settings/geocode-services")
                        .param("id", existing.getId().toString())
                        .param("name", existing.getName())
                        .param("url", "http://192.168.1.40:8080")
                        .param("type", GeocoderType.NOMINATIM.name())
                        .param("priority", "2")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/geocode-services :: geocode-services-content"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        // Then the existing row is updated, not duplicated
        assertThat(geocodeServiceJdbcService.count()).isEqualTo(countBefore);
        GeocodeService updated = geocodeServiceJdbcService.findById(existing.getId()).orElseThrow();
        assertThat(updated.getType()).isEqualTo(GeocoderType.NOMINATIM);
        assertThat(updated.getName()).isEqualTo(existing.getName());
        assertThat(updated.getPriority()).isEqualTo(2);
    }

    @Test
    void typeFields_WhenEditingService_ShouldPreserveHiddenId() throws Exception {
        // Given an existing service
        GeocodeService existing = geocodeServiceJdbcService.save(new GeocodeService(
                "TypeFieldsTest_" + UUID.randomUUID(),
                "https://photon.example.com",
                true, 0, null, null,
                GeocoderType.PHOTON, 1, Map.of()
        ));

        // When re-fetching the type-specific fields after switching the type
        mockMvc.perform(get("/settings/geocode-services/type-fields")
                        .param("type", GeocoderType.NOMINATIM.name())
                        .param("id", existing.getId().toString())
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/fragments/geocoding :: type-fields"))
                .andExpect(content().string(containsString("name=\"id\"")))
                .andExpect(content().string(containsString(existing.getId().toString())));
    }

    @Test
    void nonAdminsCannotManageGeocodingServices() throws Exception {
        User user = testingService.randomUser();
        GeocodeService existing = geocodeServiceJdbcService.save(new GeocodeService(
                "NonAdminTest_" + UUID.randomUUID(),
                "https://photon.example.com",
                true, 0, null, null,
                GeocoderType.PHOTON, 1, Map.of()
        ));
        long countBefore = geocodeServiceJdbcService.count();

        mockMvc.perform(post("/settings/geocode-services")
                        .param("name", "Harvester")
                        .param("url", "http://192.168.1.41:8080/reverse?lat={lat}&lon={lng}")
                        .param("type", GeocoderType.GEOCODE_JSON.name())
                        .param("priority", "1")
                        .with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/geocode-services")
                        .param("id", existing.getId().toString())
                        .param("name", existing.getName())
                        .param("url", "http://192.168.1.41:8080")
                        .param("type", GeocoderType.PHOTON.name())
                        .param("priority", "1")
                        .with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/geocode-services/" + existing.getId() + "/toggle").with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/geocode-services/" + existing.getId() + "/reset-errors").with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/geocode-services/" + existing.getId() + "/delete").with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/geocode-services/test-config")
                        .param("type", GeocoderType.PHOTON.name())
                        .param("url", "http://192.168.1.41:8080")
                        .with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings/geocode-services/edit/" + existing.getId()).with(user(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings/geocode-services/type-fields")
                        .param("type", GeocoderType.PHOTON.name())
                        .param("id", existing.getId().toString())
                        .with(user(user)))
                .andExpect(status().isForbidden());

        assertThat(geocodeServiceJdbcService.count()).isEqualTo(countBefore);
        GeocodeService unchanged = geocodeServiceJdbcService.findById(existing.getId()).orElseThrow();
        assertThat(unchanged.getUrl()).isEqualTo("https://photon.example.com");
        assertThat(unchanged.isEnabled()).isTrue();
    }

    @Test
    void nonAdminsOnlySeeAReadOnlyList() throws Exception {
        User user = testingService.randomUser();
        geocodeServiceJdbcService.save(new GeocodeService(
                "ReadOnlyTest_" + UUID.randomUUID(),
                "https://secret-host.example.com",
                true, 0, null, null,
                GeocoderType.PHOTON, 1, Map.of()
        ));

        mockMvc.perform(get("/settings/geocode-services").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ReadOnlyTest_")))
                .andExpect(content().string(not(containsString("secret-host.example.com"))))
                .andExpect(content().string(not(containsString("geocode-service-form"))));
    }

    @Test
    void storedApiKeysAreNotRenderedAndKeptWhenLeftEmpty() throws Exception {
        String apiKey = "stored-secret-" + UUID.randomUUID();
        GeocodeService existing = geocodeServiceJdbcService.save(new GeocodeService(
                "ApiKeyTest_" + UUID.randomUUID(),
                "https://api.geoapify.com",
                true, 0, null, null,
                GeocoderType.GEO_APIFY, 1, Map.of("apiKey", apiKey)
        ));

        mockMvc.perform(get("/settings/geocode-services/edit/" + existing.getId()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(apiKey))));
        mockMvc.perform(get("/settings/geocode-services/type-fields")
                        .param("type", GeocoderType.GEO_APIFY.name())
                        .param("id", existing.getId().toString())
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(apiKey))));

        mockMvc.perform(post("/settings/geocode-services")
                        .param("id", existing.getId().toString())
                        .param("name", existing.getName())
                        .param("type", GeocoderType.GEO_APIFY.name())
                        .param("apiKey", "")
                        .param("priority", "3")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("errorMessage"))
                .andExpect(content().string(not(containsString(apiKey))));

        GeocodeService updated = geocodeServiceJdbcService.findById(existing.getId()).orElseThrow();
        assertThat(updated.getPriority()).isEqualTo(3);
        assertThat(updated.getAdditionalParameters()).containsEntry("apiKey", apiKey);
    }

    @Test
    void geocoderUrlsPointingToLoopbackAreRejected() throws Exception {
        long countBefore = geocodeServiceJdbcService.count();

        mockMvc.perform(post("/settings/geocode-services")
                        .param("name", "Loopback_" + UUID.randomUUID())
                        .param("url", "http://127.0.0.1:6379/?lat={lat}&lon={lng}")
                        .param("type", GeocoderType.GEOCODE_JSON.name())
                        .param("priority", "1")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeDoesNotExist("successMessage"));

        assertThat(geocodeServiceJdbcService.count()).isEqualTo(countBefore);
    }
}
