package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

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

    private Long testUserId = 999999L;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        jdbcTemplate.update("DELETE FROM user_avatars WHERE user_id = ?", testUserId);

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
        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUserId);
        assertTrue(result.isEmpty(), "Should return empty when no avatar exists");
    }

    @Test
    void testGetAvatarByUserId_WhenAvatarExists() {
        // Insert test avatar data
        String testContentType = "image/jpeg";
        byte[] testImageData = new byte[]{1, 2, 3, 4}; // Simple test data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, testContentType, testImageData);

        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUserId);

        assertTrue(result.isPresent(), "Should return avatar data when it exists");
        assertEquals(testContentType, result.get().mimeType());
        assertArrayEquals(testImageData, result.get().imageData());
        assertTrue(result.get().updatedAt() > 0, "Updated timestamp should be set");
    }

    @Test
    void testGetInfo_WhenNoAvatarExists() {
        Optional<AvatarService.AvatarInfo> result = avatarService.getInfo(testUserId);
        assertTrue(result.isEmpty(), "Should return empty when no avatar exists");
    }

    @Test
    void testGetInfo_WhenAvatarExists() {
        // Insert test avatar data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/png", new byte[]{5, 6, 7, 8});

        Optional<AvatarService.AvatarInfo> result = avatarService.getInfo(testUserId);

        assertTrue(result.isPresent(), "Should return avatar info when it exists");
        assertTrue(result.get().updatedAt() > 0, "Updated timestamp should be set");
    }

    @Test
    void testUpdateAvatar() {
        String contentType = "image/png";
        byte[] imageData = new byte[]{10, 20, 30, 40};

        // Update avatar
        avatarService.updateAvatar(testUserId, contentType, imageData);

        // Verify it was stored
        Optional<AvatarService.AvatarData> result = avatarService.getAvatarByUserId(testUserId);
        assertTrue(result.isPresent());
        assertEquals(contentType, result.get().mimeType());
        assertArrayEquals(imageData, result.get().imageData());
    }

    @Test
    void testDeleteAvatar() {
        // First insert an avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", new byte[]{1, 2, 3});

        // Verify it exists
        Optional<AvatarService.AvatarData> beforeDelete = avatarService.getAvatarByUserId(testUserId);
        assertTrue(beforeDelete.isPresent());

        // Delete the avatar
        avatarService.deleteAvatar(testUserId);

        // Verify it's gone
        Optional<AvatarService.AvatarData> afterDelete = avatarService.getAvatarByUserId(testUserId);
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
    void testGetAvatarThumbnail() {
        // Insert a larger test image
        byte[] originalImage = new byte[1000];
        for (int i = 0; i < originalImage.length; i++) {
            originalImage[i] = (byte) (i % 256);
        }

        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", originalImage);

        // Get thumbnail
        Optional<byte[]> thumbnail = avatarService.getAvatarThumbnail(testUserId, 100, 100);

        assertTrue(thumbnail.isPresent());
        assertTrue(thumbnail.get().length < originalImage.length, "Thumbnail should be smaller than original");
    }

    @Test
    void testAvatarDataCaching() {
        // Insert test data
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", new byte[]{1, 2, 3});

        // First call - should hit database
        Optional<AvatarService.AvatarData> firstCall = avatarService.getAvatarByUserId(testUserId);
        assertTrue(firstCall.isPresent());

        // Second call - should hit cache
        Optional<AvatarService.AvatarData> secondCall = avatarService.getAvatarByUserId(testUserId);
        assertTrue(secondCall.isPresent());

        // Verify both calls return the same data
        assertEquals(firstCall.get().mimeType(), secondCall.get().mimeType());
        assertArrayEquals(firstCall.get().imageData(), secondCall.get().imageData());
    }

    @Test
    void testAvatarThumbnailCaching() {
        // Insert test data
        byte[] imageData = new byte[500];
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/png", imageData);

        // First call - should process thumbnail
        Optional<byte[]> firstThumbnail = avatarService.getAvatarThumbnail(testUserId, 50, 50);
        assertTrue(firstThumbnail.isPresent());

        // Second call - should return cached thumbnail
        Optional<byte[]> secondThumbnail = avatarService.getAvatarThumbnail(testUserId, 50, 50);
        assertTrue(secondThumbnail.isPresent());

        // Verify both calls return the same thumbnail data
        assertArrayEquals(firstThumbnail.get(), secondThumbnail.get());
    }

    @Test
    void testCacheEvictionOnUpdate() {
        // Insert initial avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", new byte[]{1, 2, 3});

        // Get avatar data (will be cached)
        Optional<AvatarService.AvatarData> beforeUpdate = avatarService.getAvatarByUserId(testUserId);
        assertTrue(beforeUpdate.isPresent());

        // Update avatar
        avatarService.updateAvatar(testUserId, "image/png", new byte[]{4, 5, 6});

        // Get avatar data again - should get new data, not cached old data
        Optional<AvatarService.AvatarData> afterUpdate = avatarService.getAvatarByUserId(testUserId);
        assertTrue(afterUpdate.isPresent());
        assertEquals("image/png", afterUpdate.get().mimeType());
        assertArrayEquals(new byte[]{4, 5, 6}, afterUpdate.get().imageData());
    }

    @Test
    void testCacheEvictionOnDelete() {
        // Insert avatar
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", new byte[]{1, 2, 3});

        // Get avatar data (will be cached)
        Optional<AvatarService.AvatarData> beforeDelete = avatarService.getAvatarByUserId(testUserId);
        assertTrue(beforeDelete.isPresent());

        // Delete avatar
        avatarService.deleteAvatar(testUserId);

        // Get avatar data again - should get empty, not cached data
        Optional<AvatarService.AvatarData> afterDelete = avatarService.getAvatarByUserId(testUserId);
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    void testDifferentThumbnailSizesAreCachedSeparately() {
        // Insert test image
        byte[] imageData = new byte[1000];
        jdbcTemplate.update(
                "INSERT INTO user_avatars (user_id, mime_type, binary_data) VALUES (?, ?, ?)",
                testUserId, "image/jpeg", imageData);

        // Get different sized thumbnails
        Optional<byte[]> smallThumbnail = avatarService.getAvatarThumbnail(testUserId, 50, 50);
        Optional<byte[]> largeThumbnail = avatarService.getAvatarThumbnail(testUserId, 200, 200);

        assertTrue(smallThumbnail.isPresent());
        assertTrue(largeThumbnail.isPresent());

        // They should be different sizes
        assertNotEquals(smallThumbnail.get().length, largeThumbnail.get().length);
    }
}
