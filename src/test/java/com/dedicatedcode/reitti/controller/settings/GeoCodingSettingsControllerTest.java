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
                        .param("url", "https://nominatim.openstreetmap.org")
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
}
