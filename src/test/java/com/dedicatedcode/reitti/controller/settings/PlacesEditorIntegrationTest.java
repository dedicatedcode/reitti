package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PlacesEditorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;

    private User user;
    private SignificantPlace place;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
        this.place = testingService.newSignificantPlace(user, "Test Place");
    }

    @Test
    void shouldRenderFullEditPage() throws Exception {
        mockMvc.perform(get("/settings/places/{id}/edit", place.getId()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("place-edit-root")))
                .andExpect(content().string(containsString("polygon-form")))
                .andExpect(content().string(containsString("place-search-input")));
    }

    @Test
    void shouldReturnEditFormFragment() throws Exception {
        mockMvc.perform(get("/settings/places/{id}/edit-form", place.getId()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("place-edit-root")))
                .andExpect(content().string(containsString("polygon-form")))
                .andExpect(content().string(containsString("Test Place")));
    }

    @Test
    void shouldForbidEditFormForForeignPlace() throws Exception {
        User other = testingService.randomUser();
        mockMvc.perform(get("/settings/places/{id}/edit-form", place.getId()).with(user(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnSearchResultsFragment() throws Exception {
        testingService.newSignificantPlace(user, 53.87172110622166, 10.747495611916795, "Bus Stop Alpha");
        mockMvc.perform(get("/settings/places/search-fragment").param("search", "Bus").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("search-result")))
                .andExpect(content().string(containsString("Bus Stop Alpha")));
    }

    @Test
    void shouldReturnShowMoreButtonWhenMorePagesExist() throws Exception {
        for (int i = 0; i < 12; i++) {
            testingService.newSignificantPlace(user, 53.0 + i * 0.1, 10.0 + i * 0.1, "Place " + i);
        }
        String body = mockMvc.perform(get("/settings/places/search-fragment")
                        .param("search", "Place")
                        .param("page", "0")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("Show more");
        org.assertj.core.api.Assertions.assertThat(body).contains("page=1");
    }
}
