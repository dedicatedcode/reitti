package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.PlaceInformationOverride;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.SignificantPlace.PlaceType;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class SignificantPlaceOverrideJdbcServiceTest {

    @Autowired
    private SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;

    @Autowired
    private TestingService testingService;

    @Test
    void testFindByUserAndPoint_ExistingOverride() {
        // Create a test user (assuming a user exists or create one; for simplicity, assume user ID 1 exists)
        User user = testingService.randomUser();

        // Create a GeoPoint
        GeoPoint point = new GeoPoint(40.7128, -74.0060); // Example: New York coordinates

        // Create a SignificantPlace with the override details
        SignificantPlace place = new SignificantPlace(1L, "Home Override", "123 Main St", "New York", "US", 40.7128, -74.0060, PlaceType.HOME, ZoneId.of("America/New_York"), false, 1L);

        // Insert the override using the service
        significantPlaceOverrideJdbcService.insertOverride(user, place);

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
        User user = testingService.randomUser();

        // Create a GeoPoint that doesn't have an override
        GeoPoint point = new GeoPoint(51.5074, -0.1278); // Example: London coordinates

        // Test the find method
        Optional<PlaceInformationOverride> result = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point);

        assertFalse(result.isPresent());
    }

    @Test
    void testInsertOverride() {
        // Create a test user
        User user = testingService.randomUser();

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
        User user = testingService.randomUser();

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

    @Test
    void testFindByUserAndPoint_Within5mRadius() {
        // Create a test user
        User user = testingService.randomUser();

        // Create a SignificantPlace at a specific location
        SignificantPlace place = new SignificantPlace(1L, "Nearby Override", "456 Nearby St", "Nearby City", "US", 40.7128, -74.0060, PlaceType.WORK, ZoneId.of("America/New_York"), false, 1L);
        significantPlaceOverrideJdbcService.insertOverride(user, place);

        // Create a GeoPoint very close (within 5m) to the place
        GeoPoint closePoint = new GeoPoint(40.71281, -74.006056); // Approximately 5m away

        // Test that the override is found from the close point
        Optional<PlaceInformationOverride> result = significantPlaceOverrideJdbcService.findByUserAndPoint(user, closePoint);
        assertTrue(result.isPresent());
        assertEquals("Nearby Override", result.get().name());
    }

    @Test
    void testInsertOverride_DropsNearbyOverrides() {
        // Create a test user
        User user = testingService.randomUser();

        // Insert first override
        SignificantPlace place1 = new SignificantPlace(1L, "First Override", "123 First St", "First City", "US", 40.7128, -74.0060, PlaceType.HOME, ZoneId.of("America/New_York"), false, 1L);
        significantPlaceOverrideJdbcService.insertOverride(user, place1);

        // Verify first override exists
        GeoPoint point1 = new GeoPoint(place1.getLatitudeCentroid(), place1.getLongitudeCentroid());
        Optional<PlaceInformationOverride> result1 = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point1);
        assertTrue(result1.isPresent());

        // Insert second override very close (within 5m)
        SignificantPlace place2 = new SignificantPlace(2L, "Second Override", "456 Second St", "Second City", "US", 40.7128442, -74.0060, PlaceType.WORK, ZoneId.of("America/New_York"), false, 1L);
        significantPlaceOverrideJdbcService.insertOverride(user, place2);

        // Verify second override exists
        GeoPoint point2 = new GeoPoint(place2.getLatitudeCentroid(), place2.getLongitudeCentroid());
        Optional<PlaceInformationOverride> result2 = significantPlaceOverrideJdbcService.findByUserAndPoint(user, point2);
        assertTrue(result2.isPresent());
        assertEquals("Second Override", result2.get().name());
    }
}
