package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class AvatarServiceIntegrationTest {

    @Autowired
    private AvatarService avatarService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ResourceLoader resourceLoader;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a real user using TestingService
        testUser = testingService.randomUser();

        // Clear caches before each test
        clearCaches();
    }

    private void clearCaches() {
        Cache avatarDataCache = cacheManager.getCache("avatarData");
        Cache avatarThumbnailsCache = cacheManager.getCache("avatarThumbnails");

        if (avatarDataCache != null) {
            avatarDataCache.clear();
        }
        if (avatarThumbnailsCache != null) {
            avatarThumbnailsCache.clear();
        }
    }

    @Test
    void testGetAvatarByUserId_WhenNoAvatarExists() {
        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(result.isEmpty(), "Should return empty when no avatar exists");
    }

    @Test
    void testGetAvatarByUserId_WhenAvatarExists() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] testImageData = loadImageData(imageResource);
        String testContentType = "image/jpeg";

        // Insert test avatar data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), testContentType, testImageData);

        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUser.getId());

        assertTrue(result.isPresent(), "Should return avatar data when it exists");
        assertEquals(testContentType, result.get().mimeType());
        assertArrayEquals(testImageData, result.get().imageData());
        assertTrue(result.get().updatedAt() > 0, "Updated timestamp should be set");
    }

    @Test
    void testGetInfo_WhenNoAvatarExists() {
        Optional<AvatarService.AvatarInfo> result = avatarService.getInfo(testUser.getId());
        assertTrue(result.isEmpty(), "Should return empty when no avatar exists");
    }

    @Test
    void testGetInfo_WhenAvatarExists() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] testImageData = loadImageData(imageResource);

        // Insert test avatar data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", testImageData);

        Optional<AvatarService.AvatarInfo> result = avatarService.getInfo(testUser.getId());

        assertTrue(result.isPresent(), "Should return avatar info when it exists");
        assertTrue(result.get().updatedAt() > 0, "Updated timestamp should be set");
    }

    @Test
    void testUpdateAvatar() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);
        String contentType = "image/jpeg";

        // Update avatar
        avatarService.updateAvatar(testUser.getId(), contentType, imageData);

        // Verify it was stored
        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(result.isPresent());
        assertEquals(contentType, result.get().mimeType());
        assertArrayEquals(imageData, result.get().imageData());
    }

    @Test
    void testDeleteAvatar() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);

        // First insert an avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData);

        // Verify it exists
        Optional<AvatarService.AvatarData> beforeDelete = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(beforeDelete.isPresent());

        // Delete the avatar
        avatarService.deleteAvatar(testUser.getId());

        // Verify it's gone
        Optional<AvatarService.AvatarData> afterDelete = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    void testGenerateInitials() {
        assertEquals("", avatarService.generateInitials(null));
        assertEquals("", avatarService.generateInitials(""));
        assertEquals("", avatarService.generateInitials("   "));

        assertEquals("JD", avatarService.generateInitials("John Doe"));
        assertEquals("JS", avatarService.generateInitials("John Smith"));
        assertEquals("J", avatarService.generateInitials("John"));
        assertEquals("JA", avatarService.generateInitials("John"));
        assertEquals("A", avatarService.generateInitials("A"));
        assertEquals("AB", avatarService.generateInitials("AB"));
        assertEquals("ABCD", avatarService.generateInitials("ABCD EFGH")); // Only first 2 words
        assertEquals("JD", avatarService.generateInitials("  John   Doe  ")); // Trimmed
    }

    @Test
    void testGetAvatarThumbnail() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] originalImage = loadImageData(imageResource);

        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", originalImage);

        // Get thumbnail
        Optional<byte[]> thumbnail = avatarService.getAvatarThumbnail(testUser.getId(), 100, 100);

        assertTrue(thumbnail.isPresent());
        assertTrue(thumbnail.get().length < originalImage.length, "Thumbnail should be smaller than original");
    }

    @Test
    void testAvatarDataCaching() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);

        // Insert test data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData);

        // First call - should hit database
        Optional<AvatarService.AvatarData> firstCall = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(firstCall.isPresent());

        // Second call - should hit cache
        Optional<AvatarService.AvatarData> secondCall = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(secondCall.isPresent());

        // Verify both calls return the same data
        assertEquals(firstCall.get().mimeType(), secondCall.get().mimeType());
        assertArrayEquals(firstCall.get().imageData(), secondCall.get().imageData());
    }

    @Test
    void testAvatarThumbnailCaching() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);

        // Insert test data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData);

        // First call - should process thumbnail
        Optional<byte[]> firstThumbnail = avatarService.getAvatarThumbnail(testUser.getId(), 50, 50);
        assertTrue(firstThumbnail.isPresent());

        // Second call - should return cached thumbnail
        Optional<byte[]> secondThumbnail = avatarService.getAvatarThumbnail(testUser.getId(), 50, 50);
        assertTrue(secondThumbnail.isPresent());

        // Verify both calls return the same thumbnail data
        assertArrayEquals(firstThumbnail.get(), secondThumbnail.get());
    }

    @Test
    void testCacheEvictionOnUpdate() throws IOException {
        // Load test images from resources
        Resource imageResource1 = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        Resource imageResource2 = resourceLoader.getResource("classpath:data/images/test-image2.png");
        byte[] imageData1 = loadImageData(imageResource1);
        byte[] imageData2 = loadImageData(imageResource2);

        // Insert initial avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData1);

        // Get avatar data (will be cached)
        Optional<AvatarService.AvatarData> beforeUpdate = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(beforeUpdate.isPresent());

        // Update avatar
        avatarService.updateAvatar(testUser.getId(), "image/png", imageData2);

        // Get avatar data again - should get new data, not cached old data
        Optional<AvatarService.AvatarData> afterUpdate = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(afterUpdate.isPresent());
        assertEquals("image/png", afterUpdate.get().mimeType());
        assertArrayEquals(imageData2, afterUpdate.get().imageData());
    }

    @Test
    void testCacheEvictionOnDelete() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);

        // Insert avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData);

        // Get avatar data (will be cached)
        Optional<AvatarService.AvatarData> beforeDelete = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(beforeDelete.isPresent());

        // Delete avatar
        avatarService.deleteAvatar(testUser.getId());

        // Get avatar data again - should get empty, not cached data
        Optional<AvatarService.AvatarData> afterDelete = avatarService.getAvatarByUserId(testUser.getId());
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    void testDifferentThumbnailSizesAreCachedSeparately() throws IOException {
        // Load test image from resources
        Resource imageResource = resourceLoader.getResource("classpath:data/images/test-image.jpg");
        byte[] imageData = loadImageData(imageResource);

        // Insert test image
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUser.getId(), "image/jpeg", imageData);

        // Get different sized thumbnails
        Optional<byte[]> smallThumbnail = avatarService.getAvatarThumbnail(testUser.getId(), 50, 50);
        Optional<byte[]> largeThumbnail = avatarService.getAvatarThumbnail(testUser.getId(), 200, 200);

        assertTrue(smallThumbnail.isPresent());
        assertTrue(largeThumbnail.isPresent());

        // They should be different sizes
        assertNotEquals(smallThumbnail.get().length, largeThumbnail.get().length);
    }

    private byte[] loadImageData(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }
}
