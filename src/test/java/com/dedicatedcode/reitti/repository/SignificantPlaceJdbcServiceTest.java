package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class SignificantPlaceJdbcServiceTest {

    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser = createTestUser("testuser_" + UUID.randomUUID(), "Test User");
        otherUser = createTestUser("otheruser_" + UUID.randomUUID(), "Other User");
    }

    @Test
    void findByUser_shouldReturnPlacesForSpecificUser() {
        // Given
        SignificantPlace place1 = createTestPlace("Home", 53.863149, 10.700927);
        SignificantPlace place2 = createTestPlace("Work", 53.868977, 10.680643);
        SignificantPlace otherUserPlace = createTestPlaceForUser(otherUser, "Other Home", 53.870000, 10.690000);

        significantPlaceJdbcService.create(testUser, place1);
        significantPlaceJdbcService.create(testUser, place2);
        significantPlaceJdbcService.create(otherUser, otherUserPlace);

        // When
        Page<SignificantPlace> result = significantPlaceJdbcService.findByUser(testUser, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(SignificantPlace::getName)
                .containsExactly("Home", "Work");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByUser_shouldRespectPagination() {
        // Given
        for (int i = 0; i < 5; i++) {
            SignificantPlace place = createTestPlace("Place " + i, 53.863149 + i * 0.001, 10.700927);
            significantPlaceJdbcService.create(testUser, place);
        }

        // When
        Page<SignificantPlace> firstPage = significantPlaceJdbcService.findByUser(testUser, PageRequest.of(0, 2));
        Page<SignificantPlace> secondPage = significantPlaceJdbcService.findByUser(testUser, PageRequest.of(1, 2));

        // Then
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(secondPage.getTotalElements()).isEqualTo(5);
    }

    @Test
    void findNearbyPlaces_shouldReturnPlacesWithinDistance() {
        // Given
        Point centerPoint = geometryFactory.createPoint(new Coordinate(10.700927, 53.863149));
        SignificantPlace nearPlace = createTestPlace("Near Place", 53.863200, 10.701000); // ~50m away
        SignificantPlace farPlace = createTestPlace("Far Place", 53.870000, 10.720000); // ~1km away

        significantPlaceJdbcService.create(testUser, nearPlace);
        significantPlaceJdbcService.create(testUser, farPlace);

        // When
        List<SignificantPlace> nearbyPlaces = significantPlaceJdbcService.findNearbyPlaces(
                testUser.getId(), centerPoint, 0.003);

        // Then
        assertThat(nearbyPlaces).hasSize(1);
        assertThat(nearbyPlaces.get(0).getName()).isEqualTo("Near Place");
    }

    @Test
    void create_shouldPersistNewPlace() {
        // Given
        SignificantPlace newPlace = createTestPlace("New Place", 53.863149, 10.700927);

        // When
        SignificantPlace created = significantPlaceJdbcService.create(testUser, newPlace);

        // Then
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("New Place");
        assertThat(created.getLatitudeCentroid()).isEqualTo(53.863149);
        assertThat(created.getLongitudeCentroid()).isEqualTo(10.700927);
    }

    @Test
    void update_shouldModifyExistingPlace() {
        // Given
        SignificantPlace originalPlace = createTestPlace("Original", 53.863149, 10.700927);
        SignificantPlace created = significantPlaceJdbcService.create(testUser, originalPlace);

        SignificantPlace updatedPlace = new SignificantPlace(
                created.getId(),
                "Updated Name",
                "Updated Address",
                "Berlin",
                "DE",
                53.863149,
                10.700927,
                null,
                SignificantPlace.PlaceType.RESTAURANT,
                ZoneId.systemDefault(),
                true,
                created.getVersion()
        );

        // When
        SignificantPlace result = significantPlaceJdbcService.update(updatedPlace);

        // Then
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getAddress()).isEqualTo("Updated Address");
        assertThat(result.getCountryCode()).isEqualTo("DE");
        assertThat(result.getType()).isEqualTo(SignificantPlace.PlaceType.RESTAURANT);
        assertThat(result.getTimezone()).isEqualTo(ZoneId.systemDefault());
        assertThat(result.isGeocoded()).isTrue();
    }

    @Test
    void findById_shouldReturnPlaceWhenExists() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927);
        SignificantPlace created = significantPlaceJdbcService.create(testUser, place);

        // When
        Optional<SignificantPlace> result = significantPlaceJdbcService.findById(created.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Place");
    }

    @Test
    void findById_shouldReturnEmptyWhenNotExists() {
        // When
        Optional<SignificantPlace> result = significantPlaceJdbcService.findById(999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void exists_shouldReturnTrueWhenPlaceExistsForUser() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927);
        SignificantPlace created = significantPlaceJdbcService.create(testUser, place);

        // When
        boolean exists = significantPlaceJdbcService.exists(testUser, created.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void exists_shouldReturnFalseWhenPlaceNotExistsForUser() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927);
        SignificantPlace created = significantPlaceJdbcService.create(otherUser, place);

        // When
        boolean exists = significantPlaceJdbcService.exists(testUser, created.getId());

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void findNonGeocodedByUser_shouldReturnOnlyNonGeocodedPlaces() {
        // Given
        SignificantPlace geocodedPlace = createTestPlace("Geocoded", 53.863149, 10.700927);
        SignificantPlace nonGeocodedPlace = createTestPlace("Non-Geocoded", 53.868977, 10.680643);

        SignificantPlace created1 = significantPlaceJdbcService.create(testUser, geocodedPlace);
        SignificantPlace created2 = significantPlaceJdbcService.create(testUser, nonGeocodedPlace);

        // Update one to be geocoded
        SignificantPlace updated = new SignificantPlace(
                created1.getId(),
                created1.getName(),
                "Some Address",
                "Berlin",
                "DE",
                created1.getLatitudeCentroid(),
                created1.getLongitudeCentroid(),
                null,
                SignificantPlace.PlaceType.HOME,
                ZoneId.systemDefault(),
                true, // geocoded = true
                created1.getVersion()
        );
        significantPlaceJdbcService.update(updated);

        // When
        List<SignificantPlace> nonGeocoded = significantPlaceJdbcService.findNonGeocodedByUser(testUser);

        // Then
        assertThat(nonGeocoded).hasSize(1);
        assertThat(nonGeocoded.get(0).getName()).isEqualTo("Non-Geocoded");
        assertThat(nonGeocoded.get(0).isGeocoded()).isFalse();
    }

    @Test
    void findAllByUser_shouldReturnAllPlacesForUser() {
        // Given
        SignificantPlace place1 = createTestPlace("Place 1", 53.863149, 10.700927);
        SignificantPlace place2 = createTestPlace("Place 2", 53.868977, 10.680643);
        SignificantPlace otherUserPlace = createTestPlaceForUser(otherUser, "Other Place", 53.870000, 10.690000);

        significantPlaceJdbcService.create(testUser, place1);
        significantPlaceJdbcService.create(testUser, place2);
        significantPlaceJdbcService.create(otherUser, otherUserPlace);

        // When
        List<SignificantPlace> allPlaces = significantPlaceJdbcService.findAllByUser(testUser);

        // Then
        assertThat(allPlaces).hasSize(2);
        assertThat(allPlaces)
                .extracting(SignificantPlace::getName)
                .containsExactly("Place 1", "Place 2");
    }

    @Test
    void create_shouldPersistNewPlaceWithPolygon() {
        // Given
        List<GeoPoint> polygon = List.of(
                GeoPoint.from(53.863149, 10.700927),
                GeoPoint.from(53.863200, 10.701000),
                GeoPoint.from(53.863100, 10.701100),
                GeoPoint.from(53.863050, 10.700950),
                GeoPoint.from(53.863149, 10.700927) // Close the polygon
        );

        SignificantPlace newPlace = new SignificantPlace(
                null,
                "Place with Polygon",
                null,
                null,
                null,
                53.863149,
                10.700927,
                polygon,
                SignificantPlace.PlaceType.PARK,
                ZoneId.systemDefault(),
                false,
                0L
        );

        // When
        SignificantPlace created = significantPlaceJdbcService.create(testUser, newPlace);

        // Then
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Place with Polygon");
        assertThat(created.getPolygon()).isNotNull();
        assertThat(created.getPolygon()).hasSize(5);
        assertThat(created.getPolygon().get(0).latitude()).isEqualTo(53.863149);
        assertThat(created.getPolygon().get(0).longitude()).isEqualTo(10.700927);
    }

    @Test
    void create_shouldPersistNewPlaceWithoutPolygon() {
        // Given
        SignificantPlace newPlace = new SignificantPlace(
                null,
                "Place without Polygon",
                null,
                null,
                null,
                53.863149,
                10.700927,
                null, // No polygon
                SignificantPlace.PlaceType.OTHER,
                ZoneId.systemDefault(),
                false,
                0L
        );

        // When
        SignificantPlace created = significantPlaceJdbcService.create(testUser, newPlace);

        // Then
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Place without Polygon");
        assertThat(created.getPolygon()).isNull();
    }

    @Test
    void update_shouldAddPolygonToExistingPlace() {
        // Given - Create place without polygon
        SignificantPlace originalPlace = createTestPlace("Original Place", 53.863149, 10.700927);
        SignificantPlace created = significantPlaceJdbcService.create(testUser, originalPlace);

        // Create polygon to add
        List<GeoPoint> polygon = List.of(
                GeoPoint.from(53.863149, 10.700927),
                GeoPoint.from(53.863200, 10.701000),
                GeoPoint.from(53.863100, 10.701100),
                GeoPoint.from(53.863149, 10.700927) // Close the polygon
        );

        SignificantPlace updatedPlace = new SignificantPlace(
                created.getId(),
                created.getName(),
                "Updated Address",
                "Berlin",
                "DE",
                created.getLatitudeCentroid(),
                created.getLongitudeCentroid(),
                polygon, // Add polygon
                SignificantPlace.PlaceType.PARK,
                ZoneId.systemDefault(),
                true,
                created.getVersion()
        );

        // When
        SignificantPlace result = significantPlaceJdbcService.update(updatedPlace);

        // Then
        assertThat(result.getPolygon()).isNotNull();
        assertThat(result.getPolygon()).hasSize(4);
        assertThat(result.getPolygon().get(0).latitude()).isEqualTo(53.863149);
        assertThat(result.getPolygon().get(0).longitude()).isEqualTo(10.700927);
        assertThat(result.getAddress()).isEqualTo("Updated Address");
        assertThat(result.getType()).isEqualTo(SignificantPlace.PlaceType.PARK);
    }

    @Test
    void update_shouldRemovePolygonFromExistingPlace() {
        // Given - Create place with polygon
        List<GeoPoint> originalPolygon = List.of(
                GeoPoint.from(53.863149, 10.700927),
                GeoPoint.from(53.863200, 10.701000),
                GeoPoint.from(53.863100, 10.701100),
                GeoPoint.from(53.863149, 10.700927)
        );

        SignificantPlace originalPlace = new SignificantPlace(
                null,
                "Place with Polygon",
                null,
                null,
                null,
                53.863149,
                10.700927,
                originalPolygon,
                SignificantPlace.PlaceType.PARK,
                ZoneId.systemDefault(),
                false,
                0L
        );

        SignificantPlace created = significantPlaceJdbcService.create(testUser, originalPlace);

        // Update to remove polygon
        SignificantPlace updatedPlace = new SignificantPlace(
                created.getId(),
                created.getName(),
                "Updated Address",
                "Berlin",
                "DE",
                created.getLatitudeCentroid(),
                created.getLongitudeCentroid(),
                null, // Remove polygon
                SignificantPlace.PlaceType.RESTAURANT,
                ZoneId.systemDefault(),
                true,
                created.getVersion()
        );

        // When
        SignificantPlace result = significantPlaceJdbcService.update(updatedPlace);

        // Then
        assertThat(result.getPolygon()).isNull();
        assertThat(result.getAddress()).isEqualTo("Updated Address");
        assertThat(result.getType()).isEqualTo(SignificantPlace.PlaceType.RESTAURANT);
    }

    @Test
    void update_shouldModifyExistingPolygon() {
        // Given - Create place with polygon
        List<GeoPoint> originalPolygon = List.of(
                GeoPoint.from(53.863149, 10.700927),
                GeoPoint.from(53.863200, 10.701000),
                GeoPoint.from(53.863100, 10.701100),
                GeoPoint.from(53.863149, 10.700927)
        );

        SignificantPlace originalPlace = new SignificantPlace(
                null,
                "Place with Polygon",
                null,
                null,
                null,
                53.863149,
                10.700927,
                originalPolygon,
                SignificantPlace.PlaceType.PARK,
                ZoneId.systemDefault(),
                false,
                0L
        );

        SignificantPlace created = significantPlaceJdbcService.create(testUser, originalPlace);

        // Create modified polygon
        List<GeoPoint> modifiedPolygon = List.of(
                GeoPoint.from(53.863149, 10.700927),
                GeoPoint.from(53.863250, 10.701050), // Different coordinates
                GeoPoint.from(53.863150, 10.701150), // Different coordinates
                GeoPoint.from(53.863080, 10.700980), // Different coordinates
                GeoPoint.from(53.863149, 10.700927)
        );

        SignificantPlace updatedPlace = new SignificantPlace(
                created.getId(),
                created.getName(),
                created.getAddress(),
                created.getCity(),
                created.getCountryCode(),
                created.getLatitudeCentroid(),
                created.getLongitudeCentroid(),
                modifiedPolygon, // Modified polygon
                created.getType(),
                created.getTimezone(),
                created.isGeocoded(),
                created.getVersion()
        );

        // When
        SignificantPlace result = significantPlaceJdbcService.update(updatedPlace);

        // Then
        assertThat(result.getPolygon()).isNotNull();
        assertThat(result.getPolygon()).hasSize(5);
        assertThat(result.getPolygon().get(1).latitude()).isEqualTo(53.863250);
        assertThat(result.getPolygon().get(1).longitude()).isEqualTo(10.701050);
        assertThat(result.getPolygon().get(2).latitude()).isEqualTo(53.863150);
        assertThat(result.getPolygon().get(2).longitude()).isEqualTo(10.701150);
    }

    @Test
    void findNearbyPlaces_shouldFindPlacesWithPolygons() {
        // Given
        Point searchPoint = geometryFactory.createPoint(new Coordinate(10.700950, 53.863120));

        // Create a place with polygon that contains the search point
        List<GeoPoint> polygon = List.of(
                GeoPoint.from(53.863100, 10.700900),
                GeoPoint.from(53.863200, 10.701000),
                GeoPoint.from(53.863100, 10.701100),
                GeoPoint.from(53.863000, 10.701000),
                GeoPoint.from(53.863100, 10.700900) // Close the polygon
        );

        SignificantPlace placeWithPolygon = new SignificantPlace(
                null,
                "Place with Polygon",
                null,
                null,
                null,
                53.863100,
                10.701000,
                polygon,
                SignificantPlace.PlaceType.PARK,
                ZoneId.systemDefault(),
                false,
                0L
        );

        // Create a place without polygon that's far away
        SignificantPlace farPlace = createTestPlace("Far Place", 53.870000, 10.720000);

        significantPlaceJdbcService.create(testUser, placeWithPolygon);
        significantPlaceJdbcService.create(testUser, farPlace);

        // When
        List<SignificantPlace> nearbyPlaces = significantPlaceJdbcService.findNearbyPlaces(
                testUser.getId(), searchPoint, 0.001); // Small buffer for places without polygons

        // Then
        assertThat(nearbyPlaces).hasSize(1);
        assertThat(nearbyPlaces.get(0).getName()).isEqualTo("Place with Polygon");
        assertThat(nearbyPlaces.get(0).getPolygon()).isNotNull();
        assertThat(nearbyPlaces.get(0).getPolygon()).hasSize(5);
    }

    // Helper method to create test place with polygon
    private SignificantPlace createTestPlaceWithPolygon(String name, double latitude, double longitude, List<GeoPoint> polygon) {
        return new SignificantPlace(
                null,
                name,
                null,
                null,
                null,
                latitude,
                longitude,
                polygon,
                SignificantPlace.PlaceType.OTHER,
                ZoneId.systemDefault(),
                false,
                0L
        );
    }

    private User createTestUser(String username, String displayName) {
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, password, display_name, role) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                username, "password", displayName, "USER"
        );
        return new User(userId, username, "password", displayName, null, null, Role.USER, 0L);
    }

    private SignificantPlace createTestPlace(String name, double latitude, double longitude) {
        return new SignificantPlace(
                null,
                name,
                null,
                null,
                null,
                latitude,
                longitude,
                null,
                SignificantPlace.PlaceType.OTHER,
                ZoneId.systemDefault()
                , false,
                0L
        );
    }

    private SignificantPlace createTestPlaceForUser(User user, String name, double latitude, double longitude) {
        return new SignificantPlace(
                null,
                name,
                null,
                null,
                null,
                latitude,
                longitude,
                null,
                SignificantPlace.PlaceType.OTHER,
                ZoneId.systemDefault(),
                false,
                0L
        );
    }
}
