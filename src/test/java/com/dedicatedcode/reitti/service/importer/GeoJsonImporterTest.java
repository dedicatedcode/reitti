package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportBatchProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeoJsonImporterTest {

    @Mock
    private ImportStateHolder stateHolder;

    @Mock
    private ImportBatchProcessor batchProcessor;

    @Mock
    private User user;

    private GeoJsonImporter geoJsonImporter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geoJsonImporter = new GeoJsonImporter(objectMapper, stateHolder, batchProcessor);
    }

    @Test
    void shouldImportFeatureCollectionWithIsoTimestamp() {
        when(batchProcessor.getBatchSize()).thenReturn(100);

        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.4050, 52.5200]
                  },
                  "properties": {
                    "timestamp": "2023-10-15T10:30:00Z",
                    "accuracy": 10.0
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.4060, 52.5210]
                  },
                  "properties": {
                    "timestamp": "2023-10-15T10:31:00Z",
                    "accuracy": 15.0
                  }
                }
              ]
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertTrue((Boolean) result.get("success"));
        assertEquals(2, result.get("pointsReceived"));

        ArgumentCaptor<List<LocationPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchProcessor).sendToQueue(eq(user), captor.capture());

        List<LocationPoint> points = captor.getValue();
        assertEquals(2, points.size());

        LocationPoint point1 = points.get(0);
        assertEquals(52.5200, point1.getLatitude());
        assertEquals(13.4050, point1.getLongitude());
        assertEquals("2023-10-15T10:30:00Z", point1.getTimestamp());
        assertEquals(10.0, point1.getAccuracyMeters());

        LocationPoint point2 = points.get(1);
        assertEquals(52.5210, point2.getLatitude());
        assertEquals(13.4060, point2.getLongitude());
        assertEquals("2023-10-15T10:31:00Z", point2.getTimestamp());
        assertEquals(15.0, point2.getAccuracyMeters());

        verify(stateHolder).importStarted();
        verify(stateHolder).importFinished();
    }

    @Test
    void shouldImportFeatureCollectionWithUnixEpochTimestamp() {
        when(batchProcessor.getBatchSize()).thenReturn(100);
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.4050, 52.5200]
                  },
                  "properties": {
                    "timestamp": 1697365800,
                    "accuracy": 10.0
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.4060, 52.5210]
                  },
                  "properties": {
                    "time": 1697365860,
                    "accuracy": 15.0
                  }
                }
              ]
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertTrue((Boolean) result.get("success"));
        assertEquals(2, result.get("pointsReceived"));

        ArgumentCaptor<List<LocationPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchProcessor).sendToQueue(eq(user), captor.capture());

        List<LocationPoint> points = captor.getValue();
        assertEquals(2, points.size());

        LocationPoint point1 = points.get(0);
        assertEquals(52.5200, point1.getLatitude());
        assertEquals(13.4050, point1.getLongitude());
        assertEquals("2023-10-15T10:30:00Z", point1.getTimestamp());
        assertEquals(10.0, point1.getAccuracyMeters());

        LocationPoint point2 = points.get(1);
        assertEquals(52.5210, point2.getLatitude());
        assertEquals(13.4060, point2.getLongitude());
        assertEquals("2023-10-15T10:31:00Z", point2.getTimestamp());
        assertEquals(15.0, point2.getAccuracyMeters());
    }

    @Test
    void shouldImportSingleFeature() {
        when(batchProcessor.getBatchSize()).thenReturn(100);
        String geoJson = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [13.4050, 52.5200]
              },
              "properties": {
                "timestamp": "2023-10-15T10:30:00Z",
                "accuracy": 10.0
              }
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("pointsReceived"));

        ArgumentCaptor<List<LocationPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchProcessor).sendToQueue(eq(user), captor.capture());

        List<LocationPoint> points = captor.getValue();
        assertEquals(1, points.size());

        LocationPoint point = points.get(0);
        assertEquals(52.5200, point.getLatitude());
        assertEquals(13.4050, point.getLongitude());
        assertEquals("2023-10-15T10:30:00Z", point.getTimestamp());
        assertEquals(10.0, point.getAccuracyMeters());
    }

    @Test
    void shouldImportSinglePoint() {
        String geoJson = """
            {
              "type": "Point",
              "coordinates": [13.4050, 52.5200]
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertFalse((Boolean) result.get("success"));
        assertEquals(0, result.get("pointsReceived"));
        verify(batchProcessor, never()).sendToQueue(any(), any());
    }

    @Test
    void shouldHandleInvalidGeoJson() {
        String invalidJson = """
            {
              "invalid": "json"
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(invalidJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Invalid GeoJSON"));
        verify(batchProcessor, never()).sendToQueue(any(), any());
        verify(stateHolder).importStarted();
        verify(stateHolder).importFinished();
    }

    @Test
    void shouldHandleUnsupportedGeoJsonType() {
        when(batchProcessor.getBatchSize()).thenReturn(100);
        String geoJson = """
            {
              "type": "LineString",
              "coordinates": [[13.4050, 52.5200], [13.4060, 52.5210]]
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Unsupported GeoJSON type"));
        verify(batchProcessor, never()).sendToQueue(any(), any());
    }

    @Test
    void shouldSkipFeaturesWithoutTimestamp() {
        when(batchProcessor.getBatchSize()).thenReturn(100);

        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.4050, 52.5200]
                  },
                  "properties": {
                    "accuracy": 10.0
                  }
                }
              ]
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertFalse((Boolean) result.get("success"));
        assertEquals(0, result.get("pointsReceived"));
        verify(batchProcessor, never()).sendToQueue(any(), any());
    }

    @Test
    void shouldUseDefaultAccuracyWhenNotProvided() {
        when(batchProcessor.getBatchSize()).thenReturn(100);
        String geoJson = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [13.4050, 52.5200]
              },
              "properties": {
                "timestamp": "2023-10-15T10:30:00Z"
              }
            }
            """;

        InputStream inputStream = new ByteArrayInputStream(geoJson.getBytes());
        Map<String, Object> result = geoJsonImporter.importGeoJson(inputStream, user);

        assertTrue((Boolean) result.get("success"));
        assertEquals(1, result.get("pointsReceived"));

        ArgumentCaptor<List<LocationPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchProcessor).sendToQueue(eq(user), captor.capture());

        List<LocationPoint> points = captor.getValue();
        LocationPoint point = points.get(0);
        assertEquals(50.0, point.getAccuracyMeters());
    }
}
