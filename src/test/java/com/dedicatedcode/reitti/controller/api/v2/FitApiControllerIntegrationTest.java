package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

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

        // Placeholder for the actual fit file resource.
        // Replace "sample.fit" with the actual file name once provided.
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.fit",
                "application/octet-stream",
                new ClassPathResource("sample.fit").getInputStream()
        );

        mockMvc.perform(multipart("/api/v2/fit/import")
                        .file(file)
                        .header("Authorization", "Bearer " + apiToken.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
