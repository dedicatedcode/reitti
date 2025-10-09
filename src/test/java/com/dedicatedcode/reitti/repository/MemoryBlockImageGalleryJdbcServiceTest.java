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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryBlockImageGalleryJdbcServiceTest {

    @Autowired
    private MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService;

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
        jdbcTemplate.update("DELETE FROM memory_block_image_gallery");
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

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.IMAGE_GALLERY, 0);
        testBlock = memoryBlockJdbcService.create(block);
    }

    @Test
    void testCreateImage() {
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Test Caption",
                0
        );

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(image);

        assertNotNull(created.getId());
        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals("https://example.com/image1.jpg", created.getImageUrl());
        assertEquals("Test Caption", created.getCaption());
        assertEquals(0, created.getPosition());
    }

    @Test
    void testUpdateImage() {
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Original Caption",
                0
        );

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(image);

        MemoryBlockImageGallery updated = created
                .withCaption("Updated Caption")
                .withPosition(5);

        MemoryBlockImageGallery result = memoryBlockImageGalleryJdbcService.update(updated);

        assertEquals("Updated Caption", result.getCaption());
        assertEquals(5, result.getPosition());
    }

    @Test
    void testDeleteImage() {
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Test Caption",
                0
        );

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(image);
        memoryBlockImageGalleryJdbcService.delete(created.getId());

        Optional<MemoryBlockImageGallery> found = memoryBlockImageGalleryJdbcService.findById(created.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testDeleteByBlockId() {
        MemoryBlockImageGallery image1 = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Caption 1",
                0
        );

        MemoryBlockImageGallery image2 = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image2.jpg",
                "Caption 2",
                1
        );

        memoryBlockImageGalleryJdbcService.create(image1);
        memoryBlockImageGalleryJdbcService.create(image2);

        memoryBlockImageGalleryJdbcService.deleteByBlockId(testBlock.getId());

        List<MemoryBlockImageGallery> images = memoryBlockImageGalleryJdbcService.findByBlockId(testBlock.getId());
        assertTrue(images.isEmpty());
    }

    @Test
    void testFindById() {
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Test Caption",
                0
        );

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(image);
        Optional<MemoryBlockImageGallery> found = memoryBlockImageGalleryJdbcService.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals("https://example.com/image1.jpg", found.get().getImageUrl());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockImageGallery image1 = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                "Caption 1",
                0
        );

        MemoryBlockImageGallery image2 = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image2.jpg",
                "Caption 2",
                1
        );

        MemoryBlockImageGallery image3 = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image3.jpg",
                "Caption 3",
                2
        );

        memoryBlockImageGalleryJdbcService.create(image1);
        memoryBlockImageGalleryJdbcService.create(image2);
        memoryBlockImageGalleryJdbcService.create(image3);

        List<MemoryBlockImageGallery> images = memoryBlockImageGalleryJdbcService.findByBlockId(testBlock.getId());

        assertEquals(3, images.size());
        assertEquals(0, images.get(0).getPosition());
        assertEquals(1, images.get(1).getPosition());
        assertEquals(2, images.get(2).getPosition());
    }

    @Test
    void testCreateImageWithNullCaption() {
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(
                testBlock.getId(),
                "https://example.com/image1.jpg",
                null,
                0
        );

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(image);

        assertNull(created.getCaption());
        assertEquals("https://example.com/image1.jpg", created.getImageUrl());
    }
}
