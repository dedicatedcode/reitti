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
        testBlock = memoryBlockJdbcService.create(block);
    }

    @Test
    void testCreateTextBlock() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                "Test Headline",
                "Test content goes here"
        );

        MemoryBlockText created = memoryBlockTextJdbcService.create(textBlock);

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

        memoryBlockTextJdbcService.create(textBlock);

        MemoryBlockText updated = textBlock
                .withHeadline("Updated Headline")
                .withContent("Updated content");

        MemoryBlockText result = memoryBlockTextJdbcService.update(updated);

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

        memoryBlockTextJdbcService.create(textBlock);

        Optional<MemoryBlockText> found = memoryBlockTextJdbcService.findByBlockId(testBlock.getId());

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

        memoryBlockTextJdbcService.create(textBlock);
        memoryBlockTextJdbcService.delete(testBlock.getId());

        Optional<MemoryBlockText> found = memoryBlockTextJdbcService.findByBlockId(testBlock.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testCreateTextBlockWithNullHeadline() {
        MemoryBlockText textBlock = new MemoryBlockText(
                testBlock.getId(),
                null,
                "Content without headline"
        );

        MemoryBlockText created = memoryBlockTextJdbcService.create(textBlock);

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

        MemoryBlockText created = memoryBlockTextJdbcService.create(textBlock);

        assertEquals("Headline only", created.getHeadline());
        assertNull(created.getContent());
    }
}
