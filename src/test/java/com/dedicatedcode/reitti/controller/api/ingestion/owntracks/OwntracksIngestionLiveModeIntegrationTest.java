package com.dedicatedcode.reitti.controller.api.ingestion.owntracks;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureWebMvc
class OwntracksIngestionLiveModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;

    @Autowired
    private UserJdbcService userJdbcService;

    @MockitoBean
    private ReittiIntegrationService reittiIntegrationService;

    @Test
    void shouldKeepOnlyLatestLocationForLiveDataOnlyUser() throws Exception {
        User user = testingService.randomUser();
        User liveDataOnlyUser = user.withUserType(UserType.LIVE_DATA_ONLY);
        userJdbcService.updateUser(liveDataOnlyUser);
        Device device = testingService.findDefaultDevice(liveDataOnlyUser);

        assertTrue(rawLocationPointJdbcService.findLatest(liveDataOnlyUser).isEmpty());

        sendLocation(liveDataOnlyUser, device, 60.1699, 24.9384, 1699545600, 5.0);
        sendLocation(liveDataOnlyUser, device, 60.1705, 24.9410, 1717200000, 3.0);
        sendLocation(liveDataOnlyUser, device, 60.1600, 24.9300, 1699552800, 4.0);

        await("waiting for batch to flush and pipeline to process")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> rawLocationPointJdbcService.findLatest(liveDataOnlyUser).isPresent());

        Optional<RawLocationPoint> latest = rawLocationPointJdbcService.findLatest(liveDataOnlyUser);
        assertTrue(latest.isPresent());
        assertEquals(60.1705, latest.get().getLatitude(), 0.0001,
                "latest should be the point2 (June 2024), got lat " + latest.get().getLatitude());
        assertTrue(latest.get().getTimestamp().isAfter(Instant.parse("2024-01-01T00:00:00Z")),
                "latest should be from June 2024, got " + latest.get().getTimestamp());

        Optional<SourceLocationPoint> latestSource = sourceLocationPointJdbcService.findLatest(liveDataOnlyUser, device);
        assertTrue(latestSource.isPresent());
        assertTrue(latestSource.get().getTimestamp().isAfter(Instant.parse("2024-01-01T00:00:00Z")),
                "source points should keep only the chronologically latest, got " + latestSource.get().getTimestamp());
    }

    private void sendLocation(User user, Device device, double lat, double lon, long tst, double acc) throws Exception {
        String payload = """
                {
                    "_type": "location",
                    "lat": %s,
                    "lon": %s,
                    "tst": %d,
                    "acc": %s
                }
                """.formatted(lat, lon, tst, acc);

        mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(new DeviceTokenUser(user, device)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
