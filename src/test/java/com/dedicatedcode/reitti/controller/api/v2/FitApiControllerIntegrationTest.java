package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class FitApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Test
    public void importFitFile_success() throws Exception {
        User user = testingService.randomUser();
        Device device = testingService.createRandomDevice(user);
        ApiToken apiToken = testingService.createApiToken(user, "test-token", device);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.fit",
                "application/octet-stream",
                new ClassPathResource("data/fit/sample.fit").getInputStream()
        );

        mockMvc.perform(multipart("/api/v2/fit/import")
                        .file(file)
                        .header("Authorization", "Bearer " + apiToken.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));


        testingService
                .awaitExpected(t -> t.queryForObject("SELECT COUNT(*) FROM raw_source_points WHERE user_id = ? AND device_id = ?", Long.class, user.getId(), device.id()) == 591, 100);

        List<SourceLocationPoint> savedPoints = testingService.loadPoints(user, device);

        assertEquals(591, savedPoints.size());

        assertEquals(Instant.parse("2026-06-28T13:51:10+02:00"), savedPoints.getFirst().getTimestamp());
        assertEquals(Instant.parse("2026-06-28T14:05:45+02:00"), savedPoints.getLast().getTimestamp());
    }
}
