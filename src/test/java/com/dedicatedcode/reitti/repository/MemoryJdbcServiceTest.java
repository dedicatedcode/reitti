package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryJdbcServiceTest {

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory");
        testUser = testingService.randomUser();
    }

    @Test
    void testCreateMemory() {
        Memory memory = new Memory(
                "Test Memory",
                "Test Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);

        assertNotNull(created.getId());
        assertEquals("Test Memory", created.getTitle());
        assertEquals("Test Description", created.getDescription());
        assertEquals(LocalDate.of(2024, 1, 1), created.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 7), created.getEndDate());
        assertEquals(HeaderType.MAP, created.getHeaderType());
        assertNull(created.getHeaderImageUrl());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals(1L, created.getVersion());
    }

    @Test
    void testCreateMemoryWithImage() {
        Memory memory = new Memory(
                "Image Memory",
                "Description",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 5),
                HeaderType.IMAGE,
                "https://example.com/image.jpg"
        );

        Memory created = memoryJdbcService.create(testUser, memory);

        assertNotNull(created.getId());
        assertEquals(HeaderType.IMAGE, created.getHeaderType());
        assertEquals("https://example.com/image.jpg", created.getHeaderImageUrl());
    }

    @Test
    void testUpdateMemory() {
        Memory memory = new Memory(
                "Original Title",
                "Original Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);

        Memory updated = created
                .withTitle("Updated Title")
                .withDescription("Updated Description")
                .withHeaderType(HeaderType.IMAGE)
                .withHeaderImageUrl("https://example.com/new-image.jpg");

        Memory result = memoryJdbcService.update(testUser, updated);

        assertEquals("Updated Title", result.getTitle());
        assertEquals("Updated Description", result.getDescription());
        assertEquals(HeaderType.IMAGE, result.getHeaderType());
        assertEquals("https://example.com/new-image.jpg", result.getHeaderImageUrl());
        assertEquals(2L, result.getVersion());
    }

    @Test
    void testUpdateMemoryWithWrongVersion() {
        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);
        Memory withWrongVersion = created.withVersion(999L).withTitle("Updated");

        assertThrows(IllegalStateException.class, () -> {
            memoryJdbcService.update(testUser, withWrongVersion);
        });
    }

    @Test
    void testDeleteMemory() {
        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);
        memoryJdbcService.delete(testUser, created.getId());

        Optional<Memory> found = memoryJdbcService.findById(testUser, created.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindById() {
        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);
        Optional<Memory> found = memoryJdbcService.findById(testUser, created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals("Test Memory", found.get().getTitle());
    }

    @Test
    void testFindByIdDifferentUser() {
        User otherUser = testingService.randomUser();

        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory created = memoryJdbcService.create(testUser, memory);
        Optional<Memory> found = memoryJdbcService.findById(otherUser, created.getId());

        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllByUser() {
        Memory memory1 = new Memory(
                "Memory 1",
                "Description 1",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory memory2 = new Memory(
                "Memory 2",
                "Description 2",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 7),
                HeaderType.IMAGE,
                "https://example.com/image.jpg"
        );

        memoryJdbcService.create(testUser, memory1);
        memoryJdbcService.create(testUser, memory2);

        List<Memory> memories = memoryJdbcService.findAllByUser(testUser);

        assertEquals(2, memories.size());
    }

    @Test
    void testFindByDateRange() {
        Memory memory1 = new Memory(
                "January Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        Memory memory2 = new Memory(
                "February Memory",
                "Description",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 7),
                HeaderType.MAP,
                null
        );

        Memory memory3 = new Memory(
                "March Memory",
                "Description",
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 7),
                HeaderType.MAP,
                null
        );

        memoryJdbcService.create(testUser, memory1);
        memoryJdbcService.create(testUser, memory2);
        memoryJdbcService.create(testUser, memory3);

        List<Memory> memories = memoryJdbcService.findByDateRange(
                testUser,
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 2, 15)
        );

        assertEquals(2, memories.size());
        assertTrue(memories.stream().anyMatch(m -> m.getTitle().equals("January Memory")));
        assertTrue(memories.stream().anyMatch(m -> m.getTitle().equals("February Memory")));
    }

    @Test
    void testFindByDateRangeOverlapping() {
        Memory memory = new Memory(
                "Long Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31),
                HeaderType.MAP,
                null
        );

        memoryJdbcService.create(testUser, memory);

        List<Memory> memories = memoryJdbcService.findByDateRange(
                testUser,
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 28)
        );

        assertEquals(1, memories.size());
        assertEquals("Long Memory", memories.get(0).getTitle());
    }
}
