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

        MemoryBlock created = memoryBlockJdbcService.create(testUser, block);

        assertNotNull(created.getId());
        assertEquals(testMemory.getId(), created.getMemoryId());
        assertEquals(BlockType.TEXT, created.getBlockType());
        assertEquals(0, created.getPosition());
        assertEquals(1L, created.getVersion());
    }

    @Test
    void testUpdateBlock() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(testUser, block);

        MemoryBlock updated = created.withPosition(5);
        MemoryBlock result = memoryBlockJdbcService.update(testUser, updated);

        assertEquals(5, result.getPosition());
        assertEquals(2L, result.getVersion());
    }

    @Test
    void testUpdateBlockWithWrongVersion() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(testUser, block);

        MemoryBlock withWrongVersion = created.withVersion(999L).withPosition(5);

        assertThrows(IllegalStateException.class, () -> {
            memoryBlockJdbcService.update(testUser, withWrongVersion);
        });
    }

    @Test
    void testDeleteBlock() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(testUser, block);

        memoryBlockJdbcService.delete(testUser, created.getId());

        Optional<MemoryBlock> found = memoryBlockJdbcService.findById(testUser, created.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindById() {
        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock created = memoryBlockJdbcService.create(testUser, block);

        Optional<MemoryBlock> found = memoryBlockJdbcService.findById(testUser, created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(BlockType.TEXT, found.get().getBlockType());
    }

    @Test
    void testFindByMemoryId() {
        MemoryBlock block1 = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock block2 = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_VISIT, 1);
        MemoryBlock block3 = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_TRIP, 2);

        memoryBlockJdbcService.create(testUser, block1);
        memoryBlockJdbcService.create(testUser, block2);
        memoryBlockJdbcService.create(testUser, block3);

        List<MemoryBlock> blocks = memoryBlockJdbcService.findByMemoryId(testUser, testMemory.getId());

        assertEquals(3, blocks.size());
        assertEquals(0, blocks.get(0).getPosition());
        assertEquals(1, blocks.get(1).getPosition());
        assertEquals(2, blocks.get(2).getPosition());
    }

    @Test
    void testGetMaxPosition() {
        assertEquals(-1, memoryBlockJdbcService.getMaxPosition(testUser, testMemory.getId()));

        MemoryBlock block1 = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock block2 = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_VISIT, 1);
        MemoryBlock block3 = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_TRIP, 5);

        memoryBlockJdbcService.create(testUser, block1);
        memoryBlockJdbcService.create(testUser, block2);
        memoryBlockJdbcService.create(testUser, block3);

        assertEquals(5, memoryBlockJdbcService.getMaxPosition(testUser, testMemory.getId()));
    }

    @Test
    void testCreateMultipleBlockTypes() {
        MemoryBlock textBlock = new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0);
        MemoryBlock visitBlock = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_VISIT, 1);
        MemoryBlock tripBlock = new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_TRIP, 2);
        MemoryBlock galleryBlock = new MemoryBlock(testMemory.getId(), BlockType.IMAGE_GALLERY, 3);

        MemoryBlock createdText = memoryBlockJdbcService.create(testUser, textBlock);
        MemoryBlock createdVisit = memoryBlockJdbcService.create(testUser, visitBlock);
        MemoryBlock createdTrip = memoryBlockJdbcService.create(testUser, tripBlock);
        MemoryBlock createdGallery = memoryBlockJdbcService.create(testUser, galleryBlock);

        assertEquals(BlockType.TEXT, createdText.getBlockType());
        assertEquals(BlockType.CLUSTER_VISIT, createdVisit.getBlockType());
        assertEquals(BlockType.CLUSTER_TRIP, createdTrip.getBlockType());
        assertEquals(BlockType.IMAGE_GALLERY, createdGallery.getBlockType());
    }

    @Test
    void create_WithForeignUser_ShouldNotInsertBlock() {
        User otherUser = testingService.randomUser();
        Memory memory = memoryJdbcService.create(otherUser, new Memory(
                "Foreign Memory", "Description",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP, null));

        assertThrows(IllegalStateException.class, () -> memoryBlockJdbcService.create(testUser, new MemoryBlock(memory.getId(), BlockType.TEXT, 0)));

        assertTrue(memoryBlockJdbcService.findByMemoryId(otherUser, memory.getId()).isEmpty());
    }

    @Test
    void update_WithForeignUser_ShouldNotModifyBlock() {
        User otherUser = testingService.randomUser();
        MemoryBlock created = memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0));

        assertThrows(IllegalStateException.class, () -> memoryBlockJdbcService.update(otherUser, created.withPosition(42)));

        MemoryBlock reloaded = memoryBlockJdbcService.findById(testUser, created.getId()).orElseThrow();
        assertEquals(0, reloaded.getPosition());
    }

    @Test
    void delete_WithForeignUser_ShouldNotDeleteBlock() {
        User otherUser = testingService.randomUser();
        MemoryBlock created = memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0));

        memoryBlockJdbcService.delete(otherUser, created.getId());

        assertTrue(memoryBlockJdbcService.findById(testUser, created.getId()).isPresent());
    }

    @Test
    void findByMemoryId_WithForeignUser_ShouldReturnEmpty() {
        User otherUser = testingService.randomUser();
        memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0));

        assertTrue(memoryBlockJdbcService.findByMemoryId(otherUser, testMemory.getId()).isEmpty());
    }

    @Test
    void getMaxPosition_WithForeignUser_ShouldIgnoreForeignBlocks() {
        User otherUser = testingService.randomUser();
        memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.TEXT, 7));

        assertEquals(-1, memoryBlockJdbcService.getMaxPosition(otherUser, testMemory.getId()));
    }

    @Test
    void deleteByMemoryId_WithForeignUser_ShouldNotDeleteBlocks() {
        User otherUser = testingService.randomUser();
        MemoryBlock created = memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.TEXT, 0));

        memoryBlockJdbcService.deleteByMemoryId(otherUser, testMemory.getId());

        assertTrue(memoryBlockJdbcService.findById(testUser, created.getId()).isPresent());
    }
}
