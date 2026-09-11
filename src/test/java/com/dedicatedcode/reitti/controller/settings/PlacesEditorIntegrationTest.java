package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PlacesEditorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private SuppressedVisitJdbcService suppressedVisitJdbcService;
    @Autowired
    private NoVisitZoneJdbcService noVisitZoneJdbcService;

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
    void shouldRenderEditorWithoutSelectedPlace() throws Exception {
        String body = mockMvc.perform(get("/settings/places/editor").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("navigation-container")))
                .andExpect(content().string(containsString("place-edit-root")))
                .andExpect(content().string(containsString("Select a place to edit")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("id=\"polygon-form\"");
    }

    @Test
    void shouldLinkToEditorFromVisitSensitivityPage() throws Exception {
        mockMvc.perform(get("/settings/visit-sensitivity").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/settings/places/editor")));
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

    @Test
    void shouldReturnSuppressedVisitsFragment() throws Exception {
        suppressedVisitJdbcService.create(user, new SuppressedVisit(
                place.getId(), 53.55, 9.99,
                Instant.parse("2026-08-23T08:26:20Z"), Instant.parse("2026-08-23T08:55:17Z")));

        String body = mockMvc.perform(get("/settings/places/suppressed-fragment").with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Suppressed Visits");
        assertThat(body).contains("Test Place");
        assertThat(body).contains("data-lat=\"53.55\"");
        assertThat(body).contains("Restore");
    }

    @Test
    void shouldPaginateSuppressedVisits() throws Exception {
        for (int i = 0; i < 12; i++) {
            suppressedVisitJdbcService.create(user, new SuppressedVisit(
                    place.getId(), 53.0 + i * 0.01, 10.0 + i * 0.01,
                    Instant.parse("2026-08-20T0" + (i % 10) + ":00:00Z").plusSeconds(i * 3600L),
                    Instant.parse("2026-08-20T0" + (i % 10) + ":30:00Z").plusSeconds(i * 3600L)));
        }

        String body = mockMvc.perform(get("/settings/places/suppressed-fragment")
                        .param("page", "0")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("page=1");
        assertThat(body).contains("Page 1 of 2");
    }

    @Test
    void shouldRestoreSuppressedVisit() throws Exception {
        SuppressedVisit tombstone = suppressedVisitJdbcService.create(user, new SuppressedVisit(
                place.getId(), 53.55, 9.99,
                Instant.parse("2026-08-23T08:26:20Z"), Instant.parse("2026-08-23T08:55:17Z")));

        mockMvc.perform(post("/settings/places/suppressed/{id}/restore", tombstone.id()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No suppressed visits.")));

        assertThat(suppressedVisitJdbcService.findByUser(user)).isEmpty();
    }

    @Test
    void shouldReturnSuppressedLocationsAsJson() throws Exception {
        suppressedVisitJdbcService.create(user, new SuppressedVisit(
                place.getId(), 53.55, 9.99,
                Instant.parse("2026-08-23T08:26:20Z"), Instant.parse("2026-08-23T08:55:17Z")));

        mockMvc.perform(get("/settings/places/suppressed-locations").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"latitudeCentroid\":53.55")))
                .andExpect(content().string(containsString("Test Place")));
    }

    @Test
    void shouldReturnNearbyPlacesWithinRadius() throws Exception {
        testingService.newSignificantPlace(user, 53.87172110622166, 10.747495611916795, "Nearby One");

        String body = mockMvc.perform(get("/settings/places/nearby")
                        .param("lat", "53.87")
                        .param("lng", "10.74")
                        .param("radius", "5000")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Nearby One");
    }

    @Test
    void shouldNotReturnForeignPlacesInNearbySearch() throws Exception {
        User other = testingService.randomUser();
        testingService.newSignificantPlace(other, 53.87172110622166, 10.747495611916795, "Foreign Place");

        String body = mockMvc.perform(get("/settings/places/nearby")
                        .param("lat", "53.87")
                        .param("lng", "10.74")
                        .param("radius", "5000")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Foreign Place");
    }

    @Test
    void shouldCreateAndListNoVisitZones() throws Exception {
        String polygonData = "[{\"lat\":53.5,\"lng\":9.9},{\"lat\":53.6,\"lng\":9.9},{\"lat\":53.6,\"lng\":10.0}]";

        String body = mockMvc.perform(post("/settings/places/zones")
                        .param("name", "Bus Stop Area")
                        .param("polygonData", polygonData)
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Bus Stop Area");
        assertThat(body).contains("Delete");

        assertThat(noVisitZoneJdbcService.findByUser(user)).hasSize(1);

        String locations = mockMvc.perform(get("/settings/places/zones-locations").with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(locations).contains("Bus Stop Area");
        assertThat(locations).contains("\"latitude\":53.5");
    }

    @Test
    void shouldRejectZoneWithInvalidPolygon() throws Exception {
        mockMvc.perform(post("/settings/places/zones")
                        .param("name", "Too Small")
                        .param("polygonData", "[{\"lat\":53.5,\"lng\":9.9},{\"lat\":53.6,\"lng\":9.9}]")
                        .with(user(user)))
                .andExpect(status().isBadRequest());

        assertThat(noVisitZoneJdbcService.findByUser(user)).isEmpty();
    }

    @Test
    void shouldDeleteNoVisitZone() throws Exception {
        NoVisitZone zone = noVisitZoneJdbcService.create(user, new NoVisitZone("My Zone", List.of(
                new GeoPoint(53.5, 9.9), new GeoPoint(53.6, 9.9), new GeoPoint(53.6, 10.0))));

        String body = mockMvc.perform(post("/settings/places/zones/{id}/delete", zone.id()).with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("No no-visit zones.");
        assertThat(noVisitZoneJdbcService.findByUser(user)).isEmpty();
    }

    @Test
    void shouldUpdateZoneGeometry() throws Exception {
        NoVisitZone zone = noVisitZoneJdbcService.create(user, new NoVisitZone("Editable Zone", List.of(
                new GeoPoint(53.5, 9.9), new GeoPoint(53.6, 9.9), new GeoPoint(53.6, 10.0))));

        String polygonData = "[{\"lat\":53.7,\"lng\":10.1},{\"lat\":53.8,\"lng\":10.1},{\"lat\":53.8,\"lng\":10.2}]";
        String body = mockMvc.perform(post("/settings/places/zones/{id}/update", zone.id())
                        .param("polygonData", polygonData)
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Editable Zone");

        NoVisitZone updated = noVisitZoneJdbcService.findById(user, zone.id()).orElseThrow();
        //stored WKT polygons carry the closing point as the last entry
        assertThat(updated.polygon()).hasSize(4);
        assertThat(updated.polygon().getFirst().latitude()).isEqualTo(53.7);
        assertThat(updated.polygon().getFirst().longitude()).isEqualTo(10.1);
        assertThat(updated.polygon().getLast()).isEqualTo(updated.polygon().getFirst());
    }

    @Test
    void shouldRejectZoneUpdateWithInvalidPolygon() throws Exception {
        NoVisitZone zone = noVisitZoneJdbcService.create(user, new NoVisitZone("Stable Zone", List.of(
                new GeoPoint(53.5, 9.9), new GeoPoint(53.6, 9.9), new GeoPoint(53.6, 10.0))));

        mockMvc.perform(post("/settings/places/zones/{id}/update", zone.id())
                        .param("polygonData", "[{\"lat\":53.7,\"lng\":10.1}]")
                        .with(user(user)))
                .andExpect(status().isBadRequest());

        NoVisitZone unchanged = noVisitZoneJdbcService.findById(user, zone.id()).orElseThrow();
        assertThat(unchanged.polygon()).hasSize(4);
        assertThat(unchanged.polygon().getFirst().latitude()).isEqualTo(53.5);
        assertThat(unchanged.polygon().get(1).latitude()).isEqualTo(53.6);
        assertThat(unchanged.polygon().get(2).longitude()).isEqualTo(10.0);
    }

    @Test
    void shouldNotDeleteForeignZone() throws Exception {
        User other = testingService.randomUser();
        NoVisitZone zone = noVisitZoneJdbcService.create(other, new NoVisitZone("Foreign Zone", List.of(
                new GeoPoint(53.5, 9.9), new GeoPoint(53.6, 9.9), new GeoPoint(53.6, 10.0))));

        mockMvc.perform(post("/settings/places/zones/{id}/delete", zone.id()).with(user(user)))
                .andExpect(status().isNotFound());
    }
}
