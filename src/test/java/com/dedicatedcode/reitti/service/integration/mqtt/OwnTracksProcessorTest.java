package com.dedicatedcode.reitti.service.integration.mqtt;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnTracksProcessorTest {

    @Mock
    private LocationBatchingService locationBatchingService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OwnTracksProcessor ownTracksProcessor;

    @Captor
    private ArgumentCaptor<LocationPoint> locationPointCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "testuser", "password", "Test User",
                           null, null, null, null, 1L);
    }

    @Test
    void shouldProcessValidLocationUpdate() throws Exception {
        // Given
        String validJson = "{\"_type\":\"location\",\"t\":\"2023-01-01T12:00:00Z\",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("location");
        request.setT("2023-01-01T12:00:00Z");
        request.setLatitude(53.863149);
        request.setLongitude(10.700927);
        request.setAccuracy(10.0);

        LocationPoint expectedLocationPoint = new LocationPoint();
        expectedLocationPoint.setTimestamp("2023-01-01T12:00:00Z");
        expectedLocationPoint.setLatitude(53.863149);
        expectedLocationPoint.setLongitude(10.700927);
        expectedLocationPoint.setAccuracyMeters(10.0);

        when(objectMapper.readValue(validJson, OwntracksLocationRequest.class)).thenReturn(request);
        when(request.toLocationPoint()).thenReturn(expectedLocationPoint);

        // When
        ownTracksProcessor.process(testUser, validJson.getBytes());

        // Then
        verify(locationBatchingService).addLocationPoint(eq(testUser), locationPointCaptor.capture());
        LocationPoint capturedPoint = locationPointCaptor.getValue();
        assertEquals(expectedLocationPoint.getTimestamp(), capturedPoint.getTimestamp());
        assertEquals(expectedLocationPoint.getLatitude(), capturedPoint.getLatitude());
        assertEquals(expectedLocationPoint.getLongitude(), capturedPoint.getLongitude());
        assertEquals(expectedLocationPoint.getAccuracyMeters(), capturedPoint.getAccuracyMeters());
    }

    @Test
    void shouldIgnoreNonLocationMessage() throws Exception {
        // Given
        String nonLocationJson = "{\"_type\":\"transition\",\"t\":\"2023-01-01T12:00:00Z\"}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("transition");

        when(objectMapper.readValue(nonLocationJson, OwntracksLocationRequest.class)).thenReturn(request);

        // When
        ownTracksProcessor.process(testUser, nonLocationJson.getBytes());

        // Then
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }

    @Test
    void shouldIgnoreLocationWithNullTimestamp() throws Exception {
        // Given
        String invalidJson = "{\"_type\":\"location\",\"lat\":53.863149,\"lon\":10.700927,\"acc\":10.0}";
        OwntracksLocationRequest request = new OwntracksLocationRequest();
        request.setType("location");
        request.setLatitude(53.863149);
        request.setLongitude(10.700927);
        request.setAccuracy(10.0);

        LocationPoint invalidLocationPoint = new LocationPoint();
        invalidLocationPoint.setTimestamp(null);
        invalidLocationPoint.setLatitude(53.863149);
        invalidLocationPoint.setLongitude(10.700927);
        invalidLocationPoint.setAccuracyMeters(10.0);

        when(objectMapper.readValue(invalidJson, OwntracksLocationRequest.class)).thenReturn(request);
        when(request.toLocationPoint()).thenReturn(invalidLocationPoint);

        // When
        ownTracksProcessor.process(testUser, invalidJson.getBytes());

        // Then
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }

    @Test
    void shouldHandleProcessingErrorGracefully() throws Exception {
        // Given
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, OwntracksLocationRequest.class))
            .thenThrow(new RuntimeException("JSON parsing error"));

        // When/Then
        assertDoesNotThrow(() -> ownTracksProcessor.process(testUser, invalidJson.getBytes()));
        verify(locationBatchingService, never()).addLocationPoint(any(), any());
    }
}
