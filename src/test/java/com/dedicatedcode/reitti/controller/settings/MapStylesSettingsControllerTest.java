package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.map.MapStyleDataSource;
import com.dedicatedcode.reitti.model.map.MapStyleVectorOptions;
import com.dedicatedcode.reitti.model.map.UserMapStyle;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserMapStyleJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class MapStylesSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserMapStyleJdbcService userMapStyleJdbcService;

    private MockHttpServletRequestBuilder saveRasterStyle(String name, String template) {
        return post("/settings/map-styles")
                .param("name", name)
                .param("mapType", "raster")
                .param("styleInputType", "json")
                .param("rasterSourceInputType", "url-template")
                .param("rasterTileTemplate", template)
                .param("proxyTiles", "on")
                .param("shared", "on");
    }

    private Optional<UserMapStyle> findByName(User user, String name) {
        return userMapStyleJdbcService.findAll(user).stream().filter(style -> name.equals(style.name())).findFirst();
    }

    @Test
    void nonAdminsCannotProxyOrShareStyles() throws Exception {
        User user = testingService.randomUser();
        String name = "UserStyle_" + UUID.randomUUID();

        mockMvc.perform(saveRasterStyle(name, "https://203.0.113.10/{z}/{x}/{y}.png").with(user(user)))
                .andExpect(status().isOk());

        UserMapStyle saved = findByName(user, name).orElseThrow();
        assertThat(saved.shared()).isFalse();
        assertThat(saved.dataSource().proxyTiles()).isFalse();
        assertThat(findByName(testingService.randomUser(), name)).isEmpty();
    }

    @Test
    void adminsCanProxyAndShareStyles() throws Exception {
        User admin = testingService.admin();
        String name = "AdminStyle_" + UUID.randomUUID();

        mockMvc.perform(saveRasterStyle(name, "https://203.0.113.10/{z}/{x}/{y}.png").with(user(admin)))
                .andExpect(status().isOk());

        UserMapStyle saved = findByName(admin, name).orElseThrow();
        assertThat(saved.shared()).isTrue();
        assertThat(saved.dataSource().proxyTiles()).isTrue();
        assertThat(findByName(testingService.randomUser(), name)).isPresent();
    }

    @Test
    void styleUrlsPointingToInternalAddressesAreRejected() throws Exception {
        User admin = testingService.admin();
        String name = "MetadataStyle_" + UUID.randomUUID();

        mockMvc.perform(saveRasterStyle(name, "http://169.254.169.254/latest/meta-data/{z}/{x}/{y}").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#errors"));
        mockMvc.perform(post("/settings/map-styles")
                        .param("name", name)
                        .param("mapType", "vector")
                        .param("styleInputType", "url")
                        .param("vectorStyleUrl", "http://127.0.0.1:8080/actuator/env")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Retarget", "#errors"));

        assertThat(findByName(admin, name)).isEmpty();
    }

    @Test
    void usersCannotDeleteSharedStylesOfOthers() throws Exception {
        User admin = testingService.admin();
        User user = testingService.randomUser();
        String name = "SharedStyle_" + UUID.randomUUID();
        UserMapStyle shared = userMapStyleJdbcService.save(admin, new UserMapStyle(null, admin.getId(), name, "raster", "json",
                "url-template", null, null,
                new MapStyleDataSource(null, "raster", null, "https://203.0.113.10/{z}/{x}/{y}.png", null, null, null, 256, null, false),
                new MapStyleVectorOptions(null, null, null), false, true, null));

        mockMvc.perform(delete("/settings/map-styles").param("id", shared.id().toString()).with(user(user)))
                .andExpect(status().isForbidden());
        assertThat(findByName(user, name)).isPresent();

        UserMapStyle defaultStyle = userMapStyleJdbcService.findAll(user).stream().filter(UserMapStyle::defaultStyle).findFirst().orElseThrow();
        mockMvc.perform(delete("/settings/map-styles").param("id", defaultStyle.id().toString()).with(user(user)))
                .andExpect(status().isForbidden());
        assertThat(userMapStyleJdbcService.findById(user, defaultStyle.id())).isPresent();

        mockMvc.perform(delete("/settings/map-styles").param("id", shared.id().toString()).with(user(admin)))
                .andExpect(status().isOk());
        assertThat(findByName(user, name)).isEmpty();
    }

    @Test
    void sharedStyleNamesCannotBreakOutOfTheInlineScript() throws Exception {
        User admin = testingService.admin();
        String payload = "</script><script>alert(document.cookie)</script>";
        UserMapStyle shared = userMapStyleJdbcService.save(admin, new UserMapStyle(null, admin.getId(), payload, "raster", "json",
                "url-template", null, null,
                new MapStyleDataSource(null, "raster", null, "https://203.0.113.10/{z}/{x}/{y}.png", null, null, null, 256, null, false),
                new MapStyleVectorOptions(null, null, null), false, true, null));
        try {
            mockMvc.perform(get("/settings/map-styles").with(user(testingService.randomUser())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(payload))))
                    .andExpect(content().string(containsString("\\u003c/script\\u003e\\u003cscript\\u003ealert(document.cookie)")));
        } finally {
            userMapStyleJdbcService.delete(admin, shared.id(), true);
        }
    }
}
