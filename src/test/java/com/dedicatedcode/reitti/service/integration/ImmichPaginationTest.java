package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImmichIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImmichPaginationTest {
    private static final String URL = "http://localhost:8089/api/search/metadata";
    private final User user = new User("test", "test");
    private MockRestServiceServer server;
    private ImmichIntegrationService service;

    @BeforeEach
    void setup() {
        var repository = mock(ImmichIntegrationJdbcService.class);
        when(repository.findByUser(user)).thenReturn(Optional.of(
            new ImmichIntegration("http://localhost:8089", "fake", null, null, false, true)));
        var restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new ImmichIntegrationService(repository, mock(RawLocationPointJdbcService.class),
            restTemplate, mock(StorageService.class));
    }

    private void page(int number, String response) {
        server.expect(requestTo(URL)).andExpect(content().json("{\"page\":" + number + "}"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private int searchCount() {
        return service.searchPhotosForRange(user, Instant.parse("2026-03-27T23:00:00Z"),
            Instant.parse("2026-03-28T22:59:59Z")).size();
    }

    private static final String GPS_PHOTO =
        "{\"id\":\"photo\",\"localDateTime\":\"2026-03-28T10:00:00Z\","
        + "\"exifInfo\":{\"latitude\":1,\"longitude\":2}}";

    @Test
    void followsNextPageAndIncludesGpsPhotoAfterFirstPage() {
        page(1, "{\"assets\":{\"items\":[{\"id\":\"no-gps\"}],\"nextPage\":\"2\"}}");
        page(2, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":null}}");
        assertEquals(1, searchCount());
        server.verify();
    }

    @Test
    void continuesPastPageWithoutGpsAndIncludesThirdPage() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":\"2\"}}");
        page(2, "{\"assets\":{\"items\":[{\"id\":\"no-gps\"}],\"nextPage\":\"3\"}}");
        page(3, "{\"assets\":{\"items\":[" + GPS_PHOTO.replace("photo", "photo-2") + "],\"nextPage\":null}}");
        assertEquals(2, searchCount());
        server.verify();
    }

    @Test
    void deduplicatesRepeatedPhotoIdsAcrossPages() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":\"2\"}}");
        page(2, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":null}}");
        assertEquals(1, searchCount());
        server.verify();
    }

    @Test
    void endsWhenNextPageIsNull() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":null}}");
        assertEquals(1, searchCount());
        server.verify();
    }

    @Test
    void discardsPartialResultWhenLaterRequestFails() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":\"2\"}}");
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertEquals(0, searchCount());
        server.verify();
    }

    @Test
    void discardsPartialResultWhenLaterItemsAreMissing() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":\"2\"}}");
        page(2, "{\"assets\":{\"nextPage\":null}}");
        assertEquals(0, searchCount());
        server.verify();
    }

    @Test
    void rejectsRepeatedNextPage() {
        page(1, "{\"assets\":{\"items\":[" + GPS_PHOTO + "],\"nextPage\":\"1\"}}");
        assertEquals(0, searchCount());
        server.verify();
    }
}
