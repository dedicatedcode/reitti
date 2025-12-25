package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.service.PlaceChangeDetectionService.PlaceChangeAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class PlaceChangeDetectionServiceTest {

    @Autowired
    private PlaceChangeDetectionService placeChangeDetectionService;

    @Autowired
    private SignificantPlaceJdbcService placeJdbcService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void analyzeChanges_WithValidPolygonAddition_ShouldReturnWarning() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, null);
        String polygonData = """
            [
                {"lat": 53.863100, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.701000},
                {"lat": 53.863100, "lng": 10.701000}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), polygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("The polygon boundary will be added to this place, this may affect visit detection.")));
    }

    @Test
    void analyzeChanges_WithPolygonRemoval_ShouldReturnWarning() {
        // Given
        List<GeoPoint> existingPolygon = List.of(
            new GeoPoint(53.863100, 10.700900),
            new GeoPoint(53.863200, 10.700900),
            new GeoPoint(53.863200, 10.701000),
            new GeoPoint(53.863100, 10.701000)
        );
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, existingPolygon);

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), null);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("The polygon boundary will be removed from this place, this may affect visit detection.")));
    }

    @Test
    void analyzeChanges_WithSignificantPolygonChange_ShouldReturnWarning() {
        // Given
        List<GeoPoint> existingPolygon = List.of(
            new GeoPoint(53.863100, 10.700900),
            new GeoPoint(53.863200, 10.700900),
            new GeoPoint(53.863200, 10.701000),
            new GeoPoint(53.863100, 10.701000)
        );
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, existingPolygon);
        
        // Polygon moved significantly (more than 10m)
        String newPolygonData = """
            [
                {"lat": 53.864100, "lng": 10.701900},
                {"lat": 53.864200, "lng": 10.701900},
                {"lat": 53.864200, "lng": 10.702000},
                {"lat": 53.864100, "lng": 10.702000}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), newPolygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("The polygon boundary will be significantly changed, which may affect visit detection.")));
    }

    @Test
    void analyzeChanges_WithNoPolygonChange_ShouldNotReturnWarning() {
        // Given
        List<GeoPoint> existingPolygon = List.of(
            new GeoPoint(53.863100, 10.700900),
            new GeoPoint(53.863200, 10.700900),
            new GeoPoint(53.863200, 10.701000),
            new GeoPoint(53.863100, 10.701000)
        );
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, existingPolygon);
        
        // Very minor change (less than 10m)
        String newPolygonData = """
            [
                {"lat": 53.863100, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.701000},
                {"lat": 53.863100, "lng": 10.701000}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), newPolygonData);

        // Then
        assertTrue(result.isCanProceed());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void analyzeChanges_WithOverlappingPlaces_ShouldReturnWarning() {
        // Given
        List<GeoPoint> existingPolygon = List.of(
            new GeoPoint(53.863100, 10.700900),
            new GeoPoint(53.863200, 10.700900),
            new GeoPoint(53.863200, 10.701000),
            new GeoPoint(53.863100, 10.701000)
        );
        createTestPlace("Existing Place", 53.863149, 10.700927, existingPolygon);
        SignificantPlace newPlace = createTestPlace("New Place", 53.863149, 10.700927, null);
        
        // Overlapping polygon
        String overlappingPolygonData = """
            [
                {"lat": 53.863150, "lng": 10.700950},
                {"lat": 53.863250, "lng": 10.700950},
                {"lat": 53.863250, "lng": 10.701050},
                {"lat": 53.863150, "lng": 10.701050}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, newPlace.getId(), overlappingPolygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("The new boundary will overlap with 1 existing place, which may cause visits to be reassigned between places and affect trip calculations")));
    }

    @Test
    void analyzeChanges_WithInvalidPolygonData_ShouldReturnError() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, null);
        String invalidPolygonData = "invalid json";

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), invalidPolygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("An error occurred while checking the update")));
    }

    @Test
    void analyzeChanges_WithInsufficientPolygonPoints_ShouldReturnError() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, null);
        String insufficientPolygonData = """
            [
                {"lat": 53.863100, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.700900}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), insufficientPolygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("An error occurred while checking the update")));
    }

    @Test
    void analyzeChanges_WithMissingLatLngProperties_ShouldReturnError() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, null);
        String invalidPolygonData = """
            [
                {"latitude": 53.863100, "longitude": 10.700900},
                {"lat": 53.863200, "lng": 10.700900},
                {"lat": 53.863200, "lng": 10.701000}
            ]
            """;

        // When
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), invalidPolygonData);

        // Then
        assertFalse(result.isCanProceed());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("An error occurred while checking the update")));
    }

    @Test
    void analyzeChanges_WithNoChanges_ShouldReturnNoWarnings() {
        // Given
        SignificantPlace place = createTestPlace("Test Place", 53.863149, 10.700927, null);

        // When - no polygon data provided for place that has no polygon
        PlaceChangeAnalysis result = placeChangeDetectionService.analyzeChanges(testUser, place.getId(), null);

        // Then
        assertTrue(result.isCanProceed());
        assertTrue(result.getWarnings().isEmpty());
    }

    private SignificantPlace createTestPlace(String name, double latitude, double longitude, List<GeoPoint> polygon) {
        return placeJdbcService.create(testUser, new SignificantPlace(
                null,
                name,
                null,
                null,
                null,
                latitude,
                longitude,
                polygon,
                SignificantPlace.PlaceType.HOME,
                ZoneId.systemDefault(),
                true,
                0L
        ));
    }
}
