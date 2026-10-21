package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ReittiPhotoApiControllerTest {

    private static final Instant MEMORY_START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant MEMORY_END = Instant.parse("2024-01-07T00:00:00Z");
    private static final String FILENAME = "photo.png";
    private static final byte[] PHOTO = loadPhoto();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private MemoryJdbcService memoryJdbcService;
    @Autowired
    private StorageService storageService;

    private User owner;
    private Memory grantedMemory;
    private Memory otherMemory;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        grantedMemory = createMemory("Granted memory");
        otherMemory = createMemory("Other memory");
        storePhoto(grantedMemory);
        storePhoto(otherMemory);
    }

    @AfterEach
    void tearDown() {
        storageService.remove("memories/" + grantedMemory.getId());
        storageService.remove("memories/" + otherMemory.getId());
    }

    @Test
    void memoryLinkCanDownloadPhotosOfItsOwnMemory() throws Exception {
        mockMvc.perform(get("/api/v1/photos/reitti/memories/{memoryId}/{filename}", grantedMemory.getId(), FILENAME)
                                .with(memoryLink(grantedMemory)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PHOTO));
    }

    @Test
    void memoryLinkCannotDownloadPhotosOfAnotherMemory() throws Exception {
        mockMvc.perform(get("/api/v1/photos/reitti/memories/{memoryId}/{filename}", otherMemory.getId(), FILENAME)
                                .with(memoryLink(grantedMemory)))
                .andExpect(status().isNotFound());
    }

    private static byte[] loadPhoto() {
        try {
            return new ClassPathResource("static/img/logo.png").getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Memory createMemory(String title) {
        return memoryJdbcService.create(owner, new Memory(title, "", MEMORY_START, MEMORY_END, HeaderType.MAP, null));
    }

    private void storePhoto(Memory memory) {
        storageService.store("memories/" + memory.getId() + "/" + FILENAME,
                             new ByteArrayInputStream(PHOTO), PHOTO.length, "image/png");
    }

    private RequestPostProcessor memoryLink(Memory memory) {
        TokenUser tokenUser = new TokenUser(owner, "test-token", MagicLinkResourceType.MEMORY, memory.getId(),
                                            List.of(MagicLinkAccessLevel.MEMORY_VIEW_ONLY.asAuthority().getAuthority()));
        return authentication(new UsernamePasswordAuthenticationToken(tokenUser, null, tokenUser.getAuthorities()));
    }
}
