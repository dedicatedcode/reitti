package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestJdbcService;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static com.dedicatedcode.reitti.TestConstants.Points.*;
import static com.dedicatedcode.reitti.TestUtils.assertVisit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class WorkbenchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TestJdbcService testJdbcService;

    private User testUser;

    @BeforeEach
    void setUp() {
        this.testUser = testingService.randomUser();
    }

    @Test
    void shouldDeleteVisitsThroughCommitEndpoint() throws Exception {
        testingService.importAndProcess(testUser, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUser(testUser);
        assertThat(processedVisits).hasSize(5);

        List<Long> pointsToDelete = testJdbcService.findPointsForVisit(testUser, processedVisits.get(1));
        String deletedPointsJson = pointsToDelete.stream()
                .map(id -> "{\"sourceId\":" + id + "}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        commit("""
                {"editStore":{"deletedPoints":[%s]}}
                """.formatted(deletedPointsJson));

        testingService.awaitDataImport(100);

        List<ProcessedVisit> afterDeletion = processedVisitJdbcService.findByUser(testUser);
        assertThat(afterDeletion).hasSize(4);
        assertVisit(afterDeletion.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:05Z", MOLTKESTR);
        assertVisit(afterDeletion.get(1), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z", MOLTKESTR);
    }

    @Test
    void shouldStitchTimelineWhenPatchingDeviceThroughCommitEndpoint() throws Exception {
        Device device = testingService.createRandomDevice(testUser);
        testingService.importAndProcess(testUser, "/data/gpx/20250617.gpx");
        testingService.importAndProcess(testUser, device, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUser(testUser);
        List<Long> pointsToDelete = testJdbcService.findPointsForVisit(testUser, processedVisits.get(1));
        String deletedPointsJson = pointsToDelete.stream()
                .map(id -> "{\"sourceId\":" + id + "}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        commit("""
                {"editStore":{"deletedPoints":[%s]}}
                """.formatted(deletedPointsJson));

        testingService.awaitDataImport(100);
        assertThat(processedVisitJdbcService.findByUser(testUser)).hasSize(4);

        commit(("""
                {"editStore":{"patches":[{"seq":1,"tStart":%d,"tEnd":%d,"deviceId":"%s"}]}}
                """).formatted(
                Instant.parse("2025-06-17T05:44:08Z").toEpochMilli(),
                Instant.parse("2025-06-17T05:54:32Z").toEpochMilli(),
                device.id()));

        testingService.awaitDataImport(60);

        List<ProcessedVisit> stitched = processedVisitJdbcService.findByUser(testUser);
        assertThat(stitched).hasSize(5);
        assertVisit(stitched.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:05Z", MOLTKESTR);
        assertVisit(stitched.get(1), "2025-06-17T05:44:39Z", "2025-06-17T05:54:32Z", ST_THOMAS);
        assertVisit(stitched.get(2), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z", MOLTKESTR);
        assertVisit(stitched.get(3), "2025-06-17T13:12:31Z", "2025-06-17T13:18:20Z", ST_THOMAS);
        assertVisit(stitched.get(4), "2025-06-17T13:21:28Z", "2025-06-17T21:59:44Z", MOLTKESTR);
    }

    private MvcResult commit(String json) throws Exception {
        return mockMvc.perform(post("/api/v2/workbench/commit")
                        .with(user(testUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
    }
}
