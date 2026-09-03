package com.dedicatedcode.reitti.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PanoramaxServiceTest {

    private static final String SEARCH_RESPONSE = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "id": "f75cc63c-696d-4b43-988c-ceda9064b02c",
                  "bbox": [13.397461, 52.509064, 13.397461, 52.509064],
                  "type": "Feature",
                  "links": [
                    {"rel": "license", "href": "https://creativecommons.org/licenses/by-sa/4.0/", "title": "License for this object (CC-BY-SA-4.0)"},
                    {"rel": "self", "href": "https://api.panoramax.xyz/api/collections/35c03fdc/items/f75cc63c", "type": "application/geo+json"}
                  ],
                  "providers": [{"id": "db49c677", "name": "p4n-pics", "roles": ["producer"]}],
                  "collection": "35c03fdc-bb47-4a01-92b7-b602d04369ee",
                  "properties": {"datetime": "2024-09-11T10:00:00+00:00"}
                }
              ]
            }
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        this.restTemplate = new RestTemplate();
        this.server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void findNearby_WithMatchingPicture_ShouldReturnParsedPicture() {
        server.expect(requestTo(containsString("https://api.panoramax.example/api/search?bbox=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        PanoramaxService service = new PanoramaxService(restTemplate,
                new tools.jackson.databind.ObjectMapper(),
                "https://api.panoramax.example/api",
                "");

        assertThat(service.findNearby(52.5091, 13.3975)).hasValueSatisfying(picture -> {
            assertThat(picture.pictureId()).isEqualTo("f75cc63c-696d-4b43-988c-ceda9064b02c");
            assertThat(picture.sequenceId()).isEqualTo("35c03fdc-bb47-4a01-92b7-b602d04369ee");
            assertThat(picture.datetime()).isEqualTo("2024-09-11T10:00:00+00:00");
            assertThat(picture.providerName()).isEqualTo("p4n-pics");
            assertThat(picture.licenseUrl()).isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/");
            assertThat(picture.licenseName()).isEqualTo("CC-BY-SA-4.0");
        });
        server.verify();
    }

    @Test
    void findNearby_WithoutPictures_ShouldReturnEmpty() {
        server.expect(requestTo(containsString("/api/search?bbox=")))
                .andRespond(withSuccess("{\"type\": \"FeatureCollection\", \"features\": []}", MediaType.APPLICATION_JSON));

        PanoramaxService service = new PanoramaxService(restTemplate,
                new tools.jackson.databind.ObjectMapper(),
                "https://api.panoramax.example/api",
                "");

        assertThat(service.findNearby(52.5091, 13.3975)).isEmpty();
        server.verify();
    }

    @Test
    void findNearby_WithServerError_ShouldReturnEmpty() {
        server.expect(requestTo(containsString("/api/search?bbox=")))
                .andRespond(withServerError());

        PanoramaxService service = new PanoramaxService(restTemplate,
                new tools.jackson.databind.ObjectMapper(),
                "https://api.panoramax.example/api",
                "");

        assertThat(service.findNearby(52.5091, 13.3975)).isEmpty();
        server.verify();
    }

    @Test
    void findNearby_WithDisabledService_ShouldReturnEmptyWithoutQuery() {
        PanoramaxService service = new PanoramaxService(restTemplate,
                new tools.jackson.databind.ObjectMapper(),
                "",
                "");

        assertThat(service.findNearby(52.5091, 13.3975)).isEmpty();
        server.verify();
    }

    @Test
    void findNearby_WithTileCache_ShouldRouteThroughCacheContainer() {
        server.expect(requestTo(containsString("http://tile-cache.internal/panoramax/search/api/search?bbox=")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Reitti-Upstream-Url", containsString("https://api.panoramax.example/api/search?bbox=")))
                .andRespond(withSuccess(SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

        PanoramaxService service = new PanoramaxService(restTemplate,
                new tools.jackson.databind.ObjectMapper(),
                "https://api.panoramax.example/api",
                "http://tile-cache.internal");

        assertThat(service.findNearby(52.5091, 13.3975)).isPresent();
        server.verify();
    }
}
