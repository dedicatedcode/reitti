package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MemoryImageUploadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private StorageService storageService;

    private User user;
    private Memory memory;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        memory = memoryJdbcService.create(user, new Memory(
                "Security Test",
                "Upload validation",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                HeaderType.MAP,
                null));
        storageService.remove("memories/" + memory.getId());
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            storageService.remove("memories/" + memory.getId());
        }
    }

    @Test
    void rejectsHtmlDisguisedAsImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "evil.png",
                MediaType.IMAGE_PNG_VALUE,
                "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/memories/{memoryId}/blocks/upload-image", memory.getId())
                        .file(file)
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(storageService.getChildren("memories/" + memory.getId())).isEmpty();
    }

    @Test
    void ignoresClientFilenameAndStoresCanonicalExtension() throws Exception {
        byte[] png = new ClassPathResource("static/img/logo.png").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("files", "evil.html", MediaType.TEXT_HTML_VALUE, png);

        MvcResult result = mockMvc.perform(multipart("/memories/{memoryId}/blocks/upload-image", memory.getId())
                        .file(file)
                        .with(user(user))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        List<String> urls = (List<String>) result.getModelAndView().getModel().get("urls");
        assertThat(urls).hasSize(1);
        assertThat(urls.getFirst()).endsWith(".png");

        mockMvc.perform(get(urls.getFirst()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));
    }

    @Test
    void doesNotServeLegacyHtmlFiles() throws Exception {
        storeLegacyFile("legacy.html", "text/html", "<html><script>alert(1)</script></html>");

        mockMvc.perform(get("/api/v1/photos/reitti/memories/{memoryId}/{filename}", memory.getId(), "legacy.html")
                        .with(user(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotServeHtmlContentWithImageExtension() throws Exception {
        storeLegacyFile("legacy.png", "image/png", "<html><script>alert(1)</script></html>");

        mockMvc.perform(get("/api/v1/photos/reitti/memories/{memoryId}/{filename}", memory.getId(), "legacy.png")
                        .with(user(user)))
                .andExpect(status().isNotFound());
    }

    private void storeLegacyFile(String filename, String contentType, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storageService.store("memories/" + memory.getId() + "/" + filename,
                new ByteArrayInputStream(bytes), bytes.length, contentType);
    }
}
