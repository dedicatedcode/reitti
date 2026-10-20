package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryBlockJdbcService;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MemoryBlockControllerTest {

    private static final Instant MEMORY_START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant MEMORY_END = Instant.parse("2024-01-07T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private MemoryJdbcService memoryJdbcService;
    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;
    @Autowired
    private MemoryService memoryService;

    private User owner;
    private Memory grantedMemory;
    private Memory otherMemory;
    private MemoryBlock grantedBlock;
    private MemoryBlock otherBlock;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        grantedMemory = createMemory("Granted memory");
        otherMemory = createMemory("Other memory");
        grantedBlock = createTextBlock(grantedMemory);
        otherBlock = createTextBlock(otherMemory);
    }

    @Test
    void memoryEditLinkCanViewAndEditItsOwnMemory() throws Exception {
        RequestPostProcessor link = memoryLink(grantedMemory, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);

        mockMvc.perform(get("/memories/{memoryId}/blocks/{blockId}/view", grantedMemory.getId(), grantedBlock.getId())
                                .with(link))
                .andExpect(status().isOk());

        mockMvc.perform(post("/memories/{memoryId}/blocks/text", grantedMemory.getId())
                                .param("headline", "Shared headline")
                                .param("content", "Shared content")
                                .with(csrf())
                                .with(link))
                .andExpect(status().isOk());
    }

    @Test
    void memoryEditLinkCannotReadBlocksOfAnotherMemory() throws Exception {
        RequestPostProcessor link = memoryLink(grantedMemory, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);

        mockMvc.perform(get("/memories/{memoryId}/blocks/{blockId}/view", otherMemory.getId(), otherBlock.getId())
                                .with(link))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/memories/{memoryId}/blocks/immich-photos", otherMemory.getId())
                                .with(link))
                .andExpect(status().isNotFound());
    }

    @Test
    void memoryEditLinkCannotModifyBlocksOfAnotherMemory() throws Exception {
        RequestPostProcessor link = memoryLink(grantedMemory, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);

        mockMvc.perform(post("/memories/{memoryId}/blocks/text", otherMemory.getId())
                                .param("headline", "Injected headline")
                                .param("content", "Injected content")
                                .with(csrf())
                                .with(link))
                .andExpect(status().isForbidden());
        assertEquals(1, memoryBlockJdbcService.findByMemoryId(owner, otherMemory.getId()).size());

        mockMvc.perform(post("/memories/{memoryId}/blocks/reorder", otherMemory.getId())
                                .param("blockIds", otherBlock.getId().toString())
                                .with(csrf())
                                .with(link))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/memories/{memoryId}/blocks/{blockId}/edit", otherMemory.getId(), otherBlock.getId())
                                .with(link))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/memories/{memoryId}/blocks/{blockId}", otherMemory.getId(), otherBlock.getId())
                                .with(csrf())
                                .with(link))
                .andExpect(status().isForbidden());
        assertTrue(memoryBlockJdbcService.findById(owner, otherBlock.getId()).isPresent());
    }

    private Memory createMemory(String title) {
        return memoryJdbcService.create(owner, new Memory(title, "", MEMORY_START, MEMORY_END, HeaderType.MAP, null));
    }

    private MemoryBlock createTextBlock(Memory memory) {
        MemoryBlock block = memoryBlockJdbcService.create(owner, new MemoryBlock(memory.getId(), BlockType.TEXT, 0));
        memoryService.addTextBlock(owner, block.getId(), "Headline", "Content");
        return block;
    }

    private RequestPostProcessor memoryLink(Memory memory, MagicLinkAccessLevel level) {
        TokenUser tokenUser = new TokenUser(owner, "test-token", MagicLinkResourceType.MEMORY, memory.getId(),
                                            List.of(level.asAuthority().getAuthority()));
        return authentication(new UsernamePasswordAuthenticationToken(tokenUser, null, tokenUser.getAuthorities()));
    }
}
