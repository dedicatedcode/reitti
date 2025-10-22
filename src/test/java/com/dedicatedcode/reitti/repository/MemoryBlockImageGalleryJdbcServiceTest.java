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
    void testCreateGallery() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Caption 1", null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image2.jpg", "Caption 2", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);

        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(gallery);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals(2, created.getImages().size());
        assertEquals("https://example.com/image1.jpg", created.getImages().get(0).getImageUrl());
        assertEquals("Caption 1", created.getImages().get(0).getCaption());
        assertEquals("https://example.com/image2.jpg", created.getImages().get(1).getImageUrl());
        assertEquals("Caption 2", created.getImages().get(1).getCaption());
    }

    @Test
    void testUpdateGallery() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Original Caption", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(gallery);

        List<MemoryBlockImageGallery.GalleryImage> updatedImages = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Updated Caption", null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image3.jpg", "New Image", null, null)
        );

        MemoryBlockImageGallery updated = created.withImages(updatedImages);
        MemoryBlockImageGallery result = memoryBlockImageGalleryJdbcService.update(updated);

        assertEquals(2, result.getImages().size());
        assertEquals("Updated Caption", result.getImages().get(0).getCaption());
        assertEquals("https://example.com/image3.jpg", result.getImages().get(1).getImageUrl());
    }

    @Test
    void testDeleteGallery() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Caption", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        memoryBlockImageGalleryJdbcService.create(gallery);

        memoryBlockImageGalleryJdbcService.delete(testBlock.getId());

        Optional<MemoryBlockImageGallery> found = memoryBlockImageGalleryJdbcService.findById(testBlock.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testDeleteByBlockId() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Caption 1", null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image2.jpg", "Caption 2", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        memoryBlockImageGalleryJdbcService.create(gallery);

        memoryBlockImageGalleryJdbcService.deleteByBlockId(testBlock.getId());

        Optional<MemoryBlockImageGallery> galleries = memoryBlockImageGalleryJdbcService.findByBlockId(testBlock.getId());
        assertTrue(galleries.isEmpty());
    }

    @Test
    void testFindById() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Test Caption", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        memoryBlockImageGalleryJdbcService.create(gallery);

        Optional<MemoryBlockImageGallery> found = memoryBlockImageGalleryJdbcService.findById(testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals(testBlock.getId(), found.get().getBlockId());
        assertEquals(1, found.get().getImages().size());
        assertEquals("https://example.com/image1.jpg", found.get().getImages().get(0).getImageUrl());
    }

    @Test
    void testFindByBlockId() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", "Caption 1", null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image2.jpg", "Caption 2", null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image3.jpg", "Caption 3", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        memoryBlockImageGalleryJdbcService.create(gallery);

        Optional<MemoryBlockImageGallery> galleries = memoryBlockImageGalleryJdbcService.findByBlockId(testBlock.getId());

        assertTrue(galleries.isPresent());
        assertEquals(3, galleries.get().getImages().size());
        assertEquals("https://example.com/image1.jpg", galleries.get().getImages().get(0).getImageUrl());
        assertEquals("https://example.com/image2.jpg", galleries.get().getImages().get(1).getImageUrl());
        assertEquals("https://example.com/image3.jpg", galleries.get().getImages().get(2).getImageUrl());
    }

    @Test
    void testCreateGalleryWithNullCaptions() {
        List<MemoryBlockImageGallery.GalleryImage> images = List.of(
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image1.jpg", null, null, null),
                new MemoryBlockImageGallery.GalleryImage("https://example.com/image2.jpg", "Caption 2", null, null)
        );

        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), images);
        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(gallery);

        assertNull(created.getImages().get(0).getCaption());
        assertEquals("Caption 2", created.getImages().get(1).getCaption());
    }

    @Test
    void testCreateEmptyGallery() {
        MemoryBlockImageGallery gallery = new MemoryBlockImageGallery(testBlock.getId(), List.of());
        MemoryBlockImageGallery created = memoryBlockImageGalleryJdbcService.create(gallery);

        assertTrue(created.getImages().isEmpty());
    }
}
