package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PlaceChangeDetectionService}.
 *
 * The tests focus on the warning generation logic, especially the
 * {@code calculateAffectedDays} path which uses {@link ProcessedVisitJdbcService#getAffectedDays}.
 */
@ExtendWith(MockitoExtension.class)
class PlaceChangeDetectionServiceTest {

    @Mock
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Mock
    private SignificantPlaceJdbcService placeJdbcService;

    @Mock
    private I18nService i18nService;

    @Mock
    private ObjectMapper objectMapper;

    private PlaceChangeDetectionService service;

    @BeforeEach
    void setUp() {
        service = new PlaceChangeDetectionService(
                processedVisitJdbcService,
                placeJdbcService,
                i18nService,
                objectMapper
        );
    }

    /**
     * Verifies that when the updated polygon causes overlapping places and the
     * {@code ProcessedVisitJdbcService#getAffectedDays} method returns at least one day,
     * a warning about recalculation is added and {@code canProceed} is false.
     */
    @Test
    void analyzeChanges_overlappingPlacesAndAffectedDays_addsRecalculationWarning() throws Exception {
        // ----- Arrange ---------------------------------------------------------

        // Mock user
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("test-user");

        // Mock current place (the one being edited)
        SignificantPlace currentPlace = mock(SignificantPlace.class);
        when(currentPlace.getId()).thenReturn(10L);
        when(currentPlace.getLatitudeCentroid()).thenReturn(53.0);
        when(currentPlace.getLongitudeCentroid()).thenReturn(10.0);
        // Existing polygon (three points forming a triangle)
        List<GeoPoint> existingPolygon = List.of(
                new GeoPoint(53.0, 10.0),
                new GeoPoint(53.0, 10.001),
                new GeoPoint(53.001, 10.0)
        );
        when(currentPlace.getPolygon()).thenReturn(existingPolygon);

        // Repository look‑ups
        when(placeJdbcService.findById(10L)).thenReturn(Optional.of(currentPlace));
        // Overlapping places – we return a list containing the current place itself
        when(placeJdbcService.findPlacesOverlappingWithPolygon(eq(1L), eq(10L), anyList()))
                .thenReturn(List.of(currentPlace));

        // ProcessedVisitJdbcService – simulate that one day would need recalculation
        when(processedVisitJdbcService.getAffectedDays(anyList()))
                .thenReturn(List.of(LocalDate.now()));

        // I18nService – simply echo the key and first argument (if any)
        when(i18nService.translate(eq("places.warning.overlapping.recalculation_hint"), any()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return "Recalc hint for " + args[1] + " days";
                });
        // For any other translation we just return the key (not relevant for this test)
        when(i18nService.translate(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        // ObjectMapper – parse the incoming polygon JSON
        String polygonJson = "[{\"lat\":53.0,\"lng\":10.0},{\"lat\":53.0,\"lng\":10.002},{\"lat\":53.002,\"lng\":10.0}]";
        when(objectMapper.readTree(polygonJson)).thenReturn(
                new com.fasterxml.jackson.databind.node.ArrayNode(
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance) {{
                    add(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                            .put("lat", 53.0).put("lng", 10.0));
                    add(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                            .put("lat", 53.0).put("lng", 10.002));
                    add(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                            .put("lat", 53.002).put("lng", 10.0));
                }}
        );

        // ----- Act -------------------------------------------------------------
        PlaceChangeDetectionService.PlaceChangeAnalysis analysis =
                service.analyzeChanges(user, 10L, polygonJson);

        // ----- Assert ----------------------------------------------------------
        assertNotNull(analysis);
        // Because a warning was added, canProceed must be false
        assertFalse(analysis.isCanProceed(), "Analysis should indicate that processing cannot proceed");

        List<String> warnings = analysis.getWarnings();
        assertEquals(1, warnings.size(), "Exactly one warning should be present");

        String warning = warnings.get(0);
        assertTrue(warning.contains("Recalc hint"),
                "Warning should contain the recalculation hint text");
        assertTrue(warning.contains("1"),
                "Warning should mention the number of affected days (1)");
    }
}
