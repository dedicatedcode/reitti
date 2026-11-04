package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.PlaceInfo;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class PlacesGeocodingExecutionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private SignificantPlaceJdbcService placeJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runGeocoding_AsRegularUser_ShouldOnlyEnqueueOwnPlaces() throws Exception {
        User testUser = testingService.randomUser();
        SignificantPlace ownPlace = testingService.newSignificantPlace(testUser, 53.1, 9.2, "Own Place");
        testingService.newSignificantPlace(testingService.randomUser(), 48.1, 11.2, "Other Place");

        MvcResult result = mockMvc.perform(post("/settings/places/run-geocoding").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/places :: places-content"))
                // the success message counts the enqueued places, so it reveals how many were picked up
                .andExpect(model().attribute("successMessage", containsString("1 places")))
                .andExpect(model().attributeDoesNotExist("errorMessage"))
                .andReturn();

        // and the re-rendered grid only contains the current user's places
        assertThat(places(result)).extracting(PlaceInfo::id).containsExactly(ownPlace.getId());
    }

    @Test
    void runGeocoding_WithoutPlaces_ShouldReportNoPlaces() throws Exception {
        mockMvc.perform(post("/settings/places/run-geocoding").with(csrf()).with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("successMessage", containsString("No places found to geocode")))
                .andExpect(model().attributeDoesNotExist("errorMessage"));
    }

    @Test
    void clearAndRerunDialog_AsRegularUser_ShouldOnlyCountOwnPlaces() throws Exception {
        User testUser = testingService.randomUser();
        testingService.newSignificantPlace(testUser, 53.1, 9.2, "Own Place");
        testingService.newSignificantPlace(testUser, 53.2, 9.3, "Own Place 2");
        testingService.newSignificantPlace(testingService.randomUser(), 48.1, 11.2, "Other Place");

        mockMvc.perform(get("/settings/places/clear-and-rerun-dialog").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/fragments/geocoding :: clear-and-rerun-dialog"))
                .andExpect(model().attribute("placeCount", 2))
                .andExpect(content().string(containsString("2 places")));
    }

    @Test
    void clearAndRerunDialog_WithoutPlaces_ShouldCountZero() throws Exception {
        mockMvc.perform(get("/settings/places/clear-and-rerun-dialog").with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/fragments/geocoding :: clear-and-rerun-dialog"))
                .andExpect(model().attribute("placeCount", 0));
    }

    @Test
    void clearAndRerun_ShouldCreateSingleBulkJob() throws Exception {
        User testUser = testingService.randomUser();
        testingService.newSignificantPlace(testUser, 53.1, 9.2, "Place 1");
        testingService.newSignificantPlace(testUser, 53.2, 9.3, "Place 2");

        mockMvc.perform(post("/settings/places/clear-and-rerun").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/places :: places-content"));

        Integer bulkJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_meta_data WHERE user_id = ? AND friendly_name = ?",
                Integer.class, testUser.getId(), "Clear and re-geocode all places");
        Integer manualJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_meta_data WHERE user_id = ? AND friendly_name = ?",
                Integer.class, testUser.getId(), "Manual reverse geocoding");

        assertThat(bulkJobs).isEqualTo(1);
        assertThat(manualJobs).isZero();
    }

    @Test
    void clearAndRerun_AsRegularUser_ShouldOnlyClearOwnPlaces() throws Exception {
        User testUser = testingService.randomUser();
        SignificantPlace ownPlace = testingService.newSignificantPlace(testUser, 53.3, 9.4, "Own Geocoded");
        placeJdbcService.update(testUser, ownPlace.withGeocoded(true).withAddress("Own Address"));

        mockMvc.perform(post("/settings/places/clear-and-rerun").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/places :: places-content"))
                .andExpect(model().attribute("successMessage", containsString("1 places")))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        // the clear and re-geocode happens inside the bulk job, so wait for it to finish
        testingService.awaitExpected(db -> db.queryForObject(
                "SELECT COUNT(*) FROM job_meta_data WHERE user_id = ? AND friendly_name = ? AND status = ?",
                Integer.class, testUser.getId(), "Clear and re-geocode all places", "COMPLETED") == 1, 30);

        SignificantPlace reloaded = placeJdbcService.findById(ownPlace.getId()).orElseThrow();
        assertThat(reloaded.isGeocoded()).isTrue();
        assertThat(reloaded.getAddress()).isNotEqualTo("Own Address");
    }

    @Test
    void clearAndRerun_AsRegularUser_ShouldNotClearOtherUsersPlaces() throws Exception {
        User testUser = testingService.randomUser();
        User otherUser = testingService.randomUser();
        SignificantPlace otherPlace = testingService.newSignificantPlace(otherUser, 48.5, 11.5, "Other Geocoded");
        placeJdbcService.update(otherUser, otherPlace.withGeocoded(true).withAddress("Other Address"));

        mockMvc.perform(post("/settings/places/clear-and-rerun").with(csrf()).with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("successMessage", containsString("No places found to geocode")));

        // the other user's geocoded place is untouched
        SignificantPlace reloaded = placeJdbcService.findById(otherPlace.getId()).orElseThrow();
        assertThat(reloaded.isGeocoded()).isTrue();
        assertThat(reloaded.getAddress()).isEqualTo("Other Address");
    }

    @SuppressWarnings("unchecked")
    private List<PlaceInfo> places(MvcResult result) {
        return (List<PlaceInfo>) result.getModelAndView().getModel().get("places");
    }
}
