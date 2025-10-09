package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryBlockJdbcServiceTest {

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

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void testCreateBlock() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);

        MemoryBlock created = memoryBlockJdbcService.create(block);

        assertNotNull(created.getId());
        assertEquals(testMemory.getId(), created.getMemoryId());
        assertEquals(BlockType.TEXT, created.getBlockType());
        assertEquals(0, created.getPosition());
        assertEquals(1L, created.getVersion());
    }

    @Test
    void testUpdateBlock() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(block);

        MemoryBlock updated = created.withPosition(5);
        MemoryBlock result = memoryBlockJdbcService.update(updated);

        assertEquals(5, result.getPosition());
        assertEquals(2L, result.getVersion());
    }

    @Test
    void testUpdateBlockWithWrongVersion() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(block);

        MemoryBlock withWrongVersion = created.withVersion(999L).withPosition(5);

        assertThrows(IllegalStateException.class, () -> {
            memoryBlockJdbcService.update(withWrongVersion);
        });
    }

    @Test
    void testDeleteBlock() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(block);

        memoryBlockJdbcService.delete(created.getId());

        Optional<MemoryBlock> found = memoryBlockJdbcService.findById(created.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindById() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(block);

        Optional<MemoryBlock> found = memoryBlockJdbcService.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(BlockType.TEXT, found.get().getBlockType());
    }

    @Test
    void testFindByMemoryId() {
        MemoryBlock block1 = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock block2 = new MemoryBlock(testMemory.getId(), BlockType.VISIT, 1);
        MemoryBlock block3 = new MemoryBlock(testMemory.getId(), BlockType.TRIP, 2);

        memoryBlockJdbcService.create(block1);
        memoryBlockJdbcService.create(block2);
        memoryBlockJdbcService.create(block3);

        List<MemoryBlock> blocks = memoryBlockJdbcService.findByMemoryId(testMemory.getId());

        assertEquals(3, blocks.size());
        assertEquals(0, blocks.get(0).getPosition());
        assertEquals(1, blocks.get(1).getPosition());
        assertEquals(2, blocks.get(2).getPosition());
    }

    @Test
    void testGetMaxPosition() {
        assertEquals(-1, memoryBlockJdbcService.getMaxPosition(testMemory.getId()));

        MemoryBlock block1 = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock block2 = new MemoryBlock(testMemory.getId(), BlockType.VISIT, 1);
        MemoryBlock block3 = new MemoryBlock(testMemory.getId(), BlockType.TRIP, 5);

        memoryBlockJdbcService.create(block1);
        memoryBlockJdbcService.create(block2);
        memoryBlockJdbcService.create(block3);

        assertEquals(5, memoryBlockJdbcService.getMaxPosition(testMemory.getId()));
    }

    @Test
    void testCreateMultipleBlockTypes() {
        MemoryBlock textBlock = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock visitBlock = new MemoryBlock(testMemory.getId(), BlockType.VISIT, 1);
        MemoryBlock tripBlock = new MemoryBlock(testMemory.getId(), BlockType.TRIP, 2);
        MemoryBlock galleryBlock = new MemoryBlock(testMemory.getId(), BlockType.IMAGE_GALLERY, 3);

        MemoryBlock createdText = memoryBlockJdbcService.create(textBlock);
        MemoryBlock createdVisit = memoryBlockJdbcService.create(visitBlock);
        MemoryBlock createdTrip = memoryBlockJdbcService.create(tripBlock);
        MemoryBlock createdGallery = memoryBlockJdbcService.create(galleryBlock);

        assertEquals(BlockType.TEXT, createdText.getBlockType());
        assertEquals(BlockType.VISIT, createdVisit.getBlockType());
        assertEquals(BlockType.TRIP, createdTrip.getBlockType());
        assertEquals(BlockType.IMAGE_GALLERY, createdGallery.getBlockType());
    }
}
