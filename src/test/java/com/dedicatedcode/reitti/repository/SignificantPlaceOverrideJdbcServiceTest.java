package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.SignificantPlace.PlaceType;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class SignificantPlaceOverrideJdbcServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    @Autowired
    private PointReaderWriter pointReaderWriter;

    @Test
    void testFindByUserAndPoint_ExistingOverride() {
        // Create a test user (assuming a user exists or create one; for simplicity, assume user ID 1 exists)
        User user = new User(1L, "testuser", "password", "Test User", null, null, null, 1L);

        // Create a GeoPoint
        GeoPoint point = new GeoPoint(40.7128, -74.0060); // Example: New York coordinates

        // Insert an override directly into the database for testing
        String insertSql = "INSERT INTO significant_places_overrides (user_id, geom, name, category, timezone) VALUES (?, ST_GeomFromText(?, '4326'), ?, ?, ?)";
        jdbcTemplate.update(insertSql, user.getId(), pointReaderWriter.write(point), "Home Override", "HOME", "America/New_York");

        // Now test the find method
        Optional<PlaceInformationOverride> result = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);

        assertTrue(result.isPresent());
        assertEquals("Home Override", result.get().name());
        assertEquals(PlaceType.HOME, result.get().category());
        assertEquals(ZoneId.of("America/New_York"), result.get().timezone());
    }

    @Test
    void testFindByUserAndPoint_NoOverride() {
        // Create a test user
        User user = new User(1L, "testuser", "password", "Test User", null, null, null, 1L);

        // Create a GeoPoint that doesn't have an override
        GeoPoint point = new GeoPoint(51.5074, -0.1278); // Example: London coordinates

        // Test the find method
        Optional<PlaceInformationOverride> result = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);

        assertFalse(result.isPresent());
    }

    @Test
    void testInsertOverride() {
        // Create a test user
        User user = new User(1L, "testuser", "password", "Test User", null, null, null, 1L);

        // Create a SignificantPlace
        SignificantPlace place = new SignificantPlace(1L, "Test Place", "123 Test St", "Test City", "US", 40.7128, -74.0060, PlaceType.HOME, ZoneId.of("America/New_York"), false, 1L);

        // Insert the override
        significantPlaceOverrideJdbcService.insertOverride(user, place);

        // Verify by finding it
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        Optional<PlaceInformationOverride> result = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);

        assertTrue(result.isPresent());
        assertEquals("Test Place", result.get().name());
        assertEquals(PlaceType.HOME, result.get().category());
        assertEquals(ZoneId.of("America/New_York"), result.get().timezone());
    }

    @Test
    void testClearOverride() {
        // Create a test user
        User user = new User(1L, "testuser", "password", "Test User", null, null, null, 1L);

        // Create a SignificantPlace
        SignificantPlace place = new SignificantPlace(1L, "Test Place", "123 Test St", "Test City", "US", 40.7128, -74.0060, PlaceType.HOME, ZoneId.of("America/New_York"), false, 1L);

        // Insert the override
        significantPlaceOverrideJdbcService.insertOverride(user, place);

        // Verify it exists
        GeoPoint point = new GeoPoint(place.getLatitudeCentroid(), place.getLongitudeCentroid());
        Optional<PlaceInformationOverride> resultBeforeClear = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);
        assertTrue(resultBeforeClear.isPresent());

        // Clear the override
        significantPlaceOverrideJdbcService.clear(user, place);

        // Verify it no longer exists
        Optional<PlaceInformationOverride> resultAfterClear = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);
        assertFalse(resultAfterClear.isPresent());
    }
}
