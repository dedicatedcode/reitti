package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MapStylesSettingsControllerTest {

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
