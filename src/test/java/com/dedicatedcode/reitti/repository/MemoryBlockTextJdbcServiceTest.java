package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryBlockTextJdbcServiceTest {

    @Autowired
    private MemoryBlockTextJdbcService memoryBlockTextJdbcService;

    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Memory testMemory;
    private MemoryBlock testBlock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_block_text");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");

        testUser = testingService.randomUser();

        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP,
                null
        );

        testMemory = memoryJdbcService.create(testUser, memory);

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        testBlock = memoryBlockJdbcService.create(testUser, block);
    }

    @Test
    void testCreateTextBlock() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Test Headline",
                "Test content goes here"
        );

        MemoryBlockText created = memoryBlockTextJdbcService.create(testUser, textBlock);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals("Test Headline", created.getHeadline());
        assertEquals("Test content goes here", created.getContent());
    }

    @Test
    void testUpdateTextBlock() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Original Headline",
                "Original content"
        );

        memoryBlockTextJdbcService.create(testUser, textBlock);

        MemoryBlockText updated = textBlock
                .withHeadline("Updated Headline")
                .withContent("Updated content");

        MemoryBlockText result = memoryBlockTextJdbcService.update(testUser, updated);

        assertEquals("Updated Headline", result.getHeadline());
        assertEquals("Updated content", result.getContent());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Test Headline",
                "Test content"
        );

        memoryBlockTextJdbcService.create(testUser, textBlock);

        Optional<MemoryBlockText> found = memoryBlockTextJdbcService.findByBlockId(testUser, testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals("Test Headline", found.get().getHeadline());
        assertEquals("Test content", found.get().getContent());
    }

    @Test
    void testDeleteTextBlock() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Test Headline",
                "Test content"
        );

        memoryBlockTextJdbcService.create(testUser, textBlock);
        memoryBlockTextJdbcService.delete(testUser, testBlock.getId());

        Optional<MemoryBlockText> found = memoryBlockTextJdbcService.findByBlockId(testUser, testBlock.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testCreateTextBlockWithNullHeadline() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                null,
                "Content without headline"
        );

        MemoryBlockText created = memoryBlockTextJdbcService.create(testUser, textBlock);

        assertNull(created.getHeadline());
        assertEquals("Content without headline", created.getContent());
    }

    @Test
    void testCreateTextBlockWithNullContent() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Headline only",
                null
        );

        MemoryBlockText created = memoryBlockTextJdbcService.create(testUser, textBlock);

        assertEquals("Headline only", created.getHeadline());
        assertNull(created.getContent());
    }

    @Test
    void findByBlockId_WithForeignUser_ShouldReturnEmpty() {
        User otherUser = testingService.randomUser();
        memoryBlockTextJdbcService.create(testUser, new MemoryBlockText(testBlock.getId(), "Secret", "Secret content"));

        Optional<MemoryBlockText> found = memoryBlockTextJdbcService.findByBlockId(otherUser, testBlock.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void create_WithForeignUser_ShouldNotInsert() {
        User otherUser = testingService.randomUser();

        assertThrows(IllegalStateException.class, () -> memoryBlockTextJdbcService.create(
                otherUser, new MemoryBlockText(testBlock.getId(), "Hijacked", "Hijacked content")));

        assertTrue(memoryBlockTextJdbcService.findByBlockId(testUser, testBlock.getId()).isEmpty());
    }

    @Test
    void update_WithForeignUser_ShouldNotModify() {
        User otherUser = testingService.randomUser();
        memoryBlockTextJdbcService.create(testUser, new MemoryBlockText(testBlock.getId(), "Original", "Original content"));

        memoryBlockTextJdbcService.update(otherUser, new MemoryBlockText(testBlock.getId(), "Hijacked", "Hijacked content"));

        MemoryBlockText found = memoryBlockTextJdbcService.findByBlockId(testUser, testBlock.getId()).orElseThrow();
        assertEquals("Original", found.getHeadline());
        assertEquals("Original content", found.getContent());
    }

    @Test
    void delete_WithForeignUser_ShouldNotDelete() {
        User otherUser = testingService.randomUser();
        memoryBlockTextJdbcService.create(testUser, new MemoryBlockText(testBlock.getId(), "Keep", "Keep content"));

        memoryBlockTextJdbcService.delete(otherUser, testBlock.getId());

        assertTrue(memoryBlockTextJdbcService.findByBlockId(testUser, testBlock.getId()).isPresent());
    }
}
